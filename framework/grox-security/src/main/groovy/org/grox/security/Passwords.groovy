package org.grox.security

import at.favre.lib.crypto.bcrypt.BCrypt

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Senhas no formato do {@code DelegatingPasswordEncoder} do spring-security-core
 * ({@code grails.plugin.springsecurity.password.*}), para o Grails e o Grox lerem o mesmo banco:
 * <ul>
 *   <li>{@code bcrypt} (padrão): grava {@code {bcrypt}$2a$...}; lê também {@code $2a$...} sem prefixo;</li>
 *   <li>{@code SHA-256}, {@code SHA-1}, {@code SHA-512}, {@code MD5}: grava {@code {SHA-256}hash}, sem sal,
 *       como o Grails 8 grava (conferido no banco do app B: 73 caracteres), com o hash repetido
 *       {@code password.hash.iterations} vezes, em hex ou em base64 ({@code encodeHashAsBase64}).
 *       Lê também {@code {SHA-256}{sal}hash} (formato do {@code MessageDigestPasswordEncoder} do Spring,
 *       hash de {@code senha + "{sal}"}) e o hash puro sem prefixo, se o algoritmo configurado for esse;</li>
 *   <li>{@code {noop}texto} (desenvolvimento).</li>
 * </ul>
 */
class Passwords {

    static final Set<String> DIGESTS = ['SHA-256', 'SHA-1', 'SHA-512', 'MD5'] as Set

    /** {@code password.bcrypt.logrounds}. */
    static int cost = 10
    /** {@code password.algorithm}. */
    static String algorithm = 'bcrypt'
    /** {@code password.hash.iterations} (algoritmos de digest). */
    static int iterations = 10000
    /** {@code password.encodeHashAsBase64}. */
    static boolean encodeHashAsBase64 = false

    private static volatile String dummy
    private static volatile int dummyCost
    private static volatile String customDummy
    private static volatile Object customDummyOf

    /** Configuração do spring-security-core (chamado na subida). */
    static void configure(String algorithm, int iterations, boolean base64, int cost) {
        String a = algorithm ?: 'bcrypt'
        if (a != 'bcrypt' && !(a in DIGESTS)) throw new IllegalArgumentException("password.algorithm não suportado: ${a} (use bcrypt, ${DIGESTS.join(', ')})")
        Passwords.algorithm = a
        Passwords.iterations = Math.max(1, iterations)
        Passwords.encodeHashAsBase64 = base64
        Passwords.cost = cost
    }

    /**
     * Bean {@code passwordEncoder} da aplicação (Spring Security), se houver: substitui o formato
     * de {@code password.algorithm} na gravação e na conferência.
     */
    static volatile PasswordCodec custom
    /** Bean {@code userDetailsPasswordService}: regrava a senha quando {@code custom.upgradeEncoding} pede. */
    static volatile PasswordUpdater passwordService

    static String encode(String raw) {
        if (custom != null) return custom.encode(raw)
        if (algorithm in DIGESTS) return '{' + algorithm + '}' + digest(algorithm, raw)
        '{bcrypt}' + BCrypt.withDefaults().hashToString(cost, raw.toCharArray())
    }

    /** Tamanho do hash em hex e em base64, por algoritmo de digest. */
    private static final Map<String, List<Integer>> HASH_LENGTHS = ['MD5': [32, 24], 'SHA-1': [40, 28], 'SHA-256': [64, 44], 'SHA-512': [128, 88]]

