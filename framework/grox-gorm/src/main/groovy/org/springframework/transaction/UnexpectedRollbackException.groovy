package org.springframework.transaction

/**
 * Compatibilidade com o {@code UnexpectedRollbackException} do Spring: o commit da transação externa virou rollback
 * porque uma transação participante falhou ou pediu rollback ({@code setRollbackOnly()}).
 */
class UnexpectedRollbackException extends TransactionException {
    UnexpectedRollbackException(String message) { super(message) }
}
