package org.springframework.security.core

/** Falha de autenticação (Spring Security); as subclasses dizem o motivo. */
class AuthenticationException extends RuntimeException {
    AuthenticationException(String msg) { super(msg) }
}
