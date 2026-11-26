package org.springframework.security.access

/** Lançada para negar acesso; o Grox responde 403 (ou redireciona para o login se anônimo). */
class AccessDeniedException extends RuntimeException {
    AccessDeniedException(String msg, Throwable cause = null) { super(msg, cause) }
}
