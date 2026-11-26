package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/** Coleção de valores simples ({@code hasMany = [tags: String]}). */
class Basic extends ToMany {
    Basic(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    @Override boolean isOwningSide() { true }

    @Override boolean isBidirectional() { false }
}
