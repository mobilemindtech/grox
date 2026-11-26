package extra

import grails.plugins.Plugin

/** Descritor de plugin da biblioteca: beans no doWithSpring() e o ciclo de vida do Grails. */
class ExtraGrailsPlugin extends Plugin {

    static volatile boolean contextoPronto

    Closure doWithSpring() { { ->
        formatadorNota(FormatadorNota) { bean ->
            bean.autowire = true
            prefixo = 'Nota: '
        }
    } }

    void doWithApplicationContext() {
        contextoPronto = applicationContext.getBean('formatadorNota').notaService != null
    }
}
