package grails.plugins.redis

import com.google.gson.Gson
import redis.clients.jedis.Jedis
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.exceptions.JedisConnectionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Bean {@code redisService} do plugin redis do Grails (sobre Jedis), configurado por
 * {@code grails.redis.*}: {@code withRedis { Jedis redis -> }}, {@code memoize}, {@code memoizeObject},
 * {@code memoizeHash}, {@code memoizeHashField}, {@code memoizeScore}, {@code withPipeline},
 * {@code withTransaction}, {@code deleteKeysWithPattern}; comandos do Jedis direto
 * ({@code redisService.del('k')}) e {@code redisService.chave = 'v'} / {@code redisService.chave}.
 */
class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService)

    static final int NO_EXPIRATION_TTL = -1
    static final int KEY_DOES_NOT_EXIST = -2

    /** {@code JedisPool} (ou {@code JedisSentinelPool}). */
    def redisPool
    /** Outras conexões ({@code grails.redis.connections}), por nome. */
    Map<String, RedisService> connections = [:]

    RedisService withConnection(String connectionName) {
        RedisService other = connections[connectionName]
        if (other == null) log.error("Conexão redis '{}' não encontrada; usando a padrão", connectionName)
        other ?: this
    }

    def withRedis(Closure closure) {
        Jedis redis = (Jedis) redisPool.resource
        try {
            closure(redis)
        } finally {
            redis.close()
        }
    }

    /** Como {@code withRedis}, mas com {@code null} (sem exceção) se o redis estiver fora do ar. */
    def withOptionalRedis(Closure closure) {
        Jedis redis = null
        try {
            redis = (Jedis) redisPool.resource
        } catch (JedisConnectionException ignored) {
            log.info('Redis inacessível; seguindo sem cache')
        }
        try {
            closure(redis)
        } catch (JedisConnectionException e) {
            log.error('Redis inacessível', e)
            null
        } finally {
            redis?.close()
        }
    }

    def withPipeline(Closure closure, Boolean returnAll = false) {
        withRedis { Jedis redis ->
            Pipeline pipeline = redis.pipelined()
            closure(pipeline)
            returnAll ? pipeline.syncAndReturnAll() : pipeline.sync()
        }
    }

    def withTransaction(Closure closure) {
        withRedis { Jedis redis ->
            Transaction transaction = redis.multi()
            try {
                closure(transaction)
            } catch (Exception e) {
                transaction.discard()
                throw e
            }
            transaction.exec()
        }
    }

    def memoize(String key, Integer expire, Closure closure) {
        memoize(key, [expire: expire], closure)
    }

    /** Valor em cache (String) ou o resultado do bloco, gravado com {@code expire} segundos. */
    def memoize(String key, Map options = [:], Closure closure) {
        def result = withOptionalRedis { Jedis redis -> redis?.get(key) }
        if (!result) {
            result = closure()
            if (result) withOptionalRedis { Jedis redis ->
                if (redis) {
                    if (options?.expire) redis.setex(key, options.expire as long, result as String)
                    else redis.set(key, result as String)
                }
            }
        }
        result
    }

    def memoizeHash(String key, Integer expire, Closure closure) {
        memoizeHash(key, [expire: expire], closure)
    }

    def memoizeHash(String key, Map options = [:], Closure closure) {
        def hash = withOptionalRedis { Jedis redis -> redis?.hgetAll(key) }
        if (!hash) {
            hash = closure()
            if (hash) withOptionalRedis { Jedis redis ->
                if (redis) {
                    redis.hmset(key, (Map<String, String>) hash.collectEntries { k, v -> [k.toString(), v?.toString()] })
                    if (options?.expire) redis.expire(key, options.expire as long)
                }
            }
        }
        hash
    }

    def memoizeHashField(String key, String field, Integer expire, Closure closure) {
        memoizeHashField(key, field, [expire: expire], closure)
    }

    def memoizeHashField(String key, String field, Map options = [:], Closure closure) {
        def result = withOptionalRedis { Jedis redis -> redis?.hget(key, field) }
        if (!result) {
            result = closure()
            if (result) withOptionalRedis { Jedis redis ->
                if (redis) {
                    redis.hset(key, field, result as String)
                    if (options?.expire && redis.ttl(key) == NO_EXPIRATION_TTL) redis.expire(key, options.expire as long)
                }
            }
        }
        result
    }

    def memoizeScore(String key, String member, Integer expire, Closure closure) {
        memoizeScore(key, member, [expire: expire], closure)
    }

    def memoizeScore(String key, String member, Map options = [:], Closure closure) {
        def score = withOptionalRedis { Jedis redis -> redis?.zscore(key, member) }
        if (!score) {
            score = closure()
            if (score) withOptionalRedis { Jedis redis ->
                if (redis) {
                    redis.zadd(key, score as double, member)
                    if (options?.expire && redis.ttl(key) == NO_EXPIRATION_TTL) redis.expire(key, options.expire as long)
                }
            }
        }
        score
    }

    def memoizeObject(Class clazz, String key, Integer expire, Closure closure) {
        memoizeObject(clazz, key, [expire: expire], closure)
    }

    /** Objeto em cache como JSON (Gson), como no plugin. */
    def memoizeObject(Class clazz, String key, Map options = [:], Closure closure) {
        Gson gson = new Gson()
        String json = memoize(key, options) { ->
            def original = closure()
            if (original == null && options.cacheNull == false) return null
            gson.toJson(original)
        }
        gson.fromJson(json, clazz)
    }

    /** Apaga as chaves do padrão (comando KEYS: caro em bases grandes). */
    void deleteKeysWithPattern(keyPattern) {
        withRedis { Jedis redis ->
            Set<String> keys = redis.keys(keyPattern.toString())
            if (keys) redis.del(keys as String[])
        }
    }

    def methodMissing(String name, args) {
        withRedis { Jedis redis -> redis.invokeMethod(name, args) }
    }

    void propertyMissing(String name, Object value) {
        withRedis { Jedis redis -> redis.set(name, value.toString()) }
    }

    Object propertyMissing(String name) {
        withRedis { Jedis redis -> redis.get(name) }
    }
}
