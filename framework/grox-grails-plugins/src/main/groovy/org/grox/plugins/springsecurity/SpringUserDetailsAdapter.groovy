package org.grox.plugins.springsecurity

import org.grox.data.Persistence
import org.grox.security.GroxUser
import org.grox.security.UserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Adapta um {@code userDetailsService} no estilo Spring Security (que devolve {@code UserDetails},
 * como o {@code GrailsUser}) para a fonte de usuários do Grox. {@code UsernameNotFoundException}
 * vira "usuário inexistente". Id e tenant vêm das propriedades {@code id}/{@code tenantId} do
 * {@code UserDetails}, se existirem.
 */
class SpringUserDetailsAdapter implements UserDetailsService {

    final org.springframework.security.core.userdetails.UserDetailsService target

    SpringUserDetailsAdapter(org.springframework.security.core.userdetails.UserDetailsService target) {
        this.target = target
    }

    @Override
    GroxUser loadUserByUsername(String username) {
        UserDetails details
        try {
            details = Persistence.withSessionIfAvailable { target.loadUserByUsername(username) }
        } catch (UsernameNotFoundException ignored) {
            return null
        }
        details == null ? null : toGroxUser(details)
    }

    static GroxUser toGroxUser(UserDetails d) {
        def u = new GroxUser(username: d.username, password: d.password, enabled: d.enabled,
                accountExpired: !d.accountNonExpired, accountLocked: !d.accountNonLocked, passwordExpired: !d.credentialsNonExpired,
                id: d.hasProperty('id') ? (Serializable) d['id'] : null,
                tenantId: d.hasProperty('tenantId') ? (Serializable) d['tenantId'] : null)
        u.roles = (d.authorities ?: []).collect { Object a ->
            a instanceof org.springframework.security.core.GrantedAuthority ? ((org.springframework.security.core.GrantedAuthority) a).authority : a.toString()
        }
        u
    }
}
