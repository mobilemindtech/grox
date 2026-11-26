package org.grox.data

/** Passado a {@code withTransaction { status -> }}; é também o {@code TransactionStatus} do Spring. */
class TransactionStatus implements org.springframework.transaction.TransactionStatus {
    boolean newTransaction
    boolean completed
    /** Sessão da transação aberta pelo {@link GroxTransactionManager} (uso programático). */
    Persistence.SessionHolder holder
    /** Transação externa, quando esta apenas participa dela. */
    TransactionStatus parent
    private boolean rollback

    void setRollbackOnly() {
        if (parent != null) parent.setRollbackOnly() else rollback = true
    }

    boolean isRollbackOnly() {
        parent != null ? parent.rollbackOnly : rollback
    }
}
