package org.grox.data

/** Propriedade persistente de uma domain class. */
class DomainProperty {

    /** ELEMENT_COLLECTION: {@code hasMany = [tags: String]} (valores simples numa tabela de junção). */
    /** ONE_TO_ONE: hasOne do GORM (no dono, inverso com {@code mappedBy}; no filho, com a chave estrangeira). */
    /** EMBEDDED: {@code static embedded = ['endereco']} (as propriedades do componente em {@link #embeddedProperties}). */
    enum Kind { ID, VERSION, TENANT_ID, BASIC, MANY_TO_ONE, ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY, ELEMENT_COLLECTION, EMBEDDED }

    String name
    Class<?> type
    /** A classe que declara o campo (a domain ou uma superclasse dela que não é domain: {@code <mapped-superclass>}). */
    Class<?> declaringClass
    Kind kind
    /** Tipo da entidade associada (many-to-one) ou dos elementos (one-to-many). */
    Class<?> referencedType
    String mappedBy
    boolean cascadeAll
    /**
     * hasOne cujo filho é multi-tenant: mapeado como many-to-one só leitura por {@code join-formula}, com
     * {@code not-found IGNORE}. O {@code @TenantId} do Hibernate 7 filtra também a carga por chave, e no one-to-one
     * inverso o filho ausente dava {@code EntityFilterException} (o Hibernate não distingue ausente de filtrado).
     * Sem cascade do Hibernate (a ordem do many-to-one apagaria o pai antes do filho): o Grox grava e apaga o filho
     * ({@link HasOneCascade}).
     */
    boolean tenantHasOne
    /** many-to-many, lado dono: tabela de junção e colunas (nomes lógicos, estilo GORM). */
    /** {@code static mapping = { total formula: 'a - b' }}: calculada pelo banco, só leitura. */
    String formula
    String joinTable
    String joinColumn
    String inverseJoinColumn
    /** ELEMENT_COLLECTION: coluna do valor e, em List, a coluna de posição (nomes do GORM). */
    String elementColumn
    String orderColumn
    boolean nullable
    /** EMBEDDED: propriedades básicas do componente, com as colunas no dono ({@code endereco_rua}). */
    Map<String, DomainProperty> embeddedProperties
    /** column, length, unique, sqlType (de {@code static mapping} e das constraints). */
    Map<String, Object> column = [:]

    boolean isAssociation() {
        kind == Kind.MANY_TO_ONE || kind == Kind.ONE_TO_ONE || kind == Kind.ONE_TO_MANY || kind == Kind.MANY_TO_MANY
    }

    /** Coleção do hasMany: de entidades (one-to-many, many-to-many) ou de valores simples. */
    boolean isCollection() {
        kind == Kind.ONE_TO_MANY || kind == Kind.MANY_TO_MANY || kind == Kind.ELEMENT_COLLECTION
    }

    Object get(Object entity) {
        entity[name]
    }

    void set(Object entity, Object value) {
        entity[name] = value
    }

    @Override
    String toString() {
        "${name}:${kind}${referencedType ? "(${referencedType.simpleName})" : ''}"
    }
}
