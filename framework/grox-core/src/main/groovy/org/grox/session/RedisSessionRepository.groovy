package org.grox.session

import groovy.util.logging.Slf4j
import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.grox.http.GroxSession

import java.nio.ByteBuffer

/**
 * Sessões no Redis (como o Spring Session): hash {@code <prefixo><id>} com
 * {@code meta:*} e {@code attr:<nome>} (serialização Java) e TTL igual ao timeout.
 * Grava apenas atributos alterados, removidos e lidos com valor mutável.
 */
@Slf4j
class RedisSessionRepository implements SessionRepository {

    static final Set<Class> IMMUTABLE = [String, Integer, Long, Short, Byte, Double, Float, Boolean, Character,
                                         BigDecimal, BigInteger, UUID, Locale] as Set

    final String cookieName
    final int timeoutSeconds
    final String keyPrefix
    private final RedisClient client
    private final StatefulRedisConnection<String, byte[]> connection
    private final RedisCommands<String, byte[]> redis
    private final SessionSerializer serializer

    /** Classloader das classes dos atributos (a geração atual da app, na recarga em desenvolvimento). */
    void setClassLoader(ClassLoader loader) {
        serializer.loader = loader
    }

    RedisSessionRepository(String url, String keyPrefix, String cookieName, int timeoutSeconds, ClassLoader loader,
                           List<String> allowedPackages = []) {
        this.cookieName = cookieName
        this.timeoutSeconds = timeoutSeconds
        this.keyPrefix = keyPrefix
        this.serializer = new SessionSerializer(loader, allowedPackages)
        this.client = RedisClient.create(url)
        this.connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
        this.redis = connection.sync()
        log.info("Sessões no Redis: ${url} (prefixo ${keyPrefix})")
    }

    private String key(String id) {
        keyPrefix + id
    }

    @Override
    /** SCAN das chaves {@code <prefixo>*} (custo proporcional ao número de sessões: só para telas administrativas). */
    @Override
    List<GroxSession> listActive() {
        List<GroxSession> result = []
        def args = io.lettuce.core.ScanArgs.Builder.matches(keyPrefix + '*').limit(500)
        def cursor = redis.scan(args)
        while (true) {
            cursor.keys.each { String k -> GroxSession s = find(k.substring(keyPrefix.length())); if (s) result << s }
            if (cursor.finished) break
            cursor = redis.scan(cursor, args)
        }
        result
    }

    GroxSession find(String id) {
        if (!id || id.length() > 64) return null
        Map<String, byte[]> hash = redis.hgetall(key(id))
        if (!hash) return null
        def s = new GroxSession(id, timeoutSeconds, this)
        s.creationTime = toLong(hash['meta:created'])
        s.lastAccessedTime = System.currentTimeMillis()
        hash.each { String field, byte[] value ->
            if (!field.startsWith('attr:')) return
            try {
                s.attributeMap.put(field.substring(5), serializer.deserialize(value))
            } catch (Exception e) {
                log.warn("Atributo de sessão ignorado (${field}): ${e}")
            }
        }
        s.markClean()
        s
    }

    @Override
    GroxSession create() {
        new GroxSession(SessionIds.next(), timeoutSeconds, this)
    }

    /**
     * Grava numa ida só e de forma atômica: HSET dos campos, HDEL dos removidos e EXPIRE.
     * Sessão existente só é gravada se a chave ainda existir: um logout (ou a expiração) durante
     * uma requisição concorrente não é desfeito por ela.
     * ARGV: 1 se nova, TTL, número de pares campo/valor, os pares, os campos a remover.
     */
    static final String SAVE_SCRIPT = '''
        if ARGV[1] == '0' and redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
        local n = tonumber(ARGV[3])
        if n > 0 then redis.call('HSET', KEYS[1], unpack(ARGV, 4, 3 + 2 * n)) end
        if #ARGV > 3 + 2 * n then redis.call('HDEL', KEYS[1], unpack(ARGV, 4 + 2 * n, #ARGV)) end
        redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
        return 1'''

    /** Atributo lido que precisa ser regravado (o valor pode ter sido alterado sem setAttribute). */
    static boolean rewriteAccessed(Object v) {
        if (v == null || IMMUTABLE.contains(v.getClass())) return false
        v instanceof SessionValue ? ((SessionValue) v).modified : true
    }

    @Override
    void save(GroxSession s) {
        if (s.invalidated) return
        Map<String, byte[]> put = ['meta:lastAccessed': toBytes(System.currentTimeMillis())]
        Collection<String> names
        if (s.isNew()) {
            put['meta:created'] = toBytes(s.creationTime)
            names = s.attributeMap.keySet()
        } else {
            names = (s.changed + s.accessed.findAll { rewriteAccessed(s.attributeMap[it]) }) as Set
        }
        names.each { String n ->
            Object v = s.attributeMap[n]
            if (v == null) return
            if (!(v instanceof Serializable)) throw new NotSerializableException("atributo de sessão '${n}' (${v.getClass().name}) não é Serializable")
            put['attr:' + n] = serializer.serialize(v)
        }
        List<byte[]> args = [bytes(s.isNew() ? '1' : '0'), bytes(timeoutSeconds.toString()), bytes(put.size().toString())]
        put.each { String f, byte[] v -> args << bytes(f) << v }
        if (!s.isNew()) s.removed.each { args << bytes('attr:' + it) }
        Long written = (Long) redis.eval(SAVE_SCRIPT, ScriptOutputType.INTEGER, [key(s.id)] as String[], args.toArray(new byte[0][]))
        if (written == 0L) log.debug("Sessão ${s.id} não existe mais no Redis (logout ou expiração): não recriada")
        s.markClean()
    }

    private static byte[] bytes(String s) {
        s.getBytes('UTF-8')
    }

    @Override
    void delete(String id) {
        redis.del(key(id))
    }

    @Override
    void changeId(GroxSession s) {
        String old = s.id
        s.id = SessionIds.next()
        if (!s.isNew()) redis.rename(key(old), key(s.id))
    }

    private static byte[] toBytes(long v) {
        ByteBuffer.allocate(8).putLong(v).array()
    }

    private static long toLong(byte[] b) {
        b ? ByteBuffer.wrap(b).long : System.currentTimeMillis()
    }

    boolean ping() {
        redis.ping() == 'PONG'
    }

    @Override
    void close() {
        connection?.close()
        client?.shutdown()
    }
}
