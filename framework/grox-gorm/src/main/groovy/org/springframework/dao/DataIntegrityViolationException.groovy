package org.springframework.dao

/** Lançada pelo GORM do Grox quando o banco rejeita a operação por restrição de integridade. */
class DataIntegrityViolationException extends RuntimeException {
    DataIntegrityViolationException(String msg, Throwable cause = null) { super(msg, cause) }
}
