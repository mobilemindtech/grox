package org.grox.data

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus as SpringStatus

/**
 * Bean {@code transactionManager}: uso programático no estilo Spring
 * ({@code getTransaction(def)} ... {@code commit(status)}/{@code rollback(status)}) sobre a sessão
 * corrente do Grox. Participa da transação em andamento ou abre uma (REQUIRED, SUPPORTS, MANDATORY).
 * REQUIRES_NEW e NOT_SUPPORTED precisariam trocar a sessão corrente entre chamadas: use
 * {@code withNewTransaction { }} ou {@code @Transactional(propagation = REQUIRES_NEW)} num service.
 */
class GroxTransactionManager implements PlatformTransactionManager {

    @Override
    SpringStatus getTransaction(TransactionDefinition definition) {
        int propagation = definition?.propagationBehavior ?: TransactionDefinition.PROPAGATION_REQUIRED
        if (propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW || propagation == TransactionDefinition.PROPAGATION_NOT_SUPPORTED
                || propagation == TransactionDefinition.PROPAGATION_NEVER) {
            throw new UnsupportedOperationException("transactionManager.getTransaction: propagação ${propagation} não suportada no uso programático; use withNewTransaction { }")
        }
        Persistence.SessionHolder holder = Persistence.currentHolder()
        if (holder == null) throw new IllegalStateException('transactionManager.getTransaction: nenhuma sessão ativa (use withSession { })')
        def tx = holder.session.transaction
        if (tx.active) return new TransactionStatus(parent: holder.status ?: new TransactionStatus(), holder: holder)
        if (propagation == TransactionDefinition.PROPAGATION_MANDATORY) {
            throw new org.springframework.transaction.TransactionException('Propagação MANDATORY sem transação em andamento')
        }
        if (propagation == TransactionDefinition.PROPAGATION_SUPPORTS) return new TransactionStatus(holder: holder)
        tx.begin()
        def status = new TransactionStatus(newTransaction: true, holder: holder)
        holder.status = status
        status
    }

    @Override
    void commit(SpringStatus s) {
        TransactionStatus status = (TransactionStatus) s
        if (status.newTransaction) {
            try {
                def tx = status.holder.session.transaction
                if (status.rollbackOnly) tx.rollback() else tx.commit()
            } finally {
                status.holder.status = null
            }
        }
        status.completed = true
    }

    @Override
    void rollback(SpringStatus s) {
        TransactionStatus status = (TransactionStatus) s
        if (status.newTransaction) {
            try {
                def tx = status.holder.session.transaction
                if (tx.active) tx.rollback()
            } finally {
                status.holder.status = null
            }
        } else {
            status.setRollbackOnly()
        }
        status.completed = true
    }
}
