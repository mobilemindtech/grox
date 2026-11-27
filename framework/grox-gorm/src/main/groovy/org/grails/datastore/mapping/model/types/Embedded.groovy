package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/** {@code static embedded = ['endereco']}: componente gravado nas colunas do dono (GORM). */
class Embedded extends ToOne {
    Embedded(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    boolean isOwningSide() { true }

    boolean isBidirectional() { false }
}
