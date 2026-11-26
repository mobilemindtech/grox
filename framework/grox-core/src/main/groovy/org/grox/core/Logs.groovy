package org.grox.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Loggers por classe em cache (a propriedade {@code log} dos artefatos é lida com frequência). {@link ClassValue}
 * em vez de um mapa estático: o logger fica na própria classe, e a recarga não prende as gerações antigas.
 */
class Logs {
    private static final ClassValue<Logger> CACHE = new ClassValue<Logger>() {
        @Override
        protected Logger computeValue(Class<?> type) {
            LoggerFactory.getLogger(type)
        }
    }

    static Logger of(Class<?> type) {
        CACHE.get(type)
    }
}
