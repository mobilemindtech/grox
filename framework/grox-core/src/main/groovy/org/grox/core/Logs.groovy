package org.grox.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

/** Loggers por classe em cache (a propriedade {@code log} dos artefatos é lida com frequência). */
class Logs {
    private static final Map<Class, Logger> CACHE = new ConcurrentHashMap<>()

    static Logger of(Class<?> type) {
        CACHE.computeIfAbsent(type) { LoggerFactory.getLogger(it) }
    }
}
