package org.grox.plugins.springsecurity

import groovy.transform.CompileStatic
import org.grox.security.Passwords
import org.springframework.security.crypto.password.PasswordEncoder

/** {@code springSecurityService.passwordEncoder}: usa o algoritmo configurado ({@link Passwords}); o nome é histórico. */
@CompileStatic
class BcryptPasswordEncoder implements PasswordEncoder {
    static final BcryptPasswordEncoder INSTANCE = new BcryptPasswordEncoder()

    @Override
    String encode(CharSequence rawPassword) { Passwords.encode(rawPassword.toString()) }

    @Override
    boolean matches(CharSequence rawPassword, String encodedPassword) { Passwords.matches(rawPassword.toString(), encodedPassword) }
}
