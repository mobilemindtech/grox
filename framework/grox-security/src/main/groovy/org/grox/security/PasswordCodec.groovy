package org.grox.security

/**
 * Codificação de senha da aplicação, no lugar do algoritmo configurado ({@link Passwords}). O
 * {@code passwordEncoder} do Spring Security é adaptado a esta interface pelo módulo do plugin.
 */
interface PasswordCodec {
    String encode(String raw)

    boolean matches(String raw, String encoded)

    /** true se a senha gravada deve ser recodificada (ex.: hash antigo); o login então a regrava. */
    boolean upgradeEncoding(String encoded)
}
