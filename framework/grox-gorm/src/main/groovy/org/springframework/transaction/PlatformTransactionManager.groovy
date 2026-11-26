package org.springframework.transaction

/** Compatibilidade com o {@code PlatformTransactionManager} do Spring (bean {@code transactionManager}). */
interface PlatformTransactionManager {
    TransactionStatus getTransaction(TransactionDefinition definition)
    void commit(TransactionStatus status)
    void rollback(TransactionStatus status)
}
