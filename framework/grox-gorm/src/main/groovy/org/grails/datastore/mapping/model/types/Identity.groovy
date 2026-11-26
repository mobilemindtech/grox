package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty

/** Identificador da entidade. */
class Identity extends PersistentProperty {
    Identity(PersistentEntity owner, DomainProperty property) { super(owner, property) }
}
