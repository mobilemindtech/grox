package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty

/** Propriedade de valor simples. */
class Simple extends PersistentProperty {
    Simple(PersistentEntity owner, DomainProperty property) { super(owner, property) }
}
