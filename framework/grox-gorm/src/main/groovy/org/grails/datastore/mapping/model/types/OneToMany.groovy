package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/** hasMany de entidades; dono quando o filho faz belongsTo (cascata). */
class OneToMany extends ToMany {
    OneToMany(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    @Override boolean isOwningSide() { property.cascadeAll }

    @Override boolean isBidirectional() { property.mappedBy != null }
}
