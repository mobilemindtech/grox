package org.springframework.security.core

/** Compatibilidade com o {@code GrantedAuthority} do Spring Security (implementado por {@code org.grox.security.GrantedAuthority}). */
interface GrantedAuthority extends Serializable {
    String getAuthority()
}
