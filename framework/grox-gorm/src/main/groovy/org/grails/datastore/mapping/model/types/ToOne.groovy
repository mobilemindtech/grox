package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

abstract class ToOne extends Association {
    ToOne(PersistentEntity owner, DomainProperty property) { super(owner, property) }
}
