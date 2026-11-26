package org.grails.core.io

import org.springframework.core.io.Resource

/** Compatibilidade com o bean {@code grailsResourceLocator}: recurso estático da aplicação pela URI. */
interface ResourceLocator {
    Resource findResourceForURI(String uri)
}
