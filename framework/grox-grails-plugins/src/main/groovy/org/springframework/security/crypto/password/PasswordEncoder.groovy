package org.springframework.security.crypto.password

/**
 * Compatibilidade com o {@code PasswordEncoder} do Spring Security. Um bean {@code passwordEncoder}
 * da aplicação que o implemente passa a ser usado no login e no {@code springSecurityService.encodePassword}.
 */
interface PasswordEncoder {
    String encode(CharSequence rawPassword)

    boolean matches(CharSequence rawPassword, String encodedPassword)

    /** true se a senha gravada deve ser recodificada (ex.: hash antigo); o login então a regrava. */
    default boolean upgradeEncoding(String encodedPassword) { false }
}
