package org.springframework.transaction

/** Compatibilidade com o {@code TransactionException} do Spring. */
class TransactionException extends RuntimeException {
    TransactionException(String message) { super(message) }
}
