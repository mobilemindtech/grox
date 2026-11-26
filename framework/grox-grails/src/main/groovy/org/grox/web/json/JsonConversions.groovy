package org.grox.web.json

import grails.converters.JSON
import org.codehaus.groovy.runtime.DefaultGroovyMethods

/** Extension module: habilita {@code obj as JSON} para qualquer objeto. */
class JsonConversions {

    static <T> T asType(Object self, Class<T> type) {
        if (type == JSON) return (T) new JSON(self)
        DefaultGroovyMethods.asType(self, type)
    }
}
