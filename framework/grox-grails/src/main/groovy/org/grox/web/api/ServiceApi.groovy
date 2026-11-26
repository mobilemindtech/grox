package org.grox.web.api

import org.slf4j.Logger
import org.grox.core.Logs

/** Aplicado automaticamente (AST) às classes {@code *Service} em {@code grails-app/services}. */
trait ServiceApi {
    Logger getLog() { Logs.of(this.getClass()) }
}
