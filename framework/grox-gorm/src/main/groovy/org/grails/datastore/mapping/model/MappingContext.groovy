package org.grails.datastore.mapping.model

import org.grox.data.DomainClass

/** Compatibilidade com o {@code MappingContext} do GORM ({@code hibernateDatastore.mappingContext}). */
class MappingContext {
    private final Map<Class, DomainClass> domains

    MappingContext(Map<Class, DomainClass> domains) { this.domains = domains }

    PersistentEntity getPersistentEntity(String className) {
        DomainClass d = domains.values().find { it.type.name == className }
        d == null ? null : new PersistentEntity(d)
    }

    Collection<PersistentEntity> getPersistentEntities() {
        domains.values().collect { new PersistentEntity(it) }
    }
}
