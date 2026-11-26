package org.grails.plugins.console

import org.grox.plugins.console.WebConsole
import org.grox.web.RequestContext

/**
 * Bean {@code consoleService} do plugin web-console: {@code eval(codigo, autoImportDomains, request)} executa
 * Groovy com os mesmos bindings do console ({@code ctx}, services, {@code grailsApplication}, {@code out}...).
 * Executa código arbitrário: só para scripts confiáveis.
 */
class ConsoleService {
    WebConsole console

    Evaluation eval(String code, boolean autoImportDomains = true, Object request = null) {
        Map r = console.execute(code, false, RequestContext.currentOrNull())
        new Evaluation(result: r.result, output: r.output ?: '', exception: r.error ? new RuntimeException((String) r.error) : null,
                totalTime: (r.durationMs ?: 0) as long)
    }

    static class Evaluation {
        Object result
        String output
        Throwable exception
        long totalTime
    }
}
