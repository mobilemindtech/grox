package org.grox

import grails.events.annotation.gorm.Listener
import grails.gorm.services.Service
import org.grox.core.Config
import org.grox.data.DataServices
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.GormEvents
import org.grox.plugins.quartz.CronExpression
import org.grox.routing.UrlMappingsHolder
import org.grox.testdomain.Aluno
import org.grox.testdomain.Inscricao
import org.grox.testdomain.Order
import org.grox.testdomain.Turma
import org.grox.view.taglib.TagAttributes
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import java.time.ZoneId
import java.time.ZonedDateTime

/** Compatibilidades levantadas no piloto de migração (app-a). */
class PilotCompatTest {

    static class SenhaListener {
        @Listener(Aluno)
        void antesDeInserir(PreInsertEvent e) {
            codificar(e.entityObject as Aluno, e)
        }

        @Listener(Aluno)
        void antesDeAtualizar(PreUpdateEvent e) {
            Aluno a = e.entityObject as Aluno
            if (a.isDirty('senha')) codificar(a, e)
        }

        private static void codificar(Aluno a, e) {
            if (a.senha) e.entityAccess.setProperty('senha', 'hash:' + a.senha)
        }
    }

    @Service(Aluno)
    static interface AlunoService {
        Aluno get(Serializable id)
        List<Aluno> list(Map args)
        Long count()
        Aluno save(Aluno aluno)
        void delete(Serializable id)
        Aluno findByNome(String nome)
    }

