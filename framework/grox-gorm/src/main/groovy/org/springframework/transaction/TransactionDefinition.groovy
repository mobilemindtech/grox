package org.springframework.transaction

/** Compatibilidade com o {@code TransactionDefinition} do Spring (constantes e leitura). */
interface TransactionDefinition {
    int PROPAGATION_REQUIRED = 0
    int PROPAGATION_SUPPORTS = 1
    int PROPAGATION_MANDATORY = 2
    int PROPAGATION_REQUIRES_NEW = 3
    int PROPAGATION_NOT_SUPPORTED = 4
    int PROPAGATION_NEVER = 5
    int PROPAGATION_NESTED = 6
    int ISOLATION_DEFAULT = -1
    int TIMEOUT_DEFAULT = -1

    int getPropagationBehavior()
    int getIsolationLevel()
    int getTimeout()
    boolean isReadOnly()
    String getName()
}
