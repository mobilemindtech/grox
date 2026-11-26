package org.grox.dsl

import org.grox.GroxApp
import org.grox.core.GroxRuntime

/**
 * Base de um script de aplicação ({@code app.groovy}): o script declara a app com {@code grox { }} e, executado,
 * sobe o Grox. O pacote do script é o varrido (domain classes, services, rotas).
 * <pre>
 * package livraria
 * &#64;groovy.transform.BaseScript org.grox.dsl.GroxScript app
 *
 * grox {
 *     routes { get('/') { render { h1 'Olá' } } }
 * }
 * </pre>
 */
abstract class GroxScript extends Script {

    /** Ao subir, o módulo do {@code grox-dsl} roda o script de novo só para ler a declaração (sem subir outra vez). */
    static final ThreadLocal<ProjectSpec> CAPTURING = new ThreadLocal<>()

    GroxRuntime grox(Closure app) {
        ProjectSpec capturing = CAPTURING.get()
        if (capturing != null) {
            ProjectDsl.evaluate(app, capturing)
            return null
        }
        String[] args = binding.hasVariable('args') ? (String[]) binding.getVariable('args') : new String[0]
        GroxApp.run(getClass(), args)
    }
}
