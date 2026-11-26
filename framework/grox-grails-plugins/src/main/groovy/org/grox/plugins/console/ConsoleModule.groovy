package org.grox.plugins.console

import groovy.util.logging.Slf4j
import org.grox.core.GroxModule
import org.grox.web.GrailsModule

/** Console web (plugin web-console do Grails) e bean {@code consoleService}. */
@Slf4j
class ConsoleModule extends GroxModule {

    int getOrder() { 700 }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        beans.consoleService = new org.grails.plugins.console.ConsoleService(console: new WebConsole(runtime))
    }

    /** {@code grails.plugin.console.enabled}: mesma chave do plugin web-console do Grails. */
    @Override
    void configureWeb() {
        def web = runtime.module(GrailsModule)?.webContext
        if (web == null) return
        if (!config.getProperty('grox.console.enabled', Boolean, config.getProperty('grails.plugin.console.enabled', Boolean, runtime.development))) return
        def console = new WebConsole(runtime)
        web.endpoints << console   // a mesma lista que a segurança usa
        if (!runtime.development) log.warn("Console web ativo fora de development (${console.path}): cuidado, executa código na aplicação")
        else log.info("Console web: ${console.path}")
    }
}
