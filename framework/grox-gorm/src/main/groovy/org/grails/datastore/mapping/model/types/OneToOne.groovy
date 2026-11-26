package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/** hasOne do GORM: no dono (inverso, com mappedBy) e no filho (com a chave estrangeira). */
class OneToOne extends ToOne {
    OneToOne(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    @Override boolean isOwningSide() { property.mappedBy != null && property.cascadeAll }

    @Override boolean isBidirectional() { true }
}
