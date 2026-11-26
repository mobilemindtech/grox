package org.grails.datastore.mapping.engine

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper

/** Acesso às propriedades da entidade do evento (antes da gravação, altera a própria instância). */
@CompileStatic
class EntityAccess {
    final Object entity

    EntityAccess(Object entity) { this.entity = entity }

    Object getProperty(String name) { InvokerHelper.getProperty(entity, name) }

    void setProperty(String name, Object value) { InvokerHelper.setProperty(entity, name, value) }

    Object getIdentifier() { InvokerHelper.getProperty(entity, 'id') }
}
