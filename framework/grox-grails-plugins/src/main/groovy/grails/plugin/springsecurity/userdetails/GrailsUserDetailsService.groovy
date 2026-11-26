package grails.plugin.springsecurity.userdetails

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/** Compatibilidade com o {@code GrailsUserDetailsService} do spring-security-core. */
interface GrailsUserDetailsService extends UserDetailsService {
    UserDetails loadUserByUsername(String username, boolean loadRoles) throws UsernameNotFoundException
}
