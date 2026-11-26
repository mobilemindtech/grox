package org.springframework.security.authentication

import org.springframework.security.core.AuthenticationException

/** Compatibilidade com o {@code AccountStatusException} do Spring Security (motivo da falha de login, em {@code session[WebAttributes.AUTHENTICATION_EXCEPTION]}). */
class AccountStatusException extends AuthenticationException {
    AccountStatusException(String msg) { super(msg) }
}
