package org.springframework.security.authentication


/** Compatibilidade com o {@code AccountExpiredException} do Spring Security (motivo da falha de login, em {@code session[WebAttributes.AUTHENTICATION_EXCEPTION]}). */
class AccountExpiredException extends AccountStatusException {
    AccountExpiredException(String msg) { super(msg) }
}
