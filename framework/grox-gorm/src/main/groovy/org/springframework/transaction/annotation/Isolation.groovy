package org.springframework.transaction.annotation

/** Compatibilidade com o {@code Isolation} do Spring (aceito, sem efeito no Grox). */
enum Isolation {
    DEFAULT, READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
}
