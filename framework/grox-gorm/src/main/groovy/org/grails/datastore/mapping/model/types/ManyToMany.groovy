package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/** many-to-many: o dono tem a tabela de junção (o outro lado faz belongsTo dele). */
class ManyToMany extends ToMany {
    ManyToMany(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    @Override boolean isOwningSide() { property.joinTable != null }

    @Override boolean isBidirectional() { true }
}
