package org.grails.datastore.mapping.model.types

import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty

/** Associação entre domain classes (GORM): entidade associada, lado dono e bidirecionalidade. */
abstract class Association extends PersistentProperty {

    Association(PersistentEntity owner, DomainProperty property) { super(owner, property) }

    PersistentEntity getAssociatedEntity() {
        property.referencedType && Gorm.isDomain(property.referencedType) ? new PersistentEntity(Gorm.domain(property.referencedType)) : null
    }

    /** Propriedade do outro lado (bidirecional) ou null. */
    String getReferencedPropertyName() { property.mappedBy }

    abstract boolean isOwningSide()

    abstract boolean isBidirectional()

    boolean isCircular() { property.referencedType == owner.javaClass }
}
