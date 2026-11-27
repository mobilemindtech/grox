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
        // superclasses que não são domain (base abstrata em src): <mapped-superclass> com os campos declarados nelas
        Map<Class, DomainClass> supers = [:]
        domains.each { DomainClass d ->
            for (Class k = d.type.superclass; k != null && k != Object && !byType.containsKey(k); k = k.superclass) {
                if (DomainClass.fields(k, k.superclass)) supers.putIfAbsent(k, d)
            }
        }
        supers.each { Class k, DomainClass d ->
            sb << "  <mapped-superclass class=\"${k.name}\" access=\"FIELD\" metadata-complete=\"true\">\n    <attributes>\n"
            attributes(sb, d, k, byType)
            sb << '    </attributes>\n  </mapped-superclass>\n'
        }
        domains.each { entity(sb, it, byType) }
        // componentes (embedded): um <embeddable> por classe; as colunas de cada dono vêm do <attribute-override>
        Map<Class, DomainProperty> components = [:]
        domains.each { d -> d.persistentProperties.values().findAll { it.kind == DomainProperty.Kind.EMBEDDED }.each { components.putIfAbsent(it.referencedType, it) } }
        components.each { Class type, DomainProperty p -> embeddable(sb, type, p) }
        sb << '</entity-mappings>\n'
        sb.toString()
    }

    private static void entity(StringBuilder sb, DomainClass d, Map<Class, DomainClass> byType) {
        sb << "  <entity class=\"${d.type.name}\" name=\"${d.name}\" access=\"FIELD\" metadata-complete=\"true\">\n"
        boolean child = d.parent != null
        if (d.table && !(child && d.singleTable)) sb << "    <table name=\"${esc(d.table)}\"/>\n"
        // herança como no GORM: tabela única com a coluna class (nome da classe), ou uma tabela por subclasse
        if (d.hasSubclasses && !child) sb << "    <inheritance strategy=\"${d.singleTable ? 'SINGLE_TABLE' : 'JOINED'}\"/>\n"
        if (d.inHierarchy && d.singleTable) {
            sb << "    <discriminator-value>${esc(d.discriminatorValue)}</discriminator-value>\n"
            if (!child) sb << "    <discriminator-column name=\"${esc(d.discriminatorColumn)}\" length=\"255\"/>\n"
        }
        d.hibernateFilters.each { HibernateFilters.Def f ->
            sb << "    <filter name=\"${esc(f.name)}\">" << (f.condition ? "<condition>${esc(f.condition)}</condition>" : '') << '</filter>\n'
        }
        if (d.tenantProperty && !child) {
            DomainProperty t = d.tenantProperty
            t.nullable = false
            sb << "    <tenant-id name=\"${t.name}\">" << column(t, true) << '</tenant-id>\n'
        }
        sb << '    <attributes>\n'
        attributes(sb, d, d.type, byType)
        sb << '    </attributes>\n  </entity>\n'
    }

    /** A classe que declara a propriedade. */
    private static Class declaredIn(DomainProperty p, DomainClass d) {
        p.declaringClass ?: d.type
    }

    /**
     * Os atributos de {@code d} declarados em {@code owner} (a própria entidade ou uma superclasse que não é domain).
     * Na subclasse de outra domain, id e version são declarados na raiz e ficam de fora.
     */
    private static void attributes(StringBuilder sb, DomainClass d, Class owner, Map<Class, DomainClass> byType) {
        boolean child = d.parent != null
        def props = d.ownPersistentProperties.findAll { declaredIn(it, d) == owner }
        boolean withKey = !child && (d.composite ? owner == d.type : declaredIn(d.identity, d) == owner)
        if (withKey && d.composite) {
            // chave composta sem classe de id (a entidade é a chave): partes básicas viram <id>,
            // associações viram <many-to-one id="true"> mais abaixo
            d.compositeProperties.findAll { it.kind == DomainProperty.Kind.BASIC }.each { DomainProperty p ->
                sb << "      <id name=\"${p.name}\">" << column(p, false) << '</id>\n'
            }
        } else if (withKey) {
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
        if (d.version && !child && declaredIn(d.version, d) == owner) sb << "      <version name=\"${d.version.name}\"/>\n"
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
        props.findAll { it.kind == DomainProperty.Kind.EMBEDDED }.each { DomainProperty p ->
            sb << "      <embedded name=\"${p.name}\">"
            p.embeddedProperties.values().each { DomainProperty c ->
                sb << "<attribute-override name=\"${c.name}\">" << column(c, true) << '</attribute-override>'
            }
            sb << '</embedded>\n'
        }
        // campos desta classe que não são mapeados (com metadata-complete, o Hibernate mapearia todos)
        Set<String> mapped = props*.name as Set
        if (d.version && declaredIn(d.version, d) == owner) mapped << d.version.name
        DomainClass.fields(owner, owner.superclass).findAll { !(it.name in mapped) && !(d.persistentProperties[it.name] && declaredIn(d.persistentProperties[it.name], d) == owner) }
                .each { sb << "      <transient name=\"${it.name}\"/>\n" }
    }

    /** A classe do componente: as propriedades básicas e, como transientes, os demais campos (id/version da AST, por exemplo). */
    private static void embeddable(StringBuilder sb, Class type, DomainProperty p) {
        sb << "  <embeddable class=\"${type.name}\" access=\"FIELD\" metadata-complete=\"true\">\n    <attributes>\n"
        p.embeddedProperties.values().each { DomainProperty c ->
            sb << "      <basic name=\"${c.name}\">" << (c.type.enum ? '<enumerated>STRING</enumerated>' : '') << '</basic>\n'
        }
        DomainClass.fields(type).findAll { !p.embeddedProperties.containsKey(it.name) }.each { sb << "      <transient name=\"${it.name}\"/>\n" }
        sb << '    </attributes>\n  </embeddable>\n'
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
