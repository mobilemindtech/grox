package org.grox.dsl

import org.grox.core.ClassUtils

/**
 * Lê a DSL de projeto da classe da aplicação: o closure estático {@code app} ({@code static app = { ... }}) ou,
 * num script {@link GroxScript}, o {@code grox { }} (o script roda de novo só para capturar a declaração, também
 * numa recarga em desenvolvimento).
 */
class ProjectDsl {

    /** A declaração da classe, ou null se ela não usa a DSL. */
    static ProjectSpec of(Class<?> applicationClass) {
        if (GroxScript.isAssignableFrom(applicationClass)) {
            def spec = new ProjectSpec()
            GroxScript.CAPTURING.set(spec)
            try {
                ((Script) applicationClass.getDeclaredConstructor().newInstance()).run()
            } finally {
                GroxScript.CAPTURING.remove()
            }
            return spec
        }
        Object app = ClassUtils.staticProperty(applicationClass, 'app')
        if (!(app instanceof Closure)) return null
        evaluate((Closure) app, new ProjectSpec())
    }

    static ProjectSpec evaluate(Closure app, ProjectSpec spec) {
        Closure c = (Closure) app.clone()
        c.delegate = spec
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        spec
    }
}
