package org.grox

import org.grox.http.FlashScope
import org.grox.security.Authentication
import org.grox.security.GroxUser
import org.grox.session.RedisSessionRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Sobe um redis-server local em porta livre (pulado se não estiver instalado). */
class RedisSessionTest {

    static Process redis
    static int port
    static RedisSessionRepository repo

    static int freePort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    static Process startRedis(int port) {
        def p = new ProcessBuilder('redis-server', '--port', port.toString(), '--save', '', '--appendonly', 'no')
                .redirectErrorStream(true).start()
        for (int i = 0; i < 50; i++) {
            try {
                new Socket('localhost', port).close()
                return p
            } catch (IOException ignored) {
                Thread.sleep(100)
            }
        }
        p.destroy()
        throw new IllegalStateException('redis-server não subiu')
    }

    static boolean redisInstalled() {
        try {
            new ProcessBuilder('redis-server', '--version').start().waitFor() == 0
        } catch (IOException ignored) {
            false
        }
    }

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(redisInstalled(), 'redis-server não instalado')
        port = freePort()
        redis = startRedis(port)
        repo = new RedisSessionRepository("redis://localhost:${port}", 'test:session:', 'SID', 60, RedisSessionTest.classLoader)
    }

    @AfterAll
    static void stop() {
        repo?.close()
        redis?.destroy()
    }

    @Test
    void 'grava, carrega, altera parcialmente, troca id e invalida'() {
        def s = repo.create()
        def user = new GroxUser(username: 'ana', password: 'nao-vai', tenantId: 'acme')
        user.roles = ['ROLE_USER']
        s.setAttribute('auth', new Authentication(principal: user, method: 'form'))
        s.setAttribute('contador', 1)
        s.setAttribute('carrinho', ['a'])
        def flash = new FlashScope()
        flash.put('message', 'oi')
        s.setAttribute('flash', flash)
        repo.save(s)
        assert !s.isNew()

        def loaded = repo.find(s.id)
        Authentication a = (Authentication) loaded.getAttribute('auth')
        assert a.name == 'ana' && a.principal.roles == ['ROLE_USER'] as Set && a.principal.tenantId == 'acme'
        assert a.principal.password == null
        assert ((FlashScope) loaded.getAttribute('flash')).get('message') == 'oi'

        // lista lida e alterada sem setAttribute também é gravada (valor mutável acessado)
        ((List) loaded.getAttribute('carrinho')) << 'b'
        loaded.setAttribute('contador', 2)
        loaded.removeAttribute('flash')
        repo.save(loaded)
        def again = repo.find(s.id)
        assert again.getAttribute('carrinho') == ['a', 'b'] && again.getAttribute('contador') == 2 && again.getAttribute('flash') == null

        String old = again.id
        again.changeSessionId()
        repo.save(again)
        assert repo.find(old) == null && repo.find(again.id).getAttribute('contador') == 2

        again.invalidate()
        assert repo.find(again.id) == null
        assert repo.find('inexistente') == null
    }

    @Test
    void 'atributo nao serializavel falha com mensagem clara'() {
        def s = repo.create()
        s.setAttribute('ruim', new Object())
        def e = org.junit.jupiter.api.Assertions.assertThrows(NotSerializableException) { repo.save(s) }
        assert e.message.contains("'ruim'")
    }

    @Test
    void 'autenticacao e flash lidos nao sao regravados, e logout concorrente nao e desfeito'() {
        def s = repo.create()
        s.setAttribute('grox.security.authentication', new Authentication(principal: new GroxUser(username: 'ana'), method: 'form'))
        s.setAttribute('grox.flash', new FlashScope())
        s.setAttribute('carrinho', ['a'])
        repo.save(s)
        def raw = io.lettuce.core.RedisClient.create("redis://localhost:${port}")
        def cmd = raw.connect().sync()
        try {
            String k = 'test:session:' + s.id
            // sentinelas: se o save regravar o campo, elas somem
            cmd.hset(k, 'attr:grox.security.authentication', 'SENTINELA')
            cmd.hset(k, 'attr:grox.flash', 'SENTINELA')

            def a = repo.find(s.id)
            // a sentinela não desserializa: repõe os valores em memória para simular a leitura da requisição
            a.attributeMap.put('grox.security.authentication', new Authentication(principal: new GroxUser(username: 'ana'), method: 'form'))
            a.attributeMap.put('grox.flash', new FlashScope())
            a.attributeMap.put('carrinho', ['a'])
            a.getAttribute('grox.security.authentication')
            ((FlashScope) a.getAttribute('grox.flash')).advance()   // flash vazio: nada muda
            ((List) a.getAttribute('carrinho')) << 'b'               // lista mutável: é regravada
            repo.save(a)
            assert cmd.hget(k, 'attr:grox.security.authentication') == 'SENTINELA'
            assert cmd.hget(k, 'attr:grox.flash') == 'SENTINELA'
            assert repo.find(s.id).attributeMap['carrinho'] == ['a', 'b']

            // flash que recebeu valor é gravado
            def b = repo.find(s.id)
            b.attributeMap.put('grox.flash', new FlashScope())
            ((FlashScope) b.getAttribute('grox.flash')).put('message', 'salvo')
            repo.save(b)
            assert ((FlashScope) repo.find(s.id).attributeMap['grox.flash']).get('message') == 'salvo'

            // requisição A carregou a sessão; B fez logout; A termina e não recria a sessão
            def lenta = repo.find(s.id)
            lenta.getAttribute('carrinho')
            lenta.setAttribute('outro', 'x')
            repo.delete(s.id)
            repo.save(lenta)
            assert cmd.exists(k) == 0L && repo.find(s.id) == null
        } finally {
            raw.shutdown()
        }
    }

    @Test
    void 'desserializacao so aceita classes permitidas'() {
        def serializer = new org.grox.session.SessionSerializer(RedisSessionTest.classLoader, ['sample'])
        def ok = [lista: [1, 'a', java.time.LocalDate.of(2026, 9, 30)], auth: new Authentication(principal: new GroxUser(username: 'ana'), method: 'form'),
                  texto: "oi ${1 + 1}", numeros: [1, 2] as int[]]
        assert serializer.deserialize(serializer.serialize(ok)).texto.toString() == 'oi 2'
        // classe fora da lista (aqui, do runtime do Groovy: a família de onde vêm os gadgets)
        def gadget = serializer.serialize(new Tuple2('a', 'b'))
        org.junit.jupiter.api.Assertions.assertThrows(InvalidClassException) { serializer.deserialize(gadget) }
        assert serializer.isAllowed('sample.Carrinho') && !serializer.isAllowed('samplex.Carrinho')
        assert !serializer.isAllowed('java.lang.reflect.Proxy') && !serializer.isAllowed('org.apache.commons.collections.functors.InvokerTransformer')
        assert new org.grox.session.SessionSerializer(RedisSessionTest.classLoader, ['groovy.lang']).deserialize(gadget) == new Tuple2('a', 'b')
    }
}
