package org.grails.datastore.mapping.model

import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Identity
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.Simple

/**
 * Compatibilidade com o {@code PersistentEntity} do GORM: visão somente leitura do mapeamento de uma
 * domain class ({@code hibernateDatastore.mappingContext.getPersistentEntity(nome)},
 * {@code Dominio.gormPersistentEntity}).
 */
class PersistentEntity {
    final DomainClass domain

    PersistentEntity(DomainClass domain) { this.domain = domain }

    String getName() { domain.type.name }

    Class getJavaClass() { domain.type }

    String getDecapitalizedName() { domain.propertyName }

    boolean isMultiTenant() { domain.multiTenant }

    PersistentProperty getIdentity() {
        domain.identity ? new Identity(this, domain.identity) : null
    }

    PersistentProperty[] getCompositeIdentity() {
        domain.composite ? domain.compositeProperties.collect { wrap(it) } as PersistentProperty[] : null
    }

    PersistentProperty getVersion() {
        domain.version ? new Simple(this, domain.version) : null
    }

    boolean isVersioned() { domain.version != null }

    /** Sem a identidade, como no GORM. */
    List<PersistentProperty> getPersistentProperties() {
        domain.persistentProperties.values().findAll { it.kind != DomainProperty.Kind.ID }.collect { wrap(it) }
    }

    List<String> getPersistentPropertyNames() { persistentProperties*.name }

    List<PersistentProperty> getAssociations() { persistentProperties.findAll { it instanceof org.grails.datastore.mapping.model.types.Association } }

    PersistentProperty getPropertyByName(String name) {
        DomainProperty p = domain.persistentProperties[name]
        p == null ? null : (p.kind == DomainProperty.Kind.ID ? new Identity(this, p) : wrap(p))
    }

    private PersistentProperty wrap(DomainProperty p) {
        switch (p.kind) {
            case DomainProperty.Kind.MANY_TO_ONE: return new ManyToOne(this, p)
            case DomainProperty.Kind.ONE_TO_ONE: return new org.grails.datastore.mapping.model.types.OneToOne(this, p)
            case DomainProperty.Kind.ONE_TO_MANY: return new OneToMany(this, p)
            case DomainProperty.Kind.MANY_TO_MANY: return new ManyToMany(this, p)
            case DomainProperty.Kind.ELEMENT_COLLECTION: return new Basic(this, p)
            default: return new Simple(this, p)
        }
    }

    boolean equals(Object o) { o instanceof PersistentEntity && ((PersistentEntity) o).javaClass == javaClass }

    int hashCode() { javaClass.hashCode() }

    String toString() { name }
}