    /**
     * Já é um hash (bcrypt, ou digest com o tamanho certo): não codificar de novo. {@code {noop}texto} e prefixos
     * seguidos de qualquer coisa não contam, porque o valor pode vir do usuário: com eles, uma senha como
     * {@code {noop}segredo} seria gravada em texto puro. Para gravar, prefira codificar sempre que a senha mudar.
     */
    static boolean isEncoded(String value) {
        if (value == null) return false
        if (value ==~ /^(\{bcrypt\})?\$2[aby]\$\d\d\$.{53}$/) return true
        String id = prefixId(value)
        if (id == null || !(id in DIGESTS)) return false
        String hash = value.substring(id.length() + 2)
        if (hash.startsWith('{')) {
            int end = hash.indexOf('}')
            if (end < 0) return false
            hash = hash.substring(end + 1)   // {SHA-256}{sal}hash
        }
        List<Integer> sizes = HASH_LENGTHS[id]
        (hash.length() == sizes[0] && hash ==~ /[0-9a-f]+/) || (hash.length() == sizes[1] && hash ==~ /[A-Za-z0-9+\/]+=*/)
    }

    static boolean matches(String raw, String encoded) {
        if (raw == null || encoded == null) return dummyVerify()
        if (custom != null) {
            try {
                return custom.matches(raw, encoded)
            } catch (IllegalArgumentException ignored) {
                return dummyVerify()   // prefixo sem encoder no DelegatingPasswordEncoder: mesmo tempo que uma senha errada
            }
        }
        if (encoded.startsWith('{noop}')) {
            return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), encoded.substring(6).getBytes(StandardCharsets.UTF_8))
        }
        String id = prefixId(encoded)
        if (id != null && id in DIGESTS) return digestMatches(id, raw, encoded.substring(id.length() + 2))
        String hash = encoded.startsWith('{bcrypt}') ? encoded.substring(8) : encoded
        if (hash ==~ /^\$2[aby]\$\d\d\$.{53}$/) return BCrypt.verifyer().verify(raw.toCharArray(), hash.toCharArray()).verified
        // sem prefixo: hash de digest gravado antes do DelegatingPasswordEncoder
        if (algorithm in DIGESTS && id == null) return digestMatches(algorithm, raw, encoded)
        dummyVerify()
    }

    /** {@code {SHA-256}...} → {@code SHA-256}; null se não houver prefixo de algoritmo. */
    private static String prefixId(String value) {
        if (!value.startsWith('{')) return null
        int end = value.indexOf('}')
        if (end < 0) return null
        String id = value.substring(1, end)
        id in DIGESTS || id in ['bcrypt', 'noop'] ? id : null
    }

    /** {@code {sal}hash} ou só {@code hash}, como o {@code MessageDigestPasswordEncoder} do Spring. */
    private static boolean digestMatches(String alg, String raw, String stored) {
        String salt = stored.startsWith('{') && stored.indexOf('}') > 0 ? stored.substring(0, stored.indexOf('}') + 1) : ''
        String expected = salt + digest(alg, raw + salt)
        MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8))
    }

    private static String digest(String alg, String value) {
        MessageDigest md = MessageDigest.getInstance(alg)
        byte[] d = md.digest(value.getBytes(StandardCharsets.UTF_8))
        for (int i = 1; i < iterations; i++) d = md.digest(d)
        encodeHashAsBase64 ? Base64.encoder.encodeToString(d) : d.encodeHex().toString()
    }

    /** Hash com o mesmo custo dos reais: usuário inexistente leva o mesmo tempo (sem enumeração por tempo). */
    private static boolean dummyVerify() {
        def encoder = custom
        if (encoder != null) {
            // encoder da app (pode ser um digest rápido): o dummy usa o mesmo, com o hash calculado uma vez
            String h = customDummy
            if (h == null || !customDummyOf.is(encoder)) {
                h = encoder.encode('dummy')
                customDummy = h
                customDummyOf = encoder
            }
            try {
                encoder.matches('x', h)
            } catch (IllegalArgumentException ignored) {
            }
            return false
        }
        if (algorithm in DIGESTS) {
            digest(algorithm, 'x{0000000000000000}')
            return false
        }
        if (dummy == null || dummyCost != cost) {
            dummyCost = cost
            dummy = BCrypt.withDefaults().hashToString(cost, 'dummy'.toCharArray())
        }
        BCrypt.verifyer().verify('x'.toCharArray(), dummy.toCharArray())
        false
    }
}
