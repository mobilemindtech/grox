package sample

import grails.boot.config.GrailsAutoConfiguration
import org.grox.GroxApp

class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GroxApp.run(Application, args)
    }

    /** Beans da aplicação, como no Grails: depois dos plugins e antes do resources.groovy. */
    @Override
    Closure doWithSpring() { { ->
        primeira(Etiqueta) {
            texto = 'a'
            proxima = ref('segunda')     // declarada abaixo: resolvida no fim do bloco
        }
        segunda(Etiqueta) { bean ->
            bean.scope = 'singleton'
            texto = 'b'
        }
        terceira(Etiqueta, segunda.texto + 'c')   // nome de bean já definido, sem ref(), como no DSL do Spring
        ultimoToken(UltimoTokenListener)          // ApplicationListener<RestTokenCreationEvent>
    } }
}
