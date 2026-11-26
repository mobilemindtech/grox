package org.grox.security

/**
 * Usuário autenticado (equivalente ao GrailsUser/UserDetails). A senha não é serializada
 * (a autenticação vai para a sessão, possivelmente no Redis).
 */
class GroxUser implements Serializable {

    private static final long serialVersionUID = 1L

    String username
    transient String password
    Set<GrantedAuthority> authorities = new LinkedHashSet<>()
    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired
    /** Id da domain class do usuário (quando vem do GORM). */
    Serializable id
    /** Tenant do usuário (multi-tenancy). */
    Serializable tenantId

    void setRoles(Collection<String> roles) {
        authorities = roles.collect { new GrantedAuthority(it.toString()) } as LinkedHashSet
    }

    Set<String> getRoles() {
        authorities*.authority as LinkedHashSet
    }

    boolean hasAuthority(String authority) {
        authorities.any { it.authority == authority }
    }

    GroxUser withoutPassword() {
        new GroxUser(username: username, authorities: authorities, enabled: enabled, accountExpired: accountExpired,
                accountLocked: accountLocked, passwordExpired: passwordExpired, id: id, tenantId: tenantId)
    }

    @Override
    String toString() {
        "GroxUser(${username}, ${roles})"
    }
}
