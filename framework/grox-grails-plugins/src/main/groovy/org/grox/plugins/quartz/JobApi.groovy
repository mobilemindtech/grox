package org.grox.plugins.quartz

import org.grox.core.ClassUtils
import org.grox.core.Logs
import org.slf4j.Logger

/** Aplicado automaticamente (AST) às classes {@code *Job} em {@code grails-app/jobs}. */
trait JobApi {
    Logger getLog() { Logs.of(this.getClass()) }

    /**
     * Como o {@code TriggersConfigBuilder} do plugin quartz: avalia {@code static triggers} e devolve o último
     * trigger declarado como {@code Expando} com {@code triggerAttributes} ({@code name}, {@code cronExpression}
     * ou {@code repeatInterval}...); {@code null} se o job não tiver triggers. Usado por telas de status dos jobs.
     */
    Expando triggers() {
        Object dsl = ClassUtils.staticProperty(this.getClass(), 'triggers')
        if (!(dsl instanceof Closure)) return null
        def builder = new TriggerCollector()
        Closure c = (Closure) ((Closure) dsl).clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = builder
        c.call()
        builder.created ? builder.created.last() : null
    }
}
