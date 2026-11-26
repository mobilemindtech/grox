package org.springframework.security.web

/** Chaves de sessão/requisição do Spring Security. */
class WebAttributes {
    /** Na sessão depois de um login recusado: a exceção com o motivo ({@code DisabledException}...). */
    static final String AUTHENTICATION_EXCEPTION = 'SPRING_SECURITY_LAST_EXCEPTION'
    static final String ACCESS_DENIED_403 = 'SPRING_SECURITY_403_EXCEPTION'
}
