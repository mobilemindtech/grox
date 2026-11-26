package org.grox.data

/**
 * Gera o {@code orm.xml} (formato do Hibernate 7, superset do Jakarta Persistence 3.2) das domain classes, que são POGOs sem
 * anotações. Mapeamento por campo; a ordem dos elementos segue o XSD.
 */
class OrmXml {

    static String generate(Collection<DomainClass> domains) {
        StringBuilder sb = new StringBuilder('<?xml version="1.0" encoding="UTF-8"?>\n')
        // namespace do Hibernate (superset do JPA 3.2): necessário para <tenant-id>
        sb << '<entity-mappings xmlns="http://www.hibernate.org/xsd/orm/mapping" version="7.0">\n'
        // plugin hibernate-filter: nome global (a primeira condição é a padrão); ligado por padrão se algum declarar default
        Map<String, HibernateFilters.Def> firstByName = [:]
        domains.each { d -> d.hibernateFilters.each { f -> firstByName.putIfAbsent(f.name, f) } }
        Set<String> defaults = HibernateFilters.defaults(domains)
        firstByName.each { String name, HibernateFilters.Def f ->
            sb << "  <filter-def name=\"${esc(name)}\" auto-enabled=\"${name in defaults}\">"
            if (f.condition) sb << "<default-condition>${esc(f.condition)}</default-condition>"
            sb << '</filter-def>\n'
        }
        Map<Class, DomainClass> byType = domains.collectEntries { [(it.type): it] }
        domains.each { entity(sb, it, byType) }
        sb << '</entity-mappings>\n'
        sb.toString()
    }

    private static void entity(StringBuilder sb, DomainClass d, Map<Class, DomainClass> byType) {
        sb << "  <entity class=\"${d.type.name}\" name=\"${d.name}\" access=\"FIELD\" metadata-complete=\"true\">\n"
        if (d.table) sb << "    <table name=\"${esc(d.table)}\"/>\n"
        d.hibernateFilters.each { HibernateFilters.Def f ->
            sb << "    <filter name=\"${esc(f.name)}\">" << (f.condition ? "<condition>${esc(f.condition)}</condition>" : '') << '</filter>\n'
        }
        if (d.tenantProperty) {
            DomainProperty t = d.tenantProperty
            t.nullable = false
            sb << "    <tenant-id name=\"${t.name}\">" << column(t, true) << '</tenant-id>\n'
        }
        sb << '    <attributes>\n'
        def props = d.persistentProperties.values()
        if (d.composite) {
            // chave composta sem classe de id (a entidade é a chave): partes básicas viram <id>,
            // associações viram <many-to-one id="true"> mais abaixo
            d.compositeProperties.findAll { it.kind == DomainProperty.Kind.BASIC }.each { DomainProperty p ->
                sb << "      <id name=\"${p.name}\">" << column(p, false) << '</id>\n'
            }
        } else {
            DomainProperty id = d.identity
            sb << "      <id name=\"${id.name}\">"
            switch (d.idGenerator) {
                case 'identity' -> sb << '<generated-value strategy="IDENTITY"/>'
                case 'sequence' -> sb << '<generated-value strategy="SEQUENCE"/>'
                case 'uuid' -> sb << '<generated-value strategy="UUID"/>'
                case 'assigned' -> {}
                default -> throw new IllegalArgumentException("gerador de id não suportado em ${d.type.name}: ${d.idGenerator}")
            }
            sb << column(id, false) << '</id>\n'
        }
        Set<String> keyParts = (d.compositeId ?: []) as Set
        props.findAll { it.kind == DomainProperty.Kind.BASIC && !(it.name in keyParts) }.each { DomainProperty p ->
            sb << "      <basic name=\"${p.name}\" optional=\"${p.nullable || p.formula != null}\">"
            sb << (p.formula != null ? "<formula>${esc(p.formula)}</formula>" : column(p, true))
            if (p.type.enum) sb << '<enumerated>STRING</enumerated>'
            sb << '</basic>\n'
        }
        if (d.version) sb << "      <version name=\"${d.version.name}\"/>\n"
        props.findAll { it.kind == DomainProperty.Kind.MANY_TO_ONE }.each { DomainProperty p ->
            boolean key = p.name in keyParts
            sb << "      <many-to-one name=\"${p.name}\" target-entity=\"${p.referencedType.name}\" fetch=\"LAZY\" optional=\"${p.nullable && !key}\"" <<
                    (key ? ' id="true"' : '') << '>'
            if (p.column.name) sb << "<join-column name=\"${esc(p.column.name)}\"/>"
            if (p.cascadeAll) sb << '<cascade><cascade-all/></cascade>'
            sb << '</many-to-one>\n'
        }
        props.findAll { it.kind == DomainProperty.Kind.ONE_TO_MANY }.each { DomainProperty p ->
            sb << "      <one-to-many name=\"${p.name}\" target-entity=\"${p.referencedType.name}\" fetch=\"LAZY\""
            if (p.mappedBy) sb << " mapped-by=\"${p.mappedBy}\""
            if (p.cascadeAll) sb << ' orphan-removal="true"'
            sb << '>'
            if (!p.mappedBy && p.joinTable) {
                sb << "<join-table name=\"${esc(p.joinTable)}\"><join-column name=\"${esc(p.joinColumn)}\"/>" <<
                        "<inverse-join-column name=\"${esc(p.inverseJoinColumn)}\"/></join-table>"
            }
            sb << (p.cascadeAll ? '<cascade><cascade-all/></cascade>' : '<cascade><cascade-persist/><cascade-merge/></cascade>')
            sb << '</one-to-many>\n'
        }
        props.findAll { it.tenantHasOne }.each { DomainProperty p ->
            // ver DomainProperty.tenantHasOne; min() tolera linhas duplicadas no filho. EAGER: o Hibernate força com not-found
            // (o one-to-one inverso também não era lazy), e a carga por id usa o batch fetch
            sb << "      <many-to-one name=\"${p.name}\" target-entity=\"${p.referencedType.name}\" fetch=\"EAGER\" fetch-mode=\"SELECT\" optional=\"true\" not-found=\"IGNORE\">" <<
                    "<join-formula>${esc(hasOneFormula(d, p, byType[p.referencedType]))}</join-formula></many-to-one>\n"
        }
        props.findAll { it.kind == DomainProperty.Kind.ONE_TO_ONE && !it.tenantHasOne }.each { DomainProperty p ->
            sb << "      <one-to-one name=\"${p.name}\" target-entity=\"${p.referencedType.name}\" fetch=\"LAZY\""
            if (p.mappedBy && p.cascadeAll) {
                sb << " mapped-by=\"${p.mappedBy}\" orphan-removal=\"true\"><cascade><cascade-all/></cascade></one-to-one>\n"
            } else if (p.mappedBy) {
                // inverso de um one-to-one com a chave no dono (Nose.face): sem coluna e sem cascade
                sb << " mapped-by=\"${p.mappedBy}\" optional=\"true\"/>\n"
            } else {
                sb << " optional=\"${p.nullable}\">"
                if (p.column.name) sb << "<join-column name=\"${esc(p.column.name)}\"/>"
                sb << '</one-to-one>\n'
            }
        }
        props.findAll { it.kind == DomainProperty.Kind.MANY_TO_MANY }.each { DomainProperty p ->
            sb << "      <many-to-many name=\"${p.name}\" target-entity=\"${p.referencedType.name}\" fetch=\"LAZY\""
            if (p.mappedBy) {
                sb << " mapped-by=\"${p.mappedBy}\"/>\n"
                return
            }
            sb << "><join-table name=\"${esc(p.joinTable)}\"><join-column name=\"${esc(p.joinColumn)}\"/>" <<
                    "<inverse-join-column name=\"${esc(p.inverseJoinColumn)}\"/></join-table>" <<
                    '<cascade><cascade-persist/><cascade-merge/></cascade></many-to-many>\n'
        }
        props.findAll { it.kind == DomainProperty.Kind.ELEMENT_COLLECTION }.each { DomainProperty p ->
            sb << "      <element-collection name=\"${p.name}\" target-class=\"${p.referencedType.name}\" fetch=\"LAZY\">"
            if (p.orderColumn) sb << "<order-column name=\"${esc(p.orderColumn)}\"/>"
            if (p.referencedType.enum) sb << '<enumerated>STRING</enumerated>'
            sb << "<column name=\"${esc(p.elementColumn)}\"/>" <<
                    "<collection-table name=\"${esc(p.joinTable)}\"><join-column name=\"${esc(p.joinColumn)}\"/></collection-table>" <<
                    '</element-collection>\n'
        }
        d.transientFields.unique().each { sb << "      <transient name=\"${it}\"/>\n" }
        sb << '    </attributes>\n  </entity>\n'
    }

