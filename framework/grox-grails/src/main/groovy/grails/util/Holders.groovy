package grails.util

import grails.core.ApplicationContext
import grails.core.GrailsApplication
import groovy.transform.CompileStatic
import org.grox.core.Config

/** Compatibilidade: acesso estático à aplicação corrente (uma por JVM, como no Grails). */
@CompileStatic
class Holders {
    static volatile GrailsApplication grailsApplication

    static GrailsApplication getGrailsApplication() { grailsApplication }

    static Config getConfig() { grailsApplication?.config }

    static ApplicationContext getApplicationContext() { grailsApplication?.mainContext }

    static ApplicationContext findApplicationContext() { applicationContext }

    static PluginManager getPluginManager() { PluginManager.INSTANCE }

    /** Os plugins do Grox são internos; nenhum plugin Grails está "instalado". */
    static class PluginManager {
        static final PluginManager INSTANCE = new PluginManager()

        boolean hasGrailsPlugin(String name) { false }
    }
}
