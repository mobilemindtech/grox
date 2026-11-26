package org.springframework.security.core.authority

/** Compatibilidade: uma role ({@code new SimpleGrantedAuthority('ROLE_ADMIN')}). */
class SimpleGrantedAuthority extends org.grox.security.GrantedAuthority {

    private static final long serialVersionUID = 1L

    SimpleGrantedAuthority(String role) {
        super(role)
    }
}
