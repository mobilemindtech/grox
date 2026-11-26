package org.springframework.transaction.interceptor

import org.grox.data.Persistence
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.TransactionStatus

/** {@code TransactionAspectSupport.currentTransactionStatus()}: status da transação corrente do Grox. */
class TransactionAspectSupport {

    static TransactionStatus currentTransactionStatus() {
        def holder = Persistence.currentHolder()
        if (holder?.status == null) throw new NoTransactionException('Nenhuma transação em andamento')
        holder.status
    }

    /** No Spring só vale dentro de um método transacional; aqui, na transação corrente. */
    static void setRollbackOnly() {
        currentTransactionStatus().setRollbackOnly()
    }
}
