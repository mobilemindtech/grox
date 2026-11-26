package org.grox.web

import grails.core.ApplicationContext
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.util.Environment
import org.grox.core.Config
import org.grox.core.GroxApplication

/**
 * O bean {@code grailsApplication} (e {@code Holders.grailsApplication}): fachada do Grails sobre a
 * {@link GroxApplication} do core, com o {@code mainContext} e os artefatos como {@link GrailsClass}.
 */
class GroxGrailsApplication implements GrailsApplication {

    final GroxApplication application
    /** Beans da aplicação (services, grailsApplication, messageSource...), preenchido pelo {@link GrailsModule}. */
    ApplicationContext mainContext = new ApplicationContext([:])

    GroxGrailsApplication(GroxApplication application) {
        this.application = application
    }

    Config getConfig() { application.config }

    ClassLoader getClassLoader() { application.classLoader }

    Class<?> getApplicationClass() { application.applicationClass }

    String getBasePackage() { application.basePackage }

    Environment getEnvironment() { Environment.forName(application.environmentName) }

    String getEnvironmentName() { application.environmentName }

    /**
     * Descrição própria de um tipo de artefato, no lugar da {@link GrailsClass} genérica (ex.: o plugin de jobs
     * registra {@code Job} → {@code DefaultGrailsJobClass}, como os {@code ArtefactHandler} do Grails).
     */
    final Map<String, java.util.function.Function<Class<?>, GrailsClass>> grailsClassFactories = [:]

    /** Como no Grails: os artefatos do tipo como {@link GrailsClass} ({@code it.clazz} é a classe). */
    GrailsClass[] getArtefacts(String type) {
        application.getArtefacts(type).collect { grailsClass(it, type) } as GrailsClass[]
    }

    GrailsClass getArtefact(String type, String name) {
        Class<?> c = application.getArtefacts(type).find { it.name == name || it.simpleName == name || new GrailsClass(it, type).logicalPropertyName == name }
        c == null ? null : grailsClass(c, type)
    }

    GrailsClass grailsClass(Class<?> c, String type) {
        def factory = grailsClassFactories[type]
        factory == null ? new GrailsClass(c, type) : factory.apply(c)
    }

    /** {@code grailsApplication.controllerClasses}, {@code domainClasses}, {@code serviceClasses}... */
    Object propertyMissing(String name) {
        if (name.endsWith('Classes') && name.length() > 7) {
            String type = name.substring(0, 1).toUpperCase() + name.substring(1, name.length() - 7)
            return getArtefacts(type) as List
        }
        throw new org.codehaus.groovy.runtime.metaclass.MissingPropertyExceptionNoStack(name, GroxGrailsApplication)
    }
}
