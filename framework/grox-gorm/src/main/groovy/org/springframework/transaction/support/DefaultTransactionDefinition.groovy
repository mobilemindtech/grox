package org.springframework.transaction.support

import org.springframework.transaction.TransactionDefinition

/** Compatibilidade com o {@code DefaultTransactionDefinition} do Spring. */
class DefaultTransactionDefinition implements TransactionDefinition {
    int propagationBehavior = PROPAGATION_REQUIRED
    int isolationLevel = ISOLATION_DEFAULT
    int timeout = TIMEOUT_DEFAULT
    boolean readOnly
    String name

    DefaultTransactionDefinition() {}

    DefaultTransactionDefinition(int propagationBehavior) { this.propagationBehavior = propagationBehavior }

    @Override int getPropagationBehavior() { propagationBehavior }
    @Override int getIsolationLevel() { isolationLevel }
    @Override int getTimeout() { timeout }
    @Override boolean isReadOnly() { readOnly }
    @Override String getName() { name }
}
