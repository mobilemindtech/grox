package org.grox.data

/** Passado a {@code withTransaction { status -> }}; é também o {@code TransactionStatus} do Spring. */
class TransactionStatus implements org.springframework.transaction.TransactionStatus {
    boolean newTransaction
    boolean completed
    /** Sessão da transação aberta pelo {@link GroxTransactionManager} (uso programático). */
    Persistence.SessionHolder holder
    /** Transação externa, quando esta apenas participa dela. */
    TransactionStatus parent
    /** A própria transação pediu rollback ({@code setRollbackOnly()} na externa): o commit vira rollback, sem exceção. */
    private boolean rollback
    /** Uma participante falhou ou pediu rollback: o commit da externa vira rollback e lança {@code UnexpectedRollbackException}. */
    private boolean globalRollback

    /** Numa participante, marca a transação inteira, como no Spring. */
    void setRollbackOnly() {
        if (parent != null) parent.markGlobalRollback() else rollback = true
    }

    void markGlobalRollback() {
        if (parent != null) parent.markGlobalRollback() else globalRollback = true
    }

    boolean isRollbackOnly() {
        parent != null ? parent.rollbackOnly : (rollback || globalRollback)
    }

    boolean isGlobalRollbackOnly() {
        parent != null ? parent.globalRollbackOnly : globalRollback
    }

    static final String UNEXPECTED_ROLLBACK = 'Transação desfeita: uma transação participante falhou ou foi marcada como rollback-only'
}
