package org.springframework.security.authentication

import org.springframework.security.core.AuthenticationException

/** Compatibilidade com o {@code BadCredentialsException} do Spring Security (motivo da falha de login, em {@code session[WebAttributes.AUTHENTICATION_EXCEPTION]}). */
class BadCredentialsException extends AuthenticationException {
    BadCredentialsException(String msg) { super(msg) }
}
