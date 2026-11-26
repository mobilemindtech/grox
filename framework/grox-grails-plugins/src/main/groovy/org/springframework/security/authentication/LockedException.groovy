package org.springframework.security.authentication


/** Compatibilidade com o {@code LockedException} do Spring Security (motivo da falha de login, em {@code session[WebAttributes.AUTHENTICATION_EXCEPTION]}). */
class LockedException extends AccountStatusException {
    LockedException(String msg) { super(msg) }
}
