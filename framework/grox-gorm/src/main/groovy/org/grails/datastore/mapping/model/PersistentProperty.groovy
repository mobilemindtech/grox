package org.grails.datastore.mapping.model

import org.grox.data.DomainProperty

/** Compatibilidade com o modelo de mapeamento do GORM: propriedade persistente (somente leitura). */
class PersistentProperty {
    final PersistentEntity owner
    final DomainProperty property

    PersistentProperty(PersistentEntity owner, DomainProperty property) {
        this.owner = owner
        this.property = property
    }

    String getName() { property.name }

    Class getType() { property.type }

    boolean isNullable() { property.nullable }

    String toString() { "${owner.javaClass.simpleName}.${name}" }
}
