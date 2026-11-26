package org.springframework.transaction

/** Não há transação em andamento. */
class NoTransactionException extends TransactionException {
    NoTransactionException(String message) { super(message) }
}
