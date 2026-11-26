package org.springframework.security.crypto.password

/**
 * Compatibilidade com o {@code DelegatingPasswordEncoder} do Spring Security: grava
 * {@code {id}hash} com o encoder de {@code idForEncode} e confere pelo prefixo. {@code upgradeEncoding}
 * é true quando o prefixo não é o de {@code idForEncode} (subclasses podem sobrescrever
 * {@code upgradeEncodingNonNull}, como no Spring Security 6.3+).
 */
class DelegatingPasswordEncoder implements PasswordEncoder {
    final String idForEncode
    final Map<String, PasswordEncoder> idToPasswordEncoder
    /** Usado quando o hash não tem prefixo conhecido ({@code setDefaultPasswordEncoderForMatches}). */
    PasswordEncoder defaultPasswordEncoderForMatches

    DelegatingPasswordEncoder(String idForEncode, Map<String, PasswordEncoder> idToPasswordEncoder) {
        if (!idToPasswordEncoder.containsKey(idForEncode)) throw new IllegalArgumentException("idForEncode ${idForEncode} não está no mapa")
        this.idForEncode = idForEncode
        this.idToPasswordEncoder = new LinkedHashMap<>(idToPasswordEncoder)
    }

    @Override
    String encode(CharSequence rawPassword) {
        '{' + idForEncode + '}' + idToPasswordEncoder[idForEncode].encode(rawPassword)
    }

    @Override
    boolean matches(CharSequence rawPassword, String prefixEncodedPassword) {
        if (rawPassword == null && prefixEncodedPassword == null) return true
        String id = extractId(prefixEncodedPassword)
        PasswordEncoder delegate = id == null ? null : idToPasswordEncoder[id]
        if (delegate == null) {
            if (defaultPasswordEncoderForMatches == null) {
                throw new IllegalArgumentException("Não há PasswordEncoder para o id \"${id}\"")
            }
            return defaultPasswordEncoderForMatches.matches(rawPassword, prefixEncodedPassword)
        }
        delegate.matches(rawPassword, extractEncoded(prefixEncodedPassword))
    }

    @Override
    boolean upgradeEncoding(String prefixEncodedPassword) {
        prefixEncodedPassword != null && upgradeEncodingNonNull(prefixEncodedPassword)
    }

    protected boolean upgradeEncodingNonNull(String prefixEncodedPassword) {
        String id = extractId(prefixEncodedPassword)
        if (!idForEncode.equalsIgnoreCase(id ?: '')) return true
        idToPasswordEncoder[idForEncode].upgradeEncoding(extractEncoded(prefixEncodedPassword))
    }

    private static String extractId(String encoded) {
        if (encoded == null || !encoded.startsWith('{')) return null
        int end = encoded.indexOf('}')
        end < 0 ? null : encoded.substring(1, end)
    }

    private static String extractEncoded(String encoded) {
        encoded.substring(encoded.indexOf('}') + 1)
    }
}