    @BeforeAll
    static void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:pilotCompat;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop']])
        Gorm.init(new Datastore(config, [Turma, Aluno, Order, Inscricao], PilotCompatTest.classLoader, true))
        GormEvents.register(new SenhaListener())
    }

    @AfterAll
    static void stop() {
        GormEvents.reset()
        Gorm.reset()
    }

    @Test
    void 'many-to-many com addTo nos dois lados e tabela de juncao'() {
        Turma.withTransaction {
            def t = new Turma(nome: 'A').save(failOnError: true)
            def a = new Aluno(nome: 'Ana')
            t.addToAlunos(a)
            assert a.turmas*.nome == ['A']
            a.save(failOnError: true, flush: true)
        }
        Turma.withTransaction {
            def t = Turma.findByNome('A')
            assert t.alunos*.nome == ['Ana']
            assert Aluno.findByNome('Ana').turmas*.nome == ['A']
        }
    }

    @Test
    void 'construtor com Map mesmo com construtor sem argumentos declarado, long primitivo'() {
        def a = new Aluno(nome: 'Bia', controller: 'ignorado', pontos: '7')
        assert a.nome == 'Bia' && a.codigo.startsWith('A-') && a.pontos == 7L
    }

    @Test
    void 'listener PreInsert e PreUpdate com isDirty'() {
        Aluno.withTransaction {
            new Aluno(nome: 'Caio', senha: 'x').save(failOnError: true, flush: true)
        }
        Aluno.withTransaction {
            def a = Aluno.findByNome('Caio')
            assert a.senha == 'hash:x'
            a.nome = 'Caio 2'
            assert a.isDirty() && !a.isDirty('senha') && a.getPersistentValue('nome') == 'Caio'
            a.save(flush: true)
            assert a.senha == 'hash:x'
            a.senha = 'y'
            a.save(flush: true)
            assert a.senha == 'hash:y'
        }
    }

    @Test
    void 'load de entidade sem proxy lazy (ninguem aponta para ela por many-to-one)'() {
        def id = Order.withTransaction { new Order(codigo: 'SP', bruto: 1, desconto: 0).save(failOnError: true, flush: true).id }
        Order.withTransaction {
            def o = Order.load(id)
            assert o.getClass() == Order
            assert o.codigo == 'SP'
        }
        // Turma e Aluno são alvo de many-to-one (Inscricao, Order): continuam com proxy lazy
        def tid = Turma.withTransaction { new Turma(nome: 'Com proxy').save(failOnError: true, flush: true).id }
        Turma.withTransaction {
            def t = Turma.load(tid)
            assert t.getClass() != Turma
            assert t.nome == 'Com proxy'
        }
    }

    @Test
    void 'chave composta como o UserRole do spring-security'() {
        def (tid, aid) = Turma.withTransaction {
            def t = new Turma(nome: 'Composta').save(failOnError: true)
            def a = new Aluno(nome: 'Eva').save(failOnError: true)
            new Inscricao(turma: t, aluno: a).save(failOnError: true, flush: true, insert: true)
            [t.id, a.id]
        }
        Inscricao.withTransaction {
            def t = Turma.get(tid), a = Aluno.get(aid)
            assert Inscricao.countByTurmaAndAluno(t, a) == 1
            def i = Inscricao.findByTurma(t)
            assert i.aluno.nome == 'Eva' && i.ident().is(i)
            i.observacao = 'alterada'
            i.save(flush: true)
            assert Inscricao.get(new Inscricao(turma: t, aluno: a)).observacao == 'alterada'
            assert Inscricao.where { turma == t }.deleteAll() == 1
            assert Inscricao.count() == 0
        }
    }

    @Test
    void 'formula e nomes de tabela e coluna em snake_case mesmo entre aspas'() {
        Order.withTransaction {
            def a = new Aluno(nome: 'Responsavel').save(failOnError: true)
            new Order(codigo: 'X', bruto: 10, desconto: 3, responsavelPrincipal: a).save(failOnError: true, flush: true)
        }
        Order.withTransaction {
            assert Order.findByCodigo('X').liquido == 7
        }
        def meta = [:]
        Order.withSession { org.hibernate.Session s ->
            s.doWork { java.sql.Connection c ->
                def rs = c.metaData.getColumns(null, null, null, null)
                while (rs.next()) meta.computeIfAbsent(rs.getString('TABLE_NAME').toLowerCase()) { [] } << rs.getString('COLUMN_NAME').toLowerCase()
            }
        }
        assert meta.containsKey('order') && !meta.keySet().any { it.contains('Order') }
        assert 'responsavel_principal_id' in meta.order && !('liquido' in meta.order)
        // regra do GORM: dígito e sigla não separam (valor10Meses → valor10meses_id no banco do Grails)
        assert meta.order.containsAll(['responsavel2turno_id', 'codigoerp'])
        assert meta.inscricao.containsAll(['turma_id', 'aluno_id']) && !('id' in meta.inscricao)
    }

    @Test
    void 'data service por interface'() {
        AlunoService svc = (AlunoService) DataServices.create(AlunoService, PilotCompatTest.classLoader)
        def a = svc.save(new Aluno(nome: 'Duda'))
        assert a.id && svc.get(a.id).nome == 'Duda' && svc.findByNome('Duda').id == a.id
        long antes = svc.count()
        svc.delete(a.id)
        assert svc.count() == antes - 1
    }

    @Test
    void 'cron no formato do Quartz'() {
        def z = ZoneId.of('America/Sao_Paulo')
        def seg = ZonedDateTime.of(2026, 9, 28, 9, 0, 0, 0, z) // segunda
        def c = new CronExpression('0 0 8,10,12,17,20 ? * MON-FRI')
        assert c.nextAfter(seg) == ZonedDateTime.of(2026, 9, 28, 10, 0, 0, 0, z)
        assert c.nextAfter(ZonedDateTime.of(2026, 10, 2, 21, 0, 0, 0, z)) == ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, z)
        assert new CronExpression('0 0 2 1 * ?').nextAfter(seg) == ZonedDateTime.of(2026, 10, 1, 2, 0, 0, 0, z)
        assert new CronExpression('0 0/15 * * * ?').nextAfter(seg) == ZonedDateTime.of(2026, 9, 28, 9, 15, 0, 0, z)
        assert new CronExpression('0 0 12 ? * WED').nextAfter(seg) == ZonedDateTime.of(2026, 9, 30, 12, 0, 0, 0, z)
    }

    @Test
    void 'config com placeholders, getProperty e bloco que nao e trocado por variavel de ambiente'() {
        Map root = [a: [url: 'http://x'], b: '${a.url}/api', c: '${NAO_EXISTE_GROX:padrao}', d: '${NAO_EXISTE_GROX:}',
                    path: [lista: ['${a.url}']]]
        Config.resolvePlaceholders(root, root)
        def cfg = new Config(root)
        assert cfg.getProperty('b') == 'http://x/api' && cfg.c == 'padrao' && cfg.d == '' && cfg.path.lista == ['http://x']
        assert cfg.a.url == 'http://x'
        // PATH existe no ambiente, mas 'path' é um bloco do YAML
        assert cfg.path instanceof Map
    }

    @Test
    void 'JSONObject e JSONArray'() {
        def o = new JSONObject('{"a": 1, "b": {"c": [1, {"d": 2}]}}')
        assert o.b instanceof JSONObject && o.b.c instanceof JSONArray && o.b.c[1] instanceof JSONObject
        assert new JSONObject(o.toString()) == o
        def req = new JSONObject('{"max": 20, "offset": "5", "ativo": "true", "vazio": ""}')
        assert req.optInt('max', 15) == 20 && req.optInt('offset', 0) == 5 && req.optInt('falta', 15) == 15
        assert req.optString('search', null) == null && req.optString('vazio', 'x') == '' && req.optLong('max') == 20L
        assert req.optBoolean('ativo') && req.getInt('max') == 20 && req.optJSONObject('max') == null
        org.junit.jupiter.api.Assertions.assertThrows(org.grails.web.json.JSONException) { req.getInt('falta') }
        // como no Grails, o converter fecha o writer: o segundo render (json(data); render()) é descartado
        def resp = new org.grox.http.GroxResponse()
        new grails.converters.JSON([a: 1]).render(resp)
        new grails.converters.JSON([a: 1]).render(resp)
        resp.writer.write('x')
        assert resp.text == '{"a":1}'
        resp.resetBuffer()
        resp.writer.write('ok')
        assert resp.text == 'ok'
        assert grails.converters.JSON.parse('[{"x": 1}]') instanceof JSONArray
    }

    @Test
    void 'atributos de tag com conversao tipada'() {
        def attrs = new TagAttributes([total: '12', ativo: 'true', 'a.b': 1])
        assert attrs.int('total') == 12 && attrs.boolean('ativo') && attrs.int('x') == null && attrs.a == null
    }

    static class AppMappings {
        static mappings = {
            prefixos('adm').each { p ->
                "/${p}/painel"(controller: 'painel')
            }
        }

        private static List<String> prefixos(String base) { [base, base + '2'] }
    }

    @Test
    void 'UrlMappings chama metodo da propria classe'() {
        def holder = UrlMappingsHolder.build([AppMappings])
        assert holder.mappings*.pattern.containsAll(['/adm/painel', '/adm2/painel'])
    }
}
