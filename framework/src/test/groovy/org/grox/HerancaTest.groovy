package org.grox

import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.testdomain.heranca.Adocao
import org.grox.testdomain.heranca.Animal
import org.grox.testdomain.heranca.Cachorro
import org.grox.testdomain.heranca.Filhote
import org.grox.testdomain.heranca.Gato
import org.grox.testdomain.heranca.Moto
import org.grox.testdomain.heranca.Veiculo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Herança de domain classes: tabela única (padrão do GORM, coluna class) e uma tabela por subclasse. */
class HerancaTest {

    static Datastore datastore

    @BeforeAll
    static void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:heranca;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop']])
        datastore = new Datastore(config, [Animal, Cachorro, Filhote, Gato, Veiculo, Moto, Adocao,
                org.grox.testdomain.heranca.Documento, org.grox.testdomain.heranca.Contrato, org.grox.testdomain.heranca.Recibo], HerancaTest.classLoader)
        Gorm.init(datastore)
        org.grox.data.tenant.TenantSupport.configure(new org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver(), datastore.tenantType)
    }

    @AfterAll
    static void stop() {
        Gorm.reset()
        org.grox.data.tenant.TenantSupport.reset()
        System.clearProperty(org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver.PROPERTY)
    }

    private static List sql(String q) {
        Animal.withSession { s -> s.createNativeQuery(q, Object[]).resultList.collect { it instanceof Object[] ? (it as List) : [it] } }
    }

    @Test
    void 'tabela unica com a coluna class, consultas polimorficas e API estatica de cada subclasse'() {
        Animal.withTransaction {
            new Cachorro(nome: 'Rex', raca: 'vira-lata').save(failOnError: true)
            new Filhote(nome: 'Bidu', raca: 'poodle', meses: 3).save(failOnError: true)
            new Gato(nome: 'Mimi').save(failOnError: true)
        }
        // uma tabela só (animal), com as colunas de todas as subclasses e o discriminador do GORM
        assert datastore.domains[Cachorro].tableName == 'animal' && datastore.domains[Filhote].tableName == 'animal'
        assert sql('select class, nome, raca, meses, ronrona from animal order by nome') == [
                ['org.grox.testdomain.heranca.Filhote', 'Bidu', 'poodle', 3, null],
                ['GATO', 'Mimi', null, null, true],
                ['org.grox.testdomain.heranca.Cachorro', 'Rex', 'vira-lata', null, null]]
        Animal.withTransaction {
            // a raiz é polimórfica; cada subclasse vê a si e às suas subclasses
            assert Animal.list(sort: 'nome').collect { it.getClass().simpleName } == ['Filhote', 'Gato', 'Cachorro']
            assert Cachorro.list(sort: 'nome')*.nome == ['Bidu', 'Rex'] && Cachorro.count() == 2 && Gato.count() == 1 && Filhote.count() == 1
            assert Animal.findByNome('Mimi') instanceof Gato && Cachorro.findByNome('Mimi') == null
            assert Cachorro.findAllByRaca('poodle')*.nome == ['Bidu'] && Filhote.findByMeses(3).nome == 'Bidu'
            assert Cachorro.where { nome == 'Rex' }.get().raca == 'vira-lata'
            assert Animal.createCriteria().list { like('nome', 'M%') }*.nome == ['Mimi']
            // propriedades herdadas na API de cada classe
            assert datastore.domains[Filhote].hasPersistentProperty('nome') && datastore.domains[Filhote].hasPersistentProperty('raca')
            assert org.grox.data.DomainJson.entity(Filhote.findByNome('Bidu')).subMap(['nome', 'raca', 'meses']) == [nome: 'Bidu', raca: 'poodle', meses: 3]
        }
        // constraints herdadas: nome blank: false vale na subclasse
        def semNome = new Gato(nome: '')
        assert !semNome.validate() && semNome.errors.getFieldError('nome')
        // atualização e exclusão pela subclasse
        Animal.withTransaction { Gato.findByNome('Mimi').ronrona = false }
        assert Animal.withTransaction { Gato.findByNome('Mimi').ronrona } == false
        Animal.withTransaction { Animal.list()*.delete() }
        assert Animal.withTransaction { Animal.count() } == 0
    }

    @Test
    void 'uma tabela por subclasse com tablePerHierarchy false'() {
        Long id = Veiculo.withTransaction { new Moto(placa: 'ABC1234', cilindradas: 300).save(failOnError: true).id }
        Veiculo.withTransaction { new Veiculo(placa: 'XYZ9876').save(failOnError: true) }
        assert sql('select placa from veiculo order by placa') == [['ABC1234'], ['XYZ9876']]
        assert sql("select id, cilindradas from moto") == [[id, 300]]
        Veiculo.withTransaction {
            assert Veiculo.list(sort: 'placa').collect { it.getClass().simpleName } == ['Moto', 'Veiculo']
            assert Moto.get(id).cilindradas == 300 && Moto.count() == 1
        }
        Veiculo.withTransaction { Veiculo.list()*.delete() }
    }

    @Test
    void 'associacao para a raiz aceita qualquer subclasse'() {
        Long id = Animal.withTransaction {
            def gato = new Gato(nome: 'Tom').save(failOnError: true)
            new Adocao(adotante: 'Ana', animal: gato).save(failOnError: true).id
        }
        Animal.withTransaction {
            def a = Adocao.get(id)
            assert a.animal.nome == 'Tom'
            assert Adocao.where { animal.nome == 'Tom' }.count() == 1
        }
        Animal.withTransaction { Adocao.list()*.delete(); Animal.list()*.delete() }
    }

    @Test
    void 'hierarquia multi-tenant: o tenant da raiz filtra as subclasses'() {
        def Documento = org.grox.testdomain.heranca.Documento
        def Contrato = org.grox.testdomain.heranca.Contrato
        String prop = org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver.PROPERTY
        try {
            System.setProperty(prop, 'acme')
            Documento.withTransaction { new org.grox.testdomain.heranca.Contrato(titulo: 'C1', valor: 10.0G).save(failOnError: true) }
            assert Documento.withTransaction { [Contrato.count(), Documento.count(), Contrato.findByTitulo('C1')?.tenantId] } == [1L, 1L, 'acme']
            System.setProperty(prop, 'globex')
            assert Documento.withTransaction { [Contrato.count(), Documento.count(), Contrato.findByTitulo('C1')] } == [0L, 0L, null]
            assert sql("select class, tenant_id, titulo, valor from documento") == [['org.grox.testdomain.heranca.Contrato', 'acme', 'C1', 10.0G]]
        } finally {
            System.clearProperty(prop)
            grails.gorm.multitenancy.Tenants.withoutId { Documento.withTransaction { Documento.list()*.delete() } }
        }
    }

    @Test
    void 'base abstrata fora de grails-app nao e entidade, as propriedades ficam na tabela da domain'() {
        def Recibo = org.grox.testdomain.heranca.Recibo
        assert !org.grox.testdomain.componentes.Registro.isAnnotationPresent(org.grox.data.AbstractEntity)
        assert datastore.domains[Recibo].parent == null && !datastore.domains.containsKey(org.grox.testdomain.componentes.Registro)
        Recibo.withTransaction { new org.grox.testdomain.heranca.Recibo(numero: 'R1', criadoPor: 'ana').save(failOnError: true) }
        assert sql('select numero, criado_por from recibo') == [['R1', 'ana']]
        assert Recibo.withTransaction { Recibo.findByCriadoPor('ana').numero } == 'R1'
        Recibo.withTransaction { Recibo.list()*.delete() }
    }
}
