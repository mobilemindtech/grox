package org.springframework.security.core.userdetails

/** Compatibilidade com o {@code User} do Spring Security. */
class User implements UserDetails {

    private static final long serialVersionUID = 1L

    final String username
    final String password
    final boolean enabled
    final boolean accountNonExpired
    final boolean credentialsNonExpired
    final boolean accountNonLocked
    final Collection<?> authorities

    User(String username, String password, Collection<?> authorities) {
        this(username, password, true, true, true, true, authorities)
    }

    User(String username, String password, boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired,
         boolean accountNonLocked, Collection<?> authorities) {
        this.username = username
        this.password = password
        this.enabled = enabled
        this.accountNonExpired = accountNonExpired
        this.credentialsNonExpired = credentialsNonExpired
        this.accountNonLocked = accountNonLocked
        this.authorities = authorities == null ? [] : new ArrayList(authorities)
    }

    @Override Collection<?> getAuthorities() { authorities }
    @Override String getPassword() { password }
    @Override String getUsername() { username }
    @Override boolean isAccountNonExpired() { accountNonExpired }
    @Override boolean isAccountNonLocked() { accountNonLocked }
    @Override boolean isCredentialsNonExpired() { credentialsNonExpired }
    @Override boolean isEnabled() { enabled }
}
