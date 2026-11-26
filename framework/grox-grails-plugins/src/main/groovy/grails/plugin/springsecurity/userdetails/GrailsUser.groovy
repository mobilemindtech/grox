package grails.plugin.springsecurity.userdetails

import org.springframework.security.core.userdetails.User

/** Compatibilidade com o {@code GrailsUser} do spring-security-core: {@code User} com o id da domain class. */
class GrailsUser extends User {

    private static final long serialVersionUID = 1L

    final Object id

    GrailsUser(String username, String password, boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired,
               boolean accountNonLocked, Collection<?> authorities, Object id) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities)
        this.id = id
    }
}
