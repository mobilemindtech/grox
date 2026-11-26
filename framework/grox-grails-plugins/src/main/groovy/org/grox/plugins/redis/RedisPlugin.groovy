package org.grox.plugins.redis

import grails.plugins.redis.RedisService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Plugin redis do Grails no Grox: com o jedis no classpath da aplicação, cria o bean {@code redisService} sobre um {@code JedisPool} ({@code host}, {@code port},
 * {@code timeout}, {@code password}, {@code database}, {@code useSSL}, {@code poolConfig}) ou um
 * {@code JedisSentinelPool} ({@code sentinels}, {@code masterName}); {@code connections} viram
 * {@code redisService<Nome>} e {@code redisService.withConnection('nome')}. Sem {@code grails.redis}, como o plugin
 * do Grails, usa {@code localhost:6379} (o app C configura {@code redis:} fora de {@code grails}, e o plugin o ignora).
 * O pool só conecta quando o Redis é usado.
 */
class RedisPlugin {

    private static final Logger log = LoggerFactory.getLogger(RedisPlugin)

    /** Beans {@code redisService*} e {@code redisPool*}; vazio sem o jedis no classpath. */
    static Map<String, Object> beans(Object config, ClassLoader loader) {
        boolean configured = config instanceof Map && config
        try {
            Class.forName('redis.clients.jedis.Jedis', false, loader)
        } catch (ClassNotFoundException ignored) {
            if (configured) log.warn('grails.redis configurado, mas o jedis não está no classpath: plugin redis desativado')
            return [:]
        }
        if (!configured) config = [:]
        Map<String, Object> beans = [:]
        RedisService main = service((Map) config)
        beans.redisPool = main.redisPool
        beans.redisService = main
        ((Map) config).connections?.each { Map c ->
            String name = c.name.toString()
            RedisService s = service(((Map) config) + c)
            main.connections[name] = s
            beans["redisPool${name.capitalize()}".toString()] = s.redisPool
            beans["redisService${name.capitalize()}".toString()] = s
        }
        log.info("Plugin redis: ${config.host ?: 'localhost'}:${config.port ?: 6379}${main.connections ? " + ${main.connections.keySet()}" : ''}")
        beans
    }

    private static RedisService service(Map c) {
        def poolConfig = new redis.clients.jedis.JedisPoolConfig()
        (c.poolConfig as Map)?.each { k, v ->
            if (poolConfig.hasProperty(k.toString())) poolConfig[k.toString()] = v
            else log.warn("grails.redis.poolConfig.${k}: propriedade desconhecida do JedisPoolConfig")
        }
        int timeout = (c.timeout ?: 2000) as int
        String password = c.password ?: null
        int database = (c.database ?: 0) as int
        boolean ssl = (c.useSSL ?: false) as boolean
        def pool
        if (c.sentinels && c.masterName) {
            Set<String> sentinels = (c.sentinels instanceof Collection ? c.sentinels : c.sentinels.toString().tokenize(',')).collect { it.toString().trim() } as Set
            pool = new redis.clients.jedis.JedisSentinelPool(c.masterName.toString(), sentinels, poolConfig, timeout, password, database)
        } else {
            String host = (c.host ?: 'localhost').toString()
            int port = (c.port ?: 6379) as int
            if (ssl) {
                // TLS com verificação do nome do host (sem ela, qualquer certificado válido para outro nome passa)
                def params = new javax.net.ssl.SSLParameters()
                params.endpointIdentificationAlgorithm = 'HTTPS'
                pool = new redis.clients.jedis.JedisPool(poolConfig, host, port, timeout, password, database, true,
                        (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault(), params, null)
            } else {
                pool = new redis.clients.jedis.JedisPool(poolConfig, host, port, timeout, password, database, false)
            }
        }
        new RedisService(redisPool: pool)
    }

    static void close(Map<String, Object> beans) {
        beans?.each { k, v -> if (k.startsWith('redisPool')) v?.close() }
    }
}
