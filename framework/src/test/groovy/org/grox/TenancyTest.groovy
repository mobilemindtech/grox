package org.grox

import grails.gorm.multitenancy.Tenants
import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.Persistence
import org.grox.data.tenant.TenantSupport
import org.grox.testdomain.Autor
import org.grox.testdomain.Ficha
import org.grox.testdomain.FichaAnamnese
import org.grox.testdomain.Livro
import org.grox.testdomain.Nota
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class TenancyTest {

    static Datastore datastore

    @BeforeAll
    static void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:tenancyTest;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop']])
        datastore = new Datastore(config, [Autor, Livro, Nota, Ficha, FichaAnamnese], TenancyTest.classLoader)
        Gorm.init(datastore)
        TenantSupport.configure(new SystemPropertyTenantResolver(), datastore.tenantType)
    }

    @AfterAll
    static void stop() {
        Gorm.reset()
        TenantSupport.reset()
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY)
    }

    @Test
    void 'discriminador: preenchido no save, filtra consultas, get, count, HQL e finders'() {
        assert datastore.multiTenancy && datastore.ormXml.contains('<tenant-id name="tenantId"><column nullable="false"/></tenant-id>')
        Tenants.withId('acme') {
            new Nota(texto: 'a1').save(failOnError: true)
            new Nota(texto: 'a2').save(failOnError: true)
        }
        Nota b1 = (Nota) Tenants.withId('globex') { new Nota(texto: 'b1').save(failOnError: true) }
        assert b1.tenantId == 'globex'

        Tenants.withId('acme') {
            assert Tenants.currentId() == 'acme'
            assert Nota.list()*.texto.sort() == ['a1', 'a2']
            assert Nota.count() == 2
            assert Nota.get(b1.id) == null
            assert Nota.findByTexto('b1') == null
            assert Nota.executeQuery('select n.texto from Nota n') .sort() == ['a1', 'a2']
            assert Nota.findAllByTextoLike('%1')*.texto == ['a1']
        }
        Tenants.withoutId {
            assert Nota.count() >= 3
            assert Nota.list()*.tenantId.toSet().containsAll(['acme', 'globex'])
        }
    }

    @Test
    void 'resolver, ausencia de tenant e tenant protegido no binding'() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY, 'initech')
        try {
            new Nota(texto: 'i1', tenantId: 'acme').save(failOnError: true)
            assert Nota.findByTexto('i1').tenantId == 'initech'
            assert Tenants.currentId() == 'initech'
        } finally {
            System.clearProperty(SystemPropertyTenantResolver.PROPERTY)
        }
        assertThrows(TenantNotFoundException) { Nota.list() }
        assertThrows(TenantNotFoundException) { new Nota(texto: 'x').save() }
        assertThrows(TenantNotFoundException) { Tenants.currentId() }
        assert Tenants.currentIdOrNull() == null
        // classes comuns não exigem tenant
        assert Autor.count() >= 0
        assertThrows(TenantNotFoundException) { Tenants.withoutId { new Nota(texto: 'sem').save() } }
        // o binding nunca aceita tenantId (protegido); em withoutId ele é atribuído explicitamente
        Tenants.withoutId {
            def n = new Nota(texto: 'explicito', tenantId: 'ignorado')
            assert n.tenantId == null
            n.tenantId = 'globex'
            n.save(failOnError: true)
        }
        assert Tenants.withId('globex') { Nota.findByTexto('explicito') } != null
    }

    @Test
    void 'hasOne com filho multi-tenant: filho ausente vira null, cascade de save e delete'() {
        // o @TenantId do Hibernate 7 filtra também a carga por chave; no one-to-one inverso o filho ausente dava EntityFilterException
        assert datastore.ormXml.contains('<join-formula>(select min(grox_c.id) from ficha_anamnese grox_c where grox_c.ficha_id = id and grox_c.tenant_id = tenant_id)</join-formula>')
        Long semFilho = (Long) Tenants.withId('acme') { new Ficha(nome: 'sem').save(failOnError: true, flush: true).id }
        Long comFilho = (Long) Tenants.withId('acme') {
            def f = new Ficha(nome: 'com')
            f.anamnese = new FichaAnamnese(observacao: 'ok', ficha: f)
            f.save(failOnError: true, flush: true).id
        }
        Tenants.withId('acme') {
            assert Ficha.get(semFilho).anamnese == null
            assert Ficha.get(comFilho).anamnese.observacao == 'ok'
            assert Ficha.list(sort: 'nome')*.anamnese*.observacao == ['ok', null]
            assert Ficha.executeQuery('from Ficha f order by f.nome').size() == 2
            assert Ficha.withTransaction { FichaAnamnese.findByObservacao('ok').ficha.nome } == 'com'
        }
        Tenants.withId('globex') {
            assert Ficha.get(comFilho) == null && FichaAnamnese.count() == 0
        }
        Tenants.withId('acme') {
            // filho incluído depois, numa ficha já gravada (gerenciada)
            Ficha.withTransaction {
                def f = Ficha.get(semFilho)
                f.anamnese = new FichaAnamnese(observacao: 'depois', ficha: f)
                f.save(failOnError: true, flush: true)
            }
        }
        Tenants.withId('acme') {
            assert Ficha.get(semFilho).anamnese.observacao == 'depois'
            // delete do pai apaga o filho antes (a chave estrangeira está no filho)
            Ficha.withTransaction { Ficha.get(comFilho).delete(flush: true) }
            assert FichaAnamnese.count() == 1 && Ficha.count() == 1
            // pai destacado com filho novo: merge
            Ficha solta = Ficha.withTransaction { new Ficha(nome: 'solta').save(failOnError: true, flush: true) }
            solta.anamnese = new FichaAnamnese(observacao: 'merge', ficha: solta)
            Ficha.withTransaction { solta.save(failOnError: true, flush: true) }
            assert Ficha.withTransaction { Ficha.findByNome('solta').anamnese.observacao } == 'merge'
        }
    }

    @Test
    void 'filtro de tenant nao pode ser desligado pela API do hibernate-filter'() {
        Tenants.withId('acme') {
            assertThrows(IllegalArgumentException) { Nota.disableHibernateFilter('_tenantId') }
            assertThrows(IllegalArgumentException) { Nota.withoutHibernateFilter('_tenantId') { Nota.count() } }
        }
    }

    @Test
    void 'withId troca de sessao quando o tenant muda'() {
        Tenants.withId('acme') {
            Persistence.withSession {
                long acme = Nota.count()
                long globex = Tenants.withId('globex') { Nota.count() }
                assert Nota.count() == acme
                assert globex != acme || acme == 0
            }
        }
    }

    @Test
    void 'withCurrent antes da primeira consulta da requisicao usa a sessao dela'() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY, 'acme')
        try {
            Ficha.withTransaction {
                def f = new Ficha(nome: 'f-lazy')
                f.anamnese = new FichaAnamnese(observacao: 'ok', ficha: f)
                f.save(failOnError: true)
            }
            // "requisição": sessão preguiçosa, e um service @CurrentTenant faz a primeira consulta
            Persistence.withSessionIfAvailable {
                def a = Tenants.withCurrent { FichaAnamnese.findByObservacao('ok') }
                assert a.attached                 // antes: destacada (sessão própria, já fechada)
                assert a.ficha.nome == 'f-lazy'   // antes: LazyInitializationException na view
            }
            // tenant diferente do da requisição continua numa sessão própria
            Persistence.withSessionIfAvailable {
                FichaAnamnese.count()
                assert Tenants.withId('globex') { FichaAnamnese.count() } == 0
                assert FichaAnamnese.findByObservacao('ok')   // a sessão da requisição segue no tenant dela
            }
        } finally {
            // o banco é compartilhado pelos testes da classe
            Ficha.withTransaction { Ficha.findByNome('f-lazy')?.delete(flush: true) }
            System.clearProperty(SystemPropertyTenantResolver.PROPERTY)
        }
    }
}
