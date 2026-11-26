package org.springframework.security.authentication


/** Compatibilidade com o {@code DisabledException} do Spring Security (motivo da falha de login, em {@code session[WebAttributes.AUTHENTICATION_EXCEPTION]}). */
class DisabledException extends AccountStatusException {
    DisabledException(String msg) { super(msg) }
}
