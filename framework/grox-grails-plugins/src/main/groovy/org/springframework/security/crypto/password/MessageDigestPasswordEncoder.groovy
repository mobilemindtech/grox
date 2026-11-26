package org.springframework.security.crypto.password

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Compatibilidade com o {@code MessageDigestPasswordEncoder} do Spring Security (legado): grava
 * {@code {sal}hash}, com o hash de {@code senha + "{sal}"} repetido {@code iterations} vezes, em hex
 * ou base64. Um hash sem {@code {sal}} é conferido sem sal.
 */
class MessageDigestPasswordEncoder implements PasswordEncoder {
    final String algorithm
    int iterations = 1
    boolean encodeHashAsBase64

    MessageDigestPasswordEncoder(String algorithm) {
        MessageDigest.getInstance(algorithm)   // falha já se o algoritmo não existir
        this.algorithm = algorithm
    }

    void setIterations(int iterations) {
        if (iterations <= 0) throw new IllegalArgumentException('iterations deve ser maior que zero')
        this.iterations = iterations
    }

    @Override
    String encode(CharSequence rawPassword) {
        byte[] bytes = new byte[8]
        new SecureRandom().nextBytes(bytes)
        String salt = '{' + bytes.encodeBase64().toString() + '}'
        salt + digest(salt, rawPassword)
    }

    @Override
    boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false
        String salt = extractSalt(encodedPassword)
        MessageDigest.isEqual((salt + digest(salt, rawPassword)).getBytes(StandardCharsets.UTF_8),
                encodedPassword.getBytes(StandardCharsets.UTF_8))
    }

    private String digest(String salt, CharSequence raw) {
        MessageDigest md = MessageDigest.getInstance(algorithm)
        byte[] hash = md.digest((raw.toString() + salt).getBytes(StandardCharsets.UTF_8))
        for (int i = 1; i < iterations; i++) hash = md.digest(hash)
        encodeHashAsBase64 ? hash.encodeBase64().toString() : hash.encodeHex().toString()
    }

    private static String extractSalt(String encoded) {
        if (!encoded.startsWith('{')) return ''
        int end = encoded.indexOf('}')
        end < 0 ? '' : encoded.substring(0, end + 1)
    }
}
