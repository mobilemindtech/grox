package org.grails.web.json

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

/** Mesmo nome e pacote da exceção do Grails: chave ausente ou de tipo inválido em {@code getInt}, {@code getLong}... */
@CompileStatic
@InheritConstructors
class JSONException extends RuntimeException {
}
