package org.springframework.security.crypto.bcrypt

import at.favre.lib.crypto.bcrypt.BCrypt
import org.springframework.security.crypto.password.PasswordEncoder

/** Compatibilidade com o {@code BCryptPasswordEncoder} do Spring Security ({@code $2a$}, custo 10 por padrão). */
class BCryptPasswordEncoder implements PasswordEncoder {
    final int strength

    BCryptPasswordEncoder() { this(10) }

    BCryptPasswordEncoder(int strength) { this.strength = strength < 4 ? 10 : strength }

    @Override
    String encode(CharSequence rawPassword) {
        BCrypt.withDefaults().hashToString(strength, rawPassword.toString().toCharArray())
    }

    @Override
    boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || !encodedPassword?.startsWith('$2')) return false
        BCrypt.verifyer().verify(rawPassword.toString().toCharArray(), encodedPassword).verified
    }

    @Override
    boolean upgradeEncoding(String encodedPassword) {
        if (!encodedPassword?.startsWith('$2')) return false
        int cost = encodedPassword.substring(4, 6) as int
        cost < strength
    }
}
