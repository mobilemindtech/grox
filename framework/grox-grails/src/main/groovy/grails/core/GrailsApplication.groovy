package grails.core

import org.grox.core.Config

/**
 * Compatibilidade: o bean {@code grailsApplication} (implementado por
 * {@link org.grox.web.GroxGrailsApplication}, fachada sobre a {@link org.grox.core.GroxApplication}). {@code xxxClasses} (ex.: {@code controllerClasses},
 * {@code domainClasses}) é resolvido dinamicamente.
 */
interface GrailsApplication {
    Config getConfig()
    ClassLoader getClassLoader()
    GrailsClass[] getArtefacts(String type)
    GrailsClass getArtefact(String type, String name)
    ApplicationContext getMainContext()
}
