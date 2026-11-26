package grails.boot

import org.grox.GroxApp
import org.grox.core.GroxRuntime

/** Compatibilidade: {@code GrailsApp.run(Application, args)} sobe o Grox. */
class GrailsApp {
    static GroxRuntime run(Class<?> applicationClass, String... args) {
        GroxApp.run(applicationClass, args)
    }
}
