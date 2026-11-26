package org.springframework.security.authentication.dao

import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Compatibilidade com o {@code DaoAuthenticationProvider}: declarado no {@code doWithSpring}/resources.groovy
 * só como configuração. O Grox usa o {@code passwordEncoder} e o {@code userDetailsPasswordService} dele.
 */
class DaoAuthenticationProvider {
    UserDetailsService userDetailsService
    PasswordEncoder passwordEncoder
    UserDetailsPasswordService userDetailsPasswordService

    DaoAuthenticationProvider() {}

    DaoAuthenticationProvider(UserDetailsService userDetailsService) { this.userDetailsService = userDetailsService }
}
