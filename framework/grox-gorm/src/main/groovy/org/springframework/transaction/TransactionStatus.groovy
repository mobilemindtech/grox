package org.springframework.transaction

/** Compatibilidade com o {@code TransactionStatus} do Spring: o status do Grox o implementa. */
interface TransactionStatus {
    boolean isNewTransaction()
    void setRollbackOnly()
    boolean isRollbackOnly()
    boolean isCompleted()
}
