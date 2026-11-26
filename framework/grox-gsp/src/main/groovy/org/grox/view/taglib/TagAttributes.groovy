package org.grox.view.taglib

import groovy.transform.CompileStatic
import org.grox.http.GroxParams

/**
 * Atributos de tag com conversão tipada, como o {@code GroovyPageAttributes} do Grails:
 * {@code attrs.int('total')}, {@code attrs.boolean('ativo')}, {@code attrs.list('ids')}...
 */
@CompileStatic
class TagAttributes extends GroxParams {

    TagAttributes(Map attrs) {
        if (attrs) putAll((Map<String, Object>) attrs)
    }

    /** Sem a busca aninhada ({@code a.b}) do {@code params}. */
    @Override
    Object get(Object key) {
        getOrDefault(key, null)
    }
}
