package org.grox.security

import groovy.transform.EqualsAndHashCode

/** Uma permissão (role), como {@code ROLE_ADMIN}. */
@EqualsAndHashCode
class GrantedAuthority implements org.springframework.security.core.GrantedAuthority {

    private static final long serialVersionUID = 1L

    final String authority

    GrantedAuthority(String authority) {
        this.authority = authority
    }

    @Override
    String toString() {
        authority
    }
}
