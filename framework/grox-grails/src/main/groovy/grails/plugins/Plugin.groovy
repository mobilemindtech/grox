package grails.plugins

import grails.core.ApplicationContext
import grails.core.GrailsApplication

/**
 * Descritor de plugin do Grails ({@code class MeuGrailsPlugin extends Plugin}), usado por bibliotecas
 * {@code org.grox.lib}. O Grox avalia o {@code doWithSpring()} (beans no DSL do resources.groovy) antes
 * dos beans da aplicação, e chama {@code doWithDynamicMethods()}, {@code doWithApplicationContext()}
 * e {@code onShutdown(event)}. Metadados ({@code title}, {@code grailsVersion}...) são ignorados.
 */
abstract class Plugin {
    GrailsApplication grailsApplication
    ApplicationContext applicationContext

    Object getConfig() { grailsApplication?.config }

    Closure doWithSpring() { null }

    void doWithDynamicMethods() {}

    void doWithApplicationContext() {}

    void onChange(Map<String, Object> event) {}

    void onConfigChange(Map<String, Object> event) {}

    void onShutdown(Map<String, Object> event) {}
}