    /**
     * {@code (select min(c.id) from filho c where c.pai_id = id)}: nomes físicos como os do {@code GroxNamingStrategy}
     * (snake_case); o {@code id} sem qualificador é a coluna do dono, qualificada pelo Hibernate.
     */
    static String hasOneFormula(DomainClass owner, DomainProperty p, DomainClass child) {
        String ownerId = DomainClass.snake((String) owner.identity.column.name ?: owner.identity.name)
        String childId = DomainClass.snake((String) child.identity.column.name ?: child.identity.name)
        DomainProperty back = child.persistentProperties[p.mappedBy]
        String fk = DomainClass.snake((String) back.column.name ?: "${p.mappedBy}_${ownerId}".toString())
        String table = DomainClass.snake(child.table ?: child.type.simpleName)
        // dono também multi-tenant: só o filho do mesmo tenant (um filho de outro tenant, por dado inconsistente,
        // não deve esconder o filho certo nem escapar do cascade de delete)
        String sameTenant = ''
        if (owner.tenantProperty != null && child.tenantProperty != null) {
            String ownerTenant = DomainClass.snake((String) owner.tenantProperty.column.name ?: owner.tenantProperty.name)
            String childTenant = DomainClass.snake((String) child.tenantProperty.column.name ?: child.tenantProperty.name)
            sameTenant = " and grox_c.${childTenant} = ${ownerTenant}"
        }
        "(select min(grox_c.${childId}) from ${table} grox_c where grox_c.${fk} = ${ownerId}${sameTenant})"
    }

    private static String column(DomainProperty p, boolean withNullable) {
        Map c = p.column
        if (!c && !withNullable) return ''
        StringBuilder sb = new StringBuilder('<column')
        if (c.name) sb << " name=\"${esc(c.name)}\""
        if (withNullable) sb << " nullable=\"${p.nullable}\""
        if (c.unique) sb << ' unique="true"'
        if (c.length) sb << " length=\"${c.length}\""
        if (c.sqlType) sb << " column-definition=\"${esc(c.sqlType)}\""
        sb << '/>'
        sb.toString()
    }

    private static String esc(Object v) {
        v.toString().replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    }
}
