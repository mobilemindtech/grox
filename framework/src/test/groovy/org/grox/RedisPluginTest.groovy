package org.grox

import grails.plugins.redis.RedisService
import org.grox.plugins.redis.RedisPlugin
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis

/** Plugin redis do Grails (redisService) contra um redis-server local (pulado se não estiver instalado). */
class RedisPluginTest {

    static Process redis
    static Map<String, Object> beans

    static class Cliente {
        String nome
        int idade
    }

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(RedisSessionTest.redisInstalled(), 'redis-server não instalado')
        int port = RedisSessionTest.freePort()
        redis = RedisSessionTest.startRedis(port)
        beans = RedisPlugin.beans([host: 'localhost', port: port, timeout: 2000, poolConfig: [maxTotal: 4],
                                   connections: [[name: 'cache', database: 1]]], RedisPluginTest.classLoader)
    }

    @AfterAll
    static void stop() {
        RedisPlugin.close(beans)
        redis?.destroy()
    }

    @Test
    void 'redisService: withRedis, memoize, memoizeObject, propriedades e conexoes'() {
        RedisService s = (RedisService) beans.redisService
        assert s.withRedis { Jedis r -> r.set('a', '1'); r.get('a') } == '1'

        int chamadas = 0
        assert s.memoize('m', [expire: 60]) { chamadas++; 'valor' } == 'valor'
        assert s.memoize('m', 60) { chamadas++; 'outro' } == 'valor' && chamadas == 1
        assert s.withRedis { Jedis r -> r.ttl('m') } > 0

        Cliente c = (Cliente) s.memoizeObject(Cliente, 'cli', [expire: 60]) { new Cliente(nome: 'Ana', idade: 30) }
        assert c.nome == 'Ana' && c.idade == 30
        assert ((Cliente) s.memoizeObject(Cliente, 'cli', 60) { new Cliente(nome: 'outra') }).nome == 'Ana'

        s.chave = 'v'                       // propertyMissing → SET
        assert s.chave == 'v' && s.get('chave') == 'v'   // methodMissing → comando do Jedis
        s['outra'] = 'x'
        assert s.withRedis { Jedis r -> r.get('outra') } == 'x'

        s.deleteKeysWithPattern('cli*')
        assert !s.exists('cli')

        RedisService cache = s.withConnection('cache')
        assert cache.is(beans.redisServiceCache) && !cache.is(s)
        cache.set('a', 'db1')
        assert cache.get('a') == 'db1' && s.get('a') == '1'   // bases diferentes
        assert s.withConnection('nao-existe').is(s)
    }

    @Test
    void 'sem configuracao usa localhost como o plugin do Grails, e sem jedis fica desativado'() {
        def padrao = RedisPlugin.beans(null, RedisPluginTest.classLoader)   // pool preguiçoso: não conecta aqui
        try {
            assert padrao.redisService != null && padrao.redisPool != null
        } finally {
            RedisPlugin.close(padrao)
        }
        def semJedis = new URLClassLoader(new URL[0], (ClassLoader) null)
        assert RedisPlugin.beans(null, semJedis).isEmpty() && RedisPlugin.beans([host: 'x'], semJedis).isEmpty()
    }
}
