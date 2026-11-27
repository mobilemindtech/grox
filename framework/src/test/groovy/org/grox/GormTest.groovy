package org.grox

import grails.validation.ValidationException
import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.GormEntity
import org.grox.data.Persistence
import org.grox.i18n.MessageSource
import org.grox.testdomain.Autor
import org.grox.testdomain.Livro
import org.grox.testdomain.Situacao
import org.grox.web.json.JsonSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class GormTest {

    static Datastore datastore

    @BeforeAll
    static void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:gormTest;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop'],
                                 hibernate : [generate_statistics: true]])
        datastore = new Datastore(config, [Autor, Livro, org.grox.testdomain.Etiquetado, org.grox.testdomain.Pasta, org.grox.testdomain.Rosto, org.grox.testdomain.Nariz, org.grox.testdomain.Produto, org.grox.testdomain.Carro, org.grox.testdomain.Motor, org.grox.testdomain.Evento], GormTest.classLoader)
        Gorm.init(datastore)
    }

    @AfterAll
    static void stop() {
        Gorm.reset()
    }

    @BeforeEach
    void limpar() {
        Livro.executeUpdate('delete from Livro')
        Autor.executeUpdate('delete from Autor')
    }

    static class LeituraService {
        @grails.gorm.transactions.ReadOnly
        String exibir(Long id) {
            def a = Autor.get(id)
            a.nome = a.nome.toUpperCase()   // ajuste só para exibição
            a.nome
        }

        @grails.gorm.transactions.Transactional(readOnly = true)
        String exibirTx(Long id) { exibir(id) }

        @grails.gorm.transactions.Transactional
        void renomear(Long id, String nome) { Autor.get(id).nome = nome }
    }

    @Test
    void 'ReadOnly nao grava alteracoes feitas nas entidades'() {
        def target = new LeituraService()
        def proxy = (LeituraService) org.grox.service.ServiceProxies.create(LeituraService, target, false,
                org.grox.service.ServiceRegistry.interceptors(LeituraService, target), [])
        assert org.grox.data.GormServiceInterceptor.readOnlyMethods(LeituraService, ['exibir', 'exibirTx', 'renomear'] as Set) == ['exibir', 'exibirTx'] as Set
        Long id = new Autor(nome: 'Ana').save(failOnError: true, flush: true).id
        Closure<String> noBanco = { Persistence.withNewSession { Autor.get(id).nome } }
        assert Persistence.withSession { proxy.exibir(id) } == 'ANA'
        assert noBanco() == 'Ana'   // antes: o commit fazia flush e gravava ANA
        assert Persistence.withSession { proxy.exibirTx(id) } == 'ANA' && noBanco() == 'Ana'
        Persistence.withSession { proxy.renomear(id, 'Bia') }
        assert noBanco() == 'Bia'
        // participando de uma transação de escrita, vale o modo dela (como no Spring)
        Autor.withTransaction { proxy.exibir(id) }
        assert noBanco() == 'BIA'
    }

    @Test
    void 'binding de hasMany por ids resolve as entidades e ajusta o belongsTo'() {
        def ana = new Autor(nome: 'Ana').save(failOnError: true, flush: true)
        def ids = ['A', 'B', 'C'].collect { new Livro(titulo: it, autor: ana).save(failOnError: true, flush: true).id }
        def bia = new Autor(nome: 'Bia').save(failOnError: true, flush: true)
        // <g:select multiple name="livros">: lista de ids em texto (antes: Strings dentro do Set e o save quebrava)
        Autor.withTransaction {
            def b = Autor.get(bia.id)
            org.grox.data.binding.DataBinder.bind(b, [livros: [ids[0].toString(), ids[1].toString(), 'nao-e-id']])
            assert b.livros*.titulo.sort() == ['A', 'B'] && b.livros.every { it instanceof Livro && it.autor.is(b) }
            b.save(failOnError: true, flush: true)
        }
        assert Persistence.withNewSession { Livro.findAllByAutor(Autor.get(bia.id))*.titulo.sort() } == ['A', 'B']
        // JSON [{"id": n}] e um id só: a coleção existente é atualizada (removeFrom/addTo), não trocada
        Autor.withTransaction { status ->
            def b = Autor.get(bia.id)
            def colecao = b.livros
            org.grox.data.binding.DataBinder.bind(b, [livros: [[id: ids[1]], [id: ids[2]]]])
            assert b.livros.is(colecao) && b.livros*.titulo.sort() == ['B', 'C']
            org.grox.data.binding.DataBinder.bind(b, [livros: ids[2].toString()])
            assert b.livros*.titulo == ['C']
            status.setRollbackOnly()   // o A e o B ficariam sem autor (belongsTo obrigatório)
        }
    }

    @Test
    void 'violacao de restricao do banco vira DataIntegrityViolationException em todos os caminhos'() {
        def ana = new Autor(nome: 'Ana', email: 'ana@x.com').save(failOnError: true, flush: true)
        new Livro(titulo: 'Groovy', autor: ana).save(failOnError: true, flush: true)
        new Autor(nome: 'Bia', email: 'bia@x.com').save(failOnError: true, flush: true)
        // HQL: chave estrangeira
        assertThrows(org.springframework.dao.DataIntegrityViolationException) { Autor.executeUpdate('delete from Autor') }
        // save sem validação: o INSERT (id IDENTITY) sai no persist; antes vinha a ConstraintViolationException do Hibernate
        assertThrows(org.springframework.dao.DataIntegrityViolationException) {
            Autor.withTransaction { new Autor(nome: 'Dup', email: 'ana@x.com').save(validate: false) }
        }
        // alteração por dirty checking: o UPDATE sai no commit
        assertThrows(org.springframework.dao.DataIntegrityViolationException) {
            Autor.withTransaction { Autor.findByNome('Bia').email = 'ana@x.com' }
        }
        assert Persistence.withNewSession { Autor.findByNome('Bia').email } == 'bia@x.com'
    }

    @Test
    void 'transacao participante que falha desfaz a externa inteira, como no Spring'() {
        Closure<List<String>> nomes = { Persistence.withNewSession { Autor.list()*.nome.sort() } }
        // exceção capturada numa participante: antes, a externa fazia commit do que a interna gravou
        def e = assertThrows(org.springframework.transaction.UnexpectedRollbackException) {
            Autor.withTransaction {
                new Autor(nome: 'Externo').save(failOnError: true)
                try {
                    Autor.withTransaction {
                        new Autor(nome: 'Interno').save(failOnError: true, flush: true)
                        throw new IllegalStateException('falhou')
                    }
                } catch (IllegalStateException ignored) {
                }
            }
        }
        assert e.message.contains('rollback-only') && nomes() == []
        // setRollbackOnly() na participante: também desfaz tudo e avisa
        assertThrows(org.springframework.transaction.UnexpectedRollbackException) {
            Autor.withTransaction {
                new Autor(nome: 'Externo').save(failOnError: true)
                Autor.withTransaction { status -> status.setRollbackOnly() }
            }
        }
        assert nomes() == []
        // setRollbackOnly() na própria externa: rollback sem exceção
        Autor.withTransaction { status ->
            new Autor(nome: 'Externo').save(failOnError: true)
            status.setRollbackOnly()
        }
        assert nomes() == []
        // exceção que sai da externa: rollback e a exceção original
        assertThrows(IllegalArgumentException) {
            Autor.withTransaction {
                new Autor(nome: 'Externo').save(failOnError: true)
                Autor.withTransaction { throw new IllegalArgumentException('x') }
            }
        }
        assert nomes() == []
        // sem falha: commit das duas
        Autor.withTransaction {
            new Autor(nome: 'Externo').save(failOnError: true)
            Autor.withTransaction { new Autor(nome: 'Interno').save(failOnError: true) }
        }
        assert nomes() == ['Externo', 'Interno']
    }

    @Test
    void 'hasMany de valores simples com os nomes do GORM'() {
        def e = new org.grox.testdomain.Etiquetado(nome: 'x', tags: ['b', 'a'], numeros: [3, 1] as Set)
        e.addToTags('c')
        e.save(failOnError: true, flush: true)
        org.grox.testdomain.Etiquetado.withNewSession {
            def lido = org.grox.testdomain.Etiquetado.get(e.id)
            assert lido.tags == ['b', 'a', 'c'] && lido.numeros == [1, 3] as Set
            assert org.grox.data.DomainJson.entity(lido).tags == ['b', 'a', 'c']
        }
        // tabela <dono>_<prop>, <dono>_id, <prop>_<classe> e <prop>_idx, como o GORM grava
        def cols = org.grox.testdomain.Etiquetado.withNewSession { s ->
            s.createNativeQuery("select column_name from information_schema.columns where lower(table_name) = 'etiquetado_tags'").list()*.toString()*.toLowerCase()
        }
        assert cols.toSet() == ['etiquetado_id', 'tags_java_lang_string', 'tags_idx'] as Set
    }

    @Test
    void 'lista com associacao lazy nao faz uma consulta por linha'() {
        (1..5).each { new Livro(titulo: "Livro ${it}", autor: autor("Autor ${it}")).save(failOnError: true, flush: true) }
        def stats = datastore.sessionFactory.statistics
        Livro.withNewSession {
            stats.clear()
            List<String> nomes = Livro.list(sort: 'titulo')*.autor*.nome
            assert nomes == (1..5).collect { "Autor ${it}".toString() }
            // livros + autores em lote (default_batch_fetch_size), e não 1 + 5
            assert stats.prepareStatementCount == 2
        }
    }

    static Autor autor(String nome, Integer idade = null, String email = null) {
        (Autor) new Autor(nome: nome, idade: idade, email: email).save(failOnError: true)
    }

    @Test
    void 'AST adiciona trait, id, version, colecoes, referencias e construtor com binding'() {
        assert GormEntity.isAssignableFrom(Autor)
        assert Autor.getMethod('getId').returnType == Long
        assert Autor.getMethod('getVersion').returnType == Long
        assert Autor.getMethod('getLivros').returnType == Set
        assert Livro.getMethod('getAutor').returnType == Autor
        def a = new Autor(nome: '  Ana  ', idade: '30', desconhecida: 'x', id: 99)
        assert a.nome == 'Ana' && a.idade == 30 && a.id == null
    }

    @Test
    void 'orm xml gerado'() {
        String xml = datastore.ormXml
        assert xml.contains('<entity class="org.grox.testdomain.Autor" name="Autor" access="FIELD" metadata-complete="true">')
        assert xml.contains('<basic name="nome" optional="false"><column nullable="false" length="50"/></basic>')
        assert xml.contains('<basic name="situacao" optional="false"><column nullable="false"/><enumerated>STRING</enumerated></basic>')
        assert xml.contains('<one-to-many name="livros" target-entity="org.grox.testdomain.Livro" fetch="LAZY" mapped-by="autor" orphan-removal="true">')
        assert xml.contains('<many-to-one name="autor" target-entity="org.grox.testdomain.Autor" fetch="LAZY" optional="false">')
    }

    @Test
    void 'save, get, count, list com ordenacao e paginacao, timestamps e version'() {
        Autor carla = autor('Carla', 40)
        autor('Ana', 30)
        autor('Bruno', 20)
        assert carla.id && carla.dateCreated && carla.lastUpdated && carla.version == 0
        assert Autor.count() == 3
        assert Autor.list()*.nome == ['Ana', 'Bruno', 'Carla']
        assert Autor.list(sort: 'idade', order: 'desc', max: 2)*.nome == ['Carla', 'Ana']
        assert Autor.list(max: 1, offset: 1)*.nome == ['Bruno']
        assert Autor.list(sort: 'naoExiste')*.nome == ['Ana', 'Bruno', 'Carla']
        assert Autor.get(carla.id).nome == 'Carla'
        assert Autor.get(carla.id.toString()).nome == 'Carla'
        assert Autor.get('abc') == null && Autor.get(null) == null
        assert Autor.exists(carla.id) && !Autor.exists(-1L)
        assert Autor.first().nome == 'Carla' && Autor.last('nome').nome == 'Carla'
        assert Autor.getAll([carla.id])*.nome == ['Carla']

        Autor.withTransaction {
            def c = Autor.get(carla.id)
            c.idade = 41
            c.save()
        }
        assert Autor.get(carla.id).idade == 41
        assert Autor.get(carla.id).version == 1
    }

    @Test
    void 'validacao: constraints, padrao nullable false, unique, validator e mensagens'() {
        def invalido = new Autor(nome: 'A', email: 'x', idade: -1)
        assert !invalido.validate()
        assert invalido.errors.errorCount == 3
        assert invalido.errors.getFieldError('nome').codes == ['org.grox.testdomain.Autor.nome.size.toosmall', 'autor.nome.size.toosmall', 'default.invalid.size.message']
        assert invalido.errors.hasFieldErrors('email') && invalido.errors.hasFieldErrors('idade')
        assert invalido.save() == null && Autor.count() == 0
        assertThrows(ValidationException) { invalido.save(failOnError: true) }

        def messages = new MessageSource(GormTest.classLoader, 'messages', Locale.forLanguageTag('pt-BR'))
        assert messages.getMessage(invalido.errors.getFieldError('idade'), null) ==
                'O campo [idade] da classe [Autor] com o valor [-1] é menor que o valor mínimo [0]'
        assert messages.getMessage(invalido.errors.getFieldError('email'), Locale.ENGLISH) ==
                'Property [email] of class [Autor] with value [x] is not a valid e-mail address'

        autor('Ana', 30, 'ana@x.com')
        def duplicado = new Autor(nome: 'Outra', email: 'ana@x.com')
        assert !duplicado.validate() && duplicado.errors.getFieldError('email').code == 'default.not.unique.message'
        def a = Autor.findByEmail('ana@x.com')
        assert a.validate()

        // save inválido numa transação não grava as alterações da entidade gerenciada
        Autor.withTransaction {
            def managed = Autor.findByEmail('ana@x.com')
            managed.nome = 'X'
            assert managed.save() == null
        }
        assert Autor.findByEmail('ana@x.com').nome == 'Ana'
        Autor.withTransaction {
            def managed = Autor.findByEmail('ana@x.com')
            managed.nome = 'X'
            assert managed.save() == null
            managed.nome = 'Ana Maria'
            assert managed.save() != null
        }
        assert Autor.findByEmail('ana@x.com').nome == 'Ana Maria'

        def semTitulo = new Livro(autor: a)
        assert !semTitulo.validate()
        assert semTitulo.errors.getFieldError('titulo').code == 'default.null.message'
        def proibido = new Livro(titulo: 'Xis', autor: a)
        assert !proibido.validate()
        assert proibido.errors.getFieldError('titulo').codes.contains('livro.titulo.proibido')
    }

    @Test
    void 'dynamic finders'() {
        autor('Ana', 30, 'ana@x.com')
        autor('Bruno', 17)
        autor('Carla', 45)
        autor('anabela', null)
        assert Autor.findByNome('Bruno').idade == 17
        assert Autor.findByNome('Ninguém') == null
        assert Autor.findAllByIdadeGreaterThan(18)*.nome.sort() == ['Ana', 'Carla']
        assert Autor.findAllByIdadeGreaterThan('18')*.nome.sort() == ['Ana', 'Carla']
        assert Autor.findByNomeAndIdade('Ana', 30).email == 'ana@x.com'
        assert Autor.findAllByNomeOrIdade('Bruno', 45)*.nome.sort() == ['Bruno', 'Carla']
        assert Autor.findAllByNomeLike('Ana%')*.nome == ['Ana']
        assert Autor.findAllByNomeIlike('ana%')*.nome.sort() == ['Ana', 'anabela']
        assert Autor.findAllByNomeInList(['Ana', 'Carla'])*.nome.sort() == ['Ana', 'Carla']
        assert Autor.findAllByIdadeBetween(18, 40)*.nome == ['Ana']
        assert Autor.findAllByIdadeInRange(10..20)*.nome == ['Bruno']
        assert Autor.findAllByIdadeIsNull()*.nome == ['anabela']
        assert Autor.findAllByEmailIsNotNull()*.nome == ['Ana']
        assert Autor.findAllByIdadeLessThanEquals(30, [sort: 'idade', order: 'desc'])*.nome == ['Ana', 'Bruno']
        assert Autor.findAllByIdadeIsNotNull([max: 1, sort: 'nome'])*.nome == ['Ana']
        assert Autor.countByIdadeGreaterThanEquals(30) == 2
        assert Autor.listOrderByIdade(order: 'desc')*.nome.take(1) == ['Carla']
        assert Autor.findWhere(nome: 'Carla', idade: 45).nome == 'Carla'
        assert Autor.findAllWhere(idade: null)*.nome == ['anabela']
        assertThrows(MissingMethodException) { Autor.findByNaoExiste('x') }
        assertThrows(MissingMethodException) { Autor.metodoQualquer() }
    }

    @Test
    void 'modelo de mapeamento do GORM (PersistentEntity, associacoes)'() {
        def ctx = datastore.mappingContext
        assert datastore instanceof org.grails.orm.hibernate.HibernateDatastore
        def autor = ctx.getPersistentEntity(Autor.name)
        assert autor.javaClass == Autor && autor.identity.name == 'id' && !('id' in autor.persistentPropertyNames)
        assert ['nome', 'email', 'idade', 'ativo', 'livros'].every { it in autor.persistentPropertyNames }
        def livros = autor.getPropertyByName('livros')
        assert livros instanceof org.grails.datastore.mapping.model.types.OneToMany
        assert livros.owningSide && livros.bidirectional && livros.associatedEntity.javaClass == Livro
        def dono = ctx.getPersistentEntity(Livro.name).getPropertyByName('autor')
        assert dono instanceof org.grails.datastore.mapping.model.types.ManyToOne && !dono.owningSide && dono.bidirectional
        assert ctx.getPersistentEntity(Livro.name).getPropertyByName('titulo') instanceof org.grails.datastore.mapping.model.types.Simple
        assert ctx.getPersistentEntity('nao.Existe') == null
    }

    @Test
    void 'hasMany unidirecional e auto-referente com a tabela de juncao do GORM'() {
        def d = datastore.domains[org.grox.testdomain.Pasta]
        def rel = d.persistentProperties.relacionadas
        assert rel.kind == org.grox.data.DomainProperty.Kind.ONE_TO_MANY && rel.mappedBy == null
        assert [rel.joinTable, rel.joinColumn, rel.inverseJoinColumn] == ['pasta_pasta', 'pasta_relacionadas_id', 'pasta_id']
        // Livro pertence a Autor, não a Pasta: unidirecional também
        assert d.persistentProperties.livros.joinTable == 'pasta_livro'
        Livro.withTransaction {
            def filha = new org.grox.testdomain.Pasta(nome: 'filha').save(failOnError: true)
            def mae = new org.grox.testdomain.Pasta(nome: 'mae')
            mae.addToRelacionadas(filha)
            mae.save(failOnError: true, flush: true)
        }
        Livro.withTransaction {
            assert org.grox.testdomain.Pasta.findByNome('mae').relacionadas*.nome == ['filha']
            def linhas = org.grox.testdomain.Pasta.withSession { s -> s.createNativeQuery('select count(*) from pasta_pasta').singleResult }
            assert linhas == 1
        }
    }

    @Test
    void 'hasOne: one-to-one com a chave estrangeira no filho'() {
        def r = datastore.domains[org.grox.testdomain.Rosto].persistentProperties.nariz
        assert r.kind == org.grox.data.DomainProperty.Kind.ONE_TO_ONE && r.mappedBy == 'rosto' && r.cascadeAll
        def n = datastore.domains[org.grox.testdomain.Nariz].persistentProperties.rosto
        assert n.kind == org.grox.data.DomainProperty.Kind.ONE_TO_ONE && !n.mappedBy
        def id = Livro.withTransaction {
            def rosto = new org.grox.testdomain.Rosto(nome: 'r1')
            rosto.nariz = new org.grox.testdomain.Nariz(formato: 'reto', rosto: rosto)
            rosto.save(failOnError: true, flush: true).id
        }
        Livro.withTransaction {
            def rosto = org.grox.testdomain.Rosto.get(id)
            assert rosto.nariz.formato == 'reto' && rosto.nariz.rosto.is(rosto)
            def col = org.grox.testdomain.Rosto.withSession { s -> s.createNativeQuery('select rosto_id from nariz').singleResult }
            assert col == id
        }
        assert new org.grox.testdomain.Rosto(nome: 'sem') instanceof Object && org.grox.testdomain.Rosto.withTransaction { new org.grox.testdomain.Rosto(nome: 'sem nariz').save(flush: true) } != null
        def pe = datastore.mappingContext.getPersistentEntity(org.grox.testdomain.Rosto.name).getPropertyByName('nariz')
        assert pe instanceof org.grails.datastore.mapping.model.types.OneToOne && pe.owningSide
    }

    @Test
    void 'one-to-one sem hasOne: chave no dono e belongsTo do filho como inverso'() {
        def m = datastore.domains[org.grox.testdomain.Carro].persistentProperties.motor
        assert m.kind == org.grox.data.DomainProperty.Kind.MANY_TO_ONE && m.cascadeAll
        def c = datastore.domains[org.grox.testdomain.Motor].persistentProperties.carro
        assert c.kind == org.grox.data.DomainProperty.Kind.ONE_TO_ONE && c.mappedBy == 'motor' && !c.cascadeAll
        def id = Livro.withTransaction {
            def carro = new org.grox.testdomain.Carro(modelo: 'fusca')
            carro.motor = new org.grox.testdomain.Motor(serie: 'm1', carro: carro)
            carro.save(failOnError: true, flush: true).id
        }
        Livro.withTransaction {
            def motor = org.grox.testdomain.Motor.findBySerie('m1')
            assert motor.carro.id == id && motor.carro.motor.is(motor)
            def cols = org.grox.testdomain.Motor.withSession { s ->
                s.createNativeQuery("select column_name from information_schema.columns where lower(table_name) = 'motor'").resultList*.toString()*.toLowerCase()
            }
            assert !('carro_id' in cols)
            assert org.grox.testdomain.Carro.withSession { s -> s.createNativeQuery('select motor_id from carro').singleResult } == motor.id
        }
        def pe = datastore.mappingContext.getPersistentEntity(org.grox.testdomain.Motor.name).getPropertyByName('carro')
        assert pe instanceof org.grails.datastore.mapping.model.types.OneToOne && !pe.owningSide
    }

    @Test
    void 'eventos de domain: before no flush sem save, after e beforeValidate, sem disparo duplo'() {
        def E = org.grox.testdomain.Evento
        E.chamadas.clear()
        Long id = E.withTransaction { new org.grox.testdomain.Evento(nome: 'a').save(failOnError: true).id }
        assert E.chamadas == ['beforeInsert', 'beforeValidate', 'afterInsert']
        Date criado = Persistence.withNewSession { E.get(id).lastUpdated }

        // alteração só por dirty checking: antes, nenhum evento (nem lastUpdated, nem @Listener/audit log)
        E.chamadas.clear()
        Thread.sleep(5)
        E.withTransaction { E.get(id).nome = 'b' }
        assert E.chamadas == ['afterLoad', 'beforeUpdate', 'afterUpdate']
        def gravado = Persistence.withNewSession { E.get(id) }
        assert gravado.nome == 'b' && gravado.marca == 'alterado em beforeUpdate' && gravado.lastUpdated.after(criado)

        // com save(): beforeUpdate uma vez só (o save já disparou; o flush não repete)
        E.chamadas.clear()
        E.withTransaction {
            def e = E.get(id)
            e.nome = 'c'
            e.save(failOnError: true)
        }
        assert E.chamadas.count('beforeUpdate') == 1 && E.chamadas.contains('afterUpdate')

        E.chamadas.clear()
        E.withTransaction { E.get(id).delete() }
        assert E.chamadas.contains('afterDelete')
    }

    @Test
    void 'where vira HQL e a mesma forma de consulta reaproveita o plano do Hibernate'() {
        def ana = new Autor(nome: 'Ana', idade: 30).save(failOnError: true, flush: true)
        new Livro(titulo: 'Groovy', autor: ana, preco: 50.0G).save(failOnError: true, flush: true)
        new Livro(titulo: 'Gradle', autor: ana, preco: 20.0G).save(failOnError: true, flush: true)
        def stats = datastore.sessionFactory.statistics
        stats.clear()
        Closure<List<String>> consulta = { String nome, BigDecimal minimo ->
            Livro.where { autor.nome == nome && preco > minimo }.list(sort: 'titulo')*.titulo
        }
        assert consulta('Ana', 10.0G) == ['Gradle', 'Groovy']
        long hits = stats.queryPlanCacheHitCount
        assert consulta('Ana', 30.0G) == ['Groovy'] && consulta('Bia', 0.0G) == []
        // antes (JPA Criteria): a tradução era refeita a cada consulta
        assert stats.queryPlanCacheHitCount >= hits + 2
        assert stats.queries.any { it.contains('join e.autor j1') && it.contains('j1.nome = :p1') && it.contains('order by e.titulo asc') }
        assert Livro.where { autor.nome == 'Ana' }.count() == 2
        assert Livro.where { titulo in [] }.list() == [] && Livro.where { !(titulo in ['Gradle']) }.list()*.titulo == ['Groovy']
    }

    @Test
    void 'getAll numa consulta so, na ordem dos ids, e exists sem carregar a entidade'() {
        def ids = ['Aa', 'Bb', 'Cc'].collect { new Autor(nome: it).save(failOnError: true, flush: true).id }
        def stats = datastore.sessionFactory.statistics
        stats.clear()
        def lista = Autor.getAll([ids[2], -1L, ids[0], ids[2].toString(), null])
        assert lista*.nome == ['Cc', null, 'Aa', 'Cc', null]
        assert stats.prepareStatementCount == 1   // antes: uma consulta por id
        stats.clear()
        assert Autor.exists(ids[1]) && !Autor.exists(-1L) && !Autor.exists('abc') && !Autor.exists(null)
        assert stats.entityLoadCount == 0         // antes: carregava a entidade inteira
        assert Autor.getAll([]) == []
    }

    @Test
    void 'sort por associacao usa left join e sort invalido de params e ignorado'() {
        Livro.withTransaction {
            def a = new org.grox.testdomain.Carro(modelo: 'ordem-a')
            a.motor = new org.grox.testdomain.Motor(serie: 'ordem-z', carro: a)
            a.save(failOnError: true)
            new org.grox.testdomain.Carro(modelo: 'ordem-b').save(failOnError: true, flush: true)
        }
        try {
            def q = org.grox.testdomain.Carro.where { modelo in ['ordem-a', 'ordem-b'] }
            // antes: inner join, e o carro sem motor sumia
            assert q.list(sort: 'motor.serie')*.modelo as Set == ['ordem-a', 'ordem-b'] as Set
            def pagina = q.list(max: 10, sort: 'motor.serie', order: 'desc')
            assert pagina.size() == 2 && pagina.totalCount == 2   // antes: 1 linha e totalCount 2
            assert q.list(sort: 'motor.naoExiste')*.modelo.size() == 2   // vindo de params: ignorado (antes, 500)
            // o filtro pela associação continua sendo inner join
            assert org.grox.testdomain.Carro.where { motor.serie == 'ordem-z' }.list(sort: 'motor.serie')*.modelo == ['ordem-a']
        } finally {
            org.grox.testdomain.Carro.executeUpdate("delete from Carro where modelo like 'ordem-%'")
            org.grox.testdomain.Motor.executeUpdate("delete from Motor where serie = 'ordem-z'")
        }
    }

    @Test
    void 'hibernate-filter: filtro padrao, withoutHibernateFilters, enable e disable'() {
        def P = org.grox.testdomain.Produto
        Livro.withTransaction {
            new org.grox.testdomain.Produto(nome: 'caro A').save(failOnError: true)
            new org.grox.testdomain.Produto(nome: 'barato B').save(failOnError: true)
            new org.grox.testdomain.Produto(nome: 'caro C', ativo: false).save(failOnError: true, flush: true)
        }
        Livro.withTransaction {
            assert P.list()*.nome.sort() == ['barato B', 'caro A']                       // ativoFilter (default: true)
            assert P.withoutHibernateFilters { P.list()*.nome.sort() } == ['barato B', 'caro A', 'caro C']
            assert P.list().size() == 2                                                   // religado depois do bloco
            P.enableHibernateFilter('caroFilter')
            assert P.list()*.nome == ['caro A']
            P.disableHibernateFilter('caroFilter')
            assert P.withoutHibernateFilter('ativoFilter') { P.count() } == 3
        }
    }

    @Test
    void 'first com argumentos, opcoes no mapa do executeQuery e offset negativo'() {
        autor('Ana', 30)
        autor('Bruno', 17)
        assert Autor.first([sort: 'nome', readOnly: true]).nome == 'Ana' && Autor.last(sort: 'nome').nome == 'Bruno'
        assert Autor.executeQuery('select a.nome from Autor a order by a.nome', [max: 1]) == ['Ana']
        assert Autor.executeQuery('select a.nome from Autor a where a.idade > :i order by a.nome', [i: 10, max: 1, offset: 1]) == ['Bruno']
        assert Autor.list(max: 10, offset: -10, sort: 'nome')*.nome == ['Ana', 'Bruno']
        // lock, readOnly, timeout, fetchSize e flushMode aplicados (antes só max/offset)
        Autor.withTransaction {
            def a = Autor.findAll('from Autor a where a.nome = :n', [n: 'Ana', lock: true, timeout: 5, fetchSize: 10])[0]
            assert a.nome == 'Ana' && Autor.withSession { it.getCurrentLockMode(a) } == org.hibernate.LockMode.PESSIMISTIC_WRITE
            def ro = Autor.findAll('from Autor a where a.nome = :n', [n: 'Bruno', readOnly: true, flushMode: 'manual'])[0]
            assert Autor.withSession { it.isReadOnly(ro) }
        }
        assert Autor.createCriteria().list(max: 10, offset: -10) { order 'nome' }*.nome == ['Ana', 'Bruno']
    }

    @Test
    void 'finders booleanos do GORM'() {
        autor('Ana', 30)
        autor('Bruno', 17).with { ativo = false; save(flush: true) }
        autor('Carla', 45)
        assert Autor.findAllAtivo()*.nome.sort() == ['Ana', 'Carla']
        assert Autor.findAllAtivo(true)*.nome.sort() == ['Ana', 'Carla']   // argumento extra ignorado, como no GORM
        assert Autor.findAllNotAtivo()*.nome == ['Bruno']
        assert Autor.findAllAtivo([sort: 'nome', order: 'desc'])*.nome == ['Carla', 'Ana']
        assert Autor.findAtivoByNome('Carla').idade == 45
        assert Autor.findAtivoByNome('Bruno') == null
        // Or: a condição booleana continua obrigatória (ativo AND (nome OR idade))
        assert Autor.findAllAtivoByNomeOrIdade('Bruno', 45)*.nome == ['Carla']
        assertThrows(MissingMethodException) { Autor.findAllNome() }   // não booleana
    }

    @Test
    void 'associacoes: addTo, cascade, lazy loading, removeFrom e delete em cascata'() {
        def a = new Autor(nome: 'Ana')
        a.addToLivros(titulo: 'Primeiro', preco: 10.5G).addToLivros(new Livro(titulo: 'Segundo'))
        assert a.livros.every { it.autor.is(a) }
        a.save(failOnError: true)
        assert Livro.count() == 2 && a.livros.every { it.id }

        Persistence.withNewSession {
            def carregado = Autor.get(a.id)
            assert carregado.livros*.titulo.sort() == ['Primeiro', 'Segundo']
            def livro = Livro.findByTitulo('Segundo')
            assert livro.autor.nome == 'Ana'
            assert Livro.findAllByAutor(carregado).size() == 2
            assert Livro.findAllBySituacao(Situacao.ATIVO).size() == 2
            assert Livro.findAllBySituacao('ATIVO').size() == 2
        }

        Autor.withTransaction {
            def carregado = Autor.get(a.id)
            carregado.removeFromLivros(carregado.livros.find { it.titulo == 'Primeiro' })
            carregado.save()
        }
        assert Livro.list()*.titulo == ['Segundo']

        Autor.withTransaction { Autor.get(a.id).delete() }
        assert Autor.count() == 0 && Livro.count() == 0
    }

    @Test
    void 'transacoes: commit, rollback por excecao e por status, participacao'() {
        Autor.withTransaction { autor('Ana') }
        assertThrows(IllegalStateException) {
            Autor.withTransaction {
                autor('Bruno')
                throw new IllegalStateException('falha')
            }
        }
        Autor.withTransaction { status ->
            autor('Carla')
            status.setRollbackOnly()
        }
        Autor.withTransaction {
            autor('Dani')
            Autor.withTransaction { st -> assert !st.newTransaction; autor('Edu') }
        }
        assert Autor.list()*.nome == ['Ana', 'Dani', 'Edu']
    }

    @Test
    void 'HQL: executeQuery, findAll, find e executeUpdate'() {
        autor('Ana', 30)
        autor('Bruno', 17)
        assert Autor.executeQuery('select a.nome from Autor a where a.idade > :i', [i: 18]) == ['Ana']
        assert Autor.executeQuery('select count(a) from Autor a')[0] == 2
        assert Autor.findAll('from Autor a where a.idade < ?1', [18])*.nome == ['Bruno']
        assert Autor.find('from Autor a order by a.idade desc').nome == 'Ana'
        assert Autor.executeUpdate('update Autor a set a.idade = a.idade + 1') == 2
        assert Autor.findByNome('Bruno').idade == 18
    }

    @Test
    void 'JSON de entidades com associacoes rasas'() {
        def a = new Autor(nome: 'Ana', idade: 30)
        a.addToLivros(titulo: 'Um')
        a.save(failOnError: true)
        Persistence.withNewSession {
            def json = new groovy.json.JsonSlurper().parseText(JsonSupport.toJson(Autor.get(a.id)))
            assert json.id == a.id && json.nome == 'Ana' && json.idade == 30
            assert json.livros == [[id: a.livros[0].id]]
            assert !json.containsKey('version') && !json.containsKey('errors')
            def livroJson = new groovy.json.JsonSlurper().parseText(JsonSupport.toJson(Livro.findByTitulo('Um')))
            assert livroJson.autor == [id: a.id] && livroJson.situacao == 'ATIVO'
        }
    }
}
