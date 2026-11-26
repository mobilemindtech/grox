package org.springframework.security.core.userdetails

/**
 * Compatibilidade com o {@code UserDetailsService} do Spring Security. Um bean
 * {@code userDetailsService} da aplicação que implemente esta interface é adaptado pelo Grox
 * ({@code org.grox.security.SpringUserDetailsAdapter}).
 */
interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException
}
