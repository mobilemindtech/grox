package org.springframework.security.core

/** Compatibilidade com o {@code Authentication} do Spring Security: a autenticação do Grox o implementa. */
interface Authentication extends java.security.Principal, Serializable {
    Collection getAuthorities()

    Object getCredentials()

    Object getDetails()

    Object getPrincipal()

    boolean isAuthenticated()
}
