package org.springframework.security.authentication


/** Compatibilidade com o {@code CredentialsExpiredException} do Spring Security (motivo da falha de login, em {@code session[WebAttributes.AUTHENTICATION_EXCEPTION]}). */
class CredentialsExpiredException extends AccountStatusException {
    CredentialsExpiredException(String msg) { super(msg) }
}
