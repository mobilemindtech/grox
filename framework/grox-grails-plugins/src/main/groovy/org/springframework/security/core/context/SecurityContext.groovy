package org.springframework.security.core.context

/** Compatibilidade com o {@code SecurityContext} do Spring Security. */
interface SecurityContext extends Serializable {
    Object getAuthentication()

    void setAuthentication(Object authentication)
}
