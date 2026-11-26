package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grails.datastore.mapping.model.PersistentEntity

/** Referência a outra entidade; dona quando a outra faz belongsTo desta. */
class ManyToOne extends ToOne {
    ManyToOne(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    @Override
    boolean isOwningSide() { property.cascadeAll }

    @Override
    boolean isBidirectional() {
        def target = Gorm.isDomain(property.referencedType) ? Gorm.domain(property.referencedType) : null
        Class mine = this.owner.javaClass   // dentro da closure, "owner" seria o dono da closure
        target != null && target.persistentProperties.values().any { it.collection && it.referencedType == mine }
    }
}
