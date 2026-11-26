package org.grox

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.query.DetachedCriteria
import org.grox.testdomain.Autor
import org.grox.testdomain.Capitulo
import org.grox.testdomain.Livro
import org.grox.testdomain.Situacao
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class QueryTest {

    @Test
    void 'parametros posicionais do Hibernate 5 (base zero e JDBC) normalizados para o Hibernate 7'() {
        def ord = { String h -> org.grox.data.GormStaticApi.ordinals(h) }
        assert ord('delete from X where a = ?0 and b = ?1') == 'delete from X where a = ?1 and b = ?2'
        assert ord('from X where a = ? and b = ?') == 'from X where a = ?1 and b = ?2'
        assert ord('from X where a = ?1 and b = ?2') == 'from X where a = ?1 and b = ?2'
        assert ord("from X where a = ?0 and c = 'quem?0'") == "from X where a = ?1 and c = 'quem?0'"
        assert ord('from X') == 'from X'
    }


    @BeforeAll
    static void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:queryTest;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop']])
        Gorm.init(new Datastore(config, [Autor, Livro, Capitulo], QueryTest.classLoader))
    }

    @AfterAll
    static void stop() {
        Gorm.reset()
    }

    @BeforeEach
    void dados() {
        Capitulo.executeUpdate('delete from Capitulo')
        Livro.executeUpdate('delete from Livro')
        Autor.executeUpdate('delete from Autor')
        def ana = new Autor(nome: 'Ana', idade: 30, email: 'ana@x.com')
        ana.addToLivros(titulo: 'Groovy', preco: 50.0G).addToLivros(titulo: 'Gradle', preco: 20.0G, situacao: Situacao.ESGOTADO)
        ana.save(failOnError: true)
        def bruno = new Autor(nome: 'Bruno', idade: 17)
        bruno.addToLivros(titulo: 'Java', preco: 35.0G)
        bruno.save(failOnError: true)
        new Autor(nome: 'Carla', idade: 45).save(failOnError: true)
    }

    @Test
    void 'where com operadores, variaveis locais, mapas, associacoes e encadeamento'() {
        int minimo = 18
        Map params = [nome: 'Ana']
        assert Autor.where { idade > minimo }.list()*.nome == ['Ana', 'Carla']
        assert Autor.where { nome == params.nome }.find().idade == 30
        assert Autor.where { idade >= 17 && idade < 45 }.list()*.nome == ['Ana', 'Bruno']
        assert Autor.where { nome == 'Ana' || idade > 40 }.list()*.nome == ['Ana', 'Carla']
        assert Autor.where { !(nome == 'Ana') }.list()*.nome == ['Bruno', 'Carla']
        assert Autor.where { nome != 'Ana' }.count() == 2
        assert Autor.where { nome ==~ 'A%' }.list()*.nome == ['Ana']
        assert Autor.where { nome =~ 'b%' }.list()*.nome == ['Bruno']
        assert Autor.where { nome in ['Ana', 'Carla'] }.list()*.nome == ['Ana', 'Carla']
        assert Autor.where { !(nome in ['Ana', 'Carla']) }.list()*.nome == ['Bruno']
        assert Autor.where { idade in 16..30 }.list()*.nome == ['Ana', 'Bruno']
        assert Autor.where { email == null }.list()*.nome == ['Bruno', 'Carla']
        assert Autor.where { email != null }.list()*.nome == ['Ana']
        assert Livro.where { autor.nome == 'Ana' && preco > 30 }.list()*.titulo == ['Groovy']
        assert Livro.where { situacao == Situacao.ESGOTADO }.list()*.titulo == ['Gradle']
        assert Livro.where { situacao == 'ATIVO' }.count() == 2
        // várias linhas = AND
        assert Autor.where {
            idade > 10
            nome ==~ '%a%'
        }.list()*.nome == ['Ana', 'Carla']
        // encadeado e imutável
        DetachedCriteria adultos = Autor.where { idade >= 18 }
        assert adultos.where { nome == 'Carla' }.list()*.nome == ['Carla']
        assert adultos.count() == 2
        assert adultos.sort('idade', 'desc').max(1).list()*.nome == ['Carla']
        assert adultos.list(sort: 'nome', order: 'desc')*.nome == ['Carla', 'Ana']
        assert adultos.exists() && !Autor.where { idade > 100 }.exists()
        assert (adultos as List)*.nome == ['Ana', 'Carla']
        assert Autor.whereAny {
            nome == 'Bruno'
            idade > 40
        }.list()*.nome == ['Bruno', 'Carla']
    }

    @Test
    void 'where com associacao de dois niveis'() {
        [Groovy: [['Closures', 40], ['Metaprogramacao', 120]], Gradle: [['Tarefas', 60]], Java: [['Streams', 90]]].each { titulo, caps ->
            Livro livro = Livro.findByTitulo(titulo)
            caps.each { t, p -> new Capitulo(titulo: t, paginas: p, livro: livro).save(failOnError: true) }
        }
        String nome = 'Ana'
        assert Capitulo.where { livro.autor.nome == 'Ana' }.list(sort: 'titulo')*.titulo == ['Closures', 'Metaprogramacao', 'Tarefas']
        // misturando níveis: propriedade própria, um e dois níveis
        assert Capitulo.where { paginas > 50 && livro.autor.nome == nome }.list(sort: 'titulo')*.titulo == ['Metaprogramacao', 'Tarefas']
        assert Capitulo.where { livro.preco > 30 && livro.autor.idade >= 18 }.list(sort: 'titulo')*.titulo == ['Closures', 'Metaprogramacao']
        assert Capitulo.where { livro.autor.nome ==~ 'B%' || livro.situacao == Situacao.ESGOTADO }.list(sort: 'titulo')*.titulo == ['Streams', 'Tarefas']
        assert Capitulo.where { !(livro.autor.nome in ['Ana']) }.list()*.titulo == ['Streams']
        assert Capitulo.where { livro.autor.email != null }.count() == 3
        // encadeado e em whereAny, find/findAll e DetachedCriteria
        DetachedCriteria daAna = Capitulo.where { livro.autor.nome == 'Ana' }
        assert daAna.where { livro.titulo == 'Groovy' && paginas < 100 }.list()*.titulo == ['Closures']
        assert Capitulo.whereAny {
            livro.autor.idade < 18
            paginas > 100
        }.list(sort: 'titulo')*.titulo == ['Metaprogramacao', 'Streams']
        assert Capitulo.find { livro.autor.nome == 'Bruno' }.titulo == 'Streams'
        assert Capitulo.findAll { livro.autor.idade > 18 && paginas <= 60 }*.titulo.sort() == ['Closures', 'Tarefas']
        assert Capitulo.where { livro.autor.nome == 'Ana' }.updateAll(paginas: 1) == 3
        assert Capitulo.where { livro.autor.nome == 'Bruno' }.deleteAll() == 1
        assert Capitulo.where { paginas == 1 }.count() == 3 && Capitulo.count() == 3
    }

    @Test
    void 'comparacao entre propriedades, find e findAll com closure'() {
        new Autor(nome: 'd@x.com', idade: 20, email: 'd@x.com').save(failOnError: true)
        assert Autor.where { nome == email }.list()*.nome == ['d@x.com']
        assert Autor.findAll { idade < 20 }*.nome == ['Bruno']
        assert Autor.findAll([max: 1]) { idade > 10 }*.nome == ['Ana']
        assert Autor.find { nome == 'Carla' }.idade == 45
        // closures de coleções não são afetados
        assert [1, 2, 3].findAll { it > 1 } == [2, 3]
    }

    @Test
    void 'deleteAll e updateAll'() {
        assert Livro.where { preco < 30 }.updateAll(preco: 25.0G) == 1
        assert Livro.findByTitulo('Gradle').preco == 25.0G
        assert Livro.where { autor.nome == 'Bruno' }.count() == 1
        assert Livro.where { titulo == 'Java' }.deleteAll() == 1
        assert Livro.count() == 2
    }

    @Test
    void 'createCriteria: restricoes, junctions, associacoes, ordem, paginacao e projecoes'() {
        def c = Autor.createCriteria()
        assert c.list {
            ge('idade', 17)
            or {
                like('nome', 'A%')
                eq('nome', 'Bruno')
            }
            order('idade', 'desc')
        }*.nome == ['Ana', 'Bruno']
        assert Autor.createCriteria().list(max: 1, offset: 1) { order('nome') }*.nome == ['Bruno']
        // com max, PagedResultList: a página e o total dos critérios (como no GORM)
        def pagina = Autor.createCriteria().list(max: 1, offset: 0) { gt('idade', 18); order('nome') }
        assert pagina instanceof grails.gorm.PagedResultList && pagina*.nome == ['Ana'] && pagina.totalCount == 2
        assert !(Autor.createCriteria().list { order('nome') } instanceof grails.gorm.PagedResultList)
        def onde = Autor.where { idade > 18 }.list(max: 1, sort: 'nome')
        assert onde*.nome == ['Ana'] && onde.totalCount == 2
        def todos = Autor.list(max: 2, sort: 'nome')
        assert todos*.nome == ['Ana', 'Bruno'] && todos.totalCount == 3
        assert !(Autor.list() instanceof grails.gorm.PagedResultList)
        assert Autor.createCriteria().get { eq('nome', 'Carla') }.idade == 45
        assert Autor.createCriteria().count { gt('idade', 18) } == 2
        assert Autor.createCriteria().list { not { eq('nome', 'Ana') }; isNull('email') }*.nome == ['Bruno', 'Carla']
        assert Autor.createCriteria().list { between('idade', 18, 40) }*.nome == ['Ana']
        assert Autor.createCriteria().list { 'in'('nome', ['Ana', 'Bruno']); maxResults(1); order('nome') }*.nome == ['Ana']
        assert Livro.createCriteria().list { autor { eq('nome', 'Ana') }; order('titulo') }*.titulo == ['Gradle', 'Groovy']
        assert Livro.withCriteria { gt('preco', 30.0G); order('preco', 'desc') }*.titulo == ['Groovy', 'Java']
        assert Autor.createCriteria().get { projections { count() } } == 3
        assert Autor.createCriteria().list { projections { property('nome') }; order('nome') } == ['Ana', 'Bruno', 'Carla']
        assert Livro.createCriteria().get { projections { sum('preco') } } == 105.0G
        assert Livro.createCriteria().get { projections { max('preco') } } == 50.0G
        def porAutor = Livro.createCriteria().list {
            projections {
                groupProperty('autor.nome')
                count()
            }
            order('autor.nome')
        }
        assert porAutor == [['Ana', 2L], ['Bruno', 1L]]
        // associação dentro de projections, como no GORM
        def porAutorAninhado = Livro.createCriteria().list {
            projections {
                autor {
                    groupProperty 'nome'
                    count 'nome'
                }
            }
            order('autor.nome')
        }
        assert porAutorAninhado == [['Ana', 2L], ['Bruno', 1L]]
        assert Autor.withCriteria { gte('idade', 18); lte('idade', 40) }*.nome == ['Ana']
        // parâmetro posicional solto (sem lista), como no GORM
        assert Autor.executeQuery('select a.nome from Autor a where a.idade = ?0', 45) == ['Carla']
        // GString nos parâmetros (nomeados, posicionais e em listas), como no GORM
        String q = 'arl'
        assert Autor.executeQuery('select a.nome from Autor a where a.nome like :n', [n: "%${q}%"]) == ['Carla']
        assert Autor.executeQuery('select a.nome from Autor a where a.nome like ?0', ["%${q}%"]) == ['Carla']
        assert Autor.executeQuery('select a.nome from Autor a where a.nome in (:ns)', [ns: ["C${q}a"]]) == ['Carla']
        assert Autor.findByNome("C${q}a")?.nome == 'Carla' && Autor.findAllByNomeLike("%${q}%")*.nome == ['Carla']
        assert Autor.withCriteria { eq('nome', "C${q}a") }*.nome == ['Carla'] && Autor.withCriteria { ilike('nome', "%${q}%") }*.nome == ['Carla']
        String alvo = "C${q}a"
        assert Autor.where { nome == alvo }.list()*.nome == ['Carla']
        assertThrows(IllegalArgumentException) { Autor.createCriteria().list { eq('naoExiste', 1) } }
    }

    @Test
    void 'expressao invalida em where gera erro de compilacao'() {
        def e = assertThrows(MultipleCompilationErrorsException) {
            new GroovyShell(QueryTest.classLoader).parse('org.grox.testdomain.Autor.where { nome.size() > 3 }')
        }
        assert e.message.contains('o lado esquerdo em where { } deve ser uma propriedade')
        def e2 = assertThrows(MultipleCompilationErrorsException) {
            new GroovyShell(QueryTest.classLoader).parse('org.grox.testdomain.Autor.where { naoExiste == 3 }')
        }
        assert e2.message.contains('deve ser uma propriedade de Autor')
        def e3 = assertThrows(MultipleCompilationErrorsException) {
            new GroovyShell(QueryTest.classLoader).parse("org.grox.testdomain.Capitulo.where { livro.autor.naoExiste == 'x' }")
        }
        assert e3.message.contains("Autor não tem 'naoExiste' (livro.autor.naoExiste)")
        def e4 = assertThrows(MultipleCompilationErrorsException) {
            new GroovyShell(QueryTest.classLoader).parse("org.grox.testdomain.Livro.where { autor.naoExiste == 'x' }")
        }
        assert e4.message.contains("Autor não tem 'naoExiste'")
        def e5 = assertThrows(MultipleCompilationErrorsException) {
            new GroovyShell(QueryTest.classLoader).parse("org.grox.testdomain.Autor.where { livros.naoExiste == 'x' }")
        }
        assert e5.message.contains("Livro não tem 'naoExiste'")
        // caminhos válidos compilam
        new GroovyShell(QueryTest.classLoader).parse("org.grox.testdomain.Capitulo.where { livro.autor.id == 1 && livro.autor.livros.titulo == 'x' }")
    }

    @Test
    void 'typeMismatch no binding sobrevive ao validate'() {
        def a = new Autor(nome: 'Erro', idade: 'abc')
        assert a.idade == null
        assert !a.validate()
        def err = a.errors.getFieldError('idade')
        assert err.codes.contains('typeMismatch.java.lang.Integer') && err.rejectedValue == 'abc'
        assert a.errors.getFieldErrors('idade').size() == 1
        def messages = new org.grox.i18n.MessageSource(QueryTest.classLoader, 'messages', Locale.forLanguageTag('pt-BR'))
        assert messages.getMessage(err, null) == 'O campo [idade] deve ser um número válido'
        a.clearErrors()
        a.idade = 20
        assert a.validate()
    }
}
