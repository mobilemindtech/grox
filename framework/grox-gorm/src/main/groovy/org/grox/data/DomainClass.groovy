package org.grox.data

import groovy.util.logging.Slf4j
import org.grox.core.ClassUtils
import org.grox.data.validation.ConstraintsBuilder

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.time.temporal.Temporal

/**
 * Metadados de uma domain class, obtidos por convenção como no GORM: {@code id},
 * {@code version}, propriedades básicas, {@code hasMany}, {@code belongsTo},
 * {@code mappedBy}, {@code transients}, {@code mapping} e {@code constraints}.
 */
@Slf4j
class DomainClass {

    static final Set<Class> BASIC_TYPES = [String, Integer, int, Long, long, Short, short, Byte, byte, Double, double,
                                           Float, float, Boolean, boolean, Character, char, BigDecimal, BigInteger,
                                           Date, java.sql.Date, java.sql.Timestamp, Calendar, UUID, Currency, Locale,
                                           TimeZone, URL, URI, byte[], char[]] as Set
    static final Set<String> AUTO_TIMESTAMPS = ['dateCreated', 'lastUpdated'] as Set

    final Class<?> type
    final String name
    final String propertyName
    /** Identificador simples; null quando a chave é composta ({@link #compositeId}). */
    DomainProperty identity
    /** {@code id composite: ['role', 'user']}: a própria entidade é a chave (como no GORM). */
    List<String> compositeId
    DomainProperty version
    /** Discriminador de tenant (domain classes {@code MultiTenant}). */
    DomainProperty tenantProperty
    final Map<String, DomainProperty> persistentProperties = [:]
    final List<String> transientFields = []
    Map<String, Map<String, Object>> constraints
    MappingDsl mapping
    Map<String, Class> hasMany = [:]
    /** {@code static hibernateFilters = { ... }} (plugin hibernate-filter). */
    List<HibernateFilters.Def> hibernateFilters = []
    /** {@code static hasOne = [perfil: Perfil]}. */
    Map<String, Class> hasOne = [:]
    Map<String, Class> belongsToMap = [:]
    /** {@code static embedded = ['endereco']}: componentes gravados nas colunas desta tabela. */
    List<String> embedded = []
    /** Herança: a domain class mãe (a superclasse mais próxima que é domain class), ou null na raiz. */
    DomainClass parent
    /** Tem subclasses que são domain classes (é raiz ou nó intermediário de uma hierarquia). */
    boolean hasSubclasses
    /** Herança: as propriedades declaradas nesta classe (as demais vêm da mãe); null fora de hierarquia. */
    Set<String> ownProperties
    Set<Class> belongsToClasses = [] as Set

    DomainClass(Class<?> type) {
        this.type = type
        this.name = type.simpleName
        this.propertyName = ClassUtils.propertyName(type)
    }

    /** A raiz da hierarquia (a própria classe sem herança). */
    DomainClass getRoot() {
        parent == null ? this : parent.root
    }

    boolean isInHierarchy() {
        parent != null || hasSubclasses
    }

    /** Como no GORM: tabela única por hierarquia ({@code tablePerHierarchy false} na raiz: uma tabela por subclasse). */
    boolean isSingleTable() {
        root.mapping.settings.tablePerHierarchy != false
    }

    /** {@code static mapping = { discriminator 'CAO' }} ou {@code discriminator value: 'CAO', column: 'tipo'}; padrão: o nome da classe. */
    String getDiscriminatorValue() {
        Object d = mapping.settings.discriminator
        (d instanceof Map ? ((Map) d).value : d)?.toString() ?: type.name
    }

    /** Coluna do discriminador (na raiz): {@code class}, como no GORM. */
    String getDiscriminatorColumn() {
        Object d = root.mapping.settings.discriminator
        (d instanceof Map ? ((Map) d).column : null)?.toString() ?: 'class'
    }

    /** A tabela onde ficam as colunas desta classe. */
    String getTableName() {
        parent != null && singleTable ? root.tableName : (table ?: snake(type.simpleName))
    }

    /** As propriedades mapeadas nesta classe (na subclasse, sem as herdadas). */
    Collection<DomainProperty> getOwnPersistentProperties() {
        ownProperties == null ? persistentProperties.values() : persistentProperties.values().findAll { it.name in ownProperties }
    }

    boolean isComposite() {
        compositeId != null
    }

    private Boolean tenantHasOneCache

    /** Tem hasOne com filho multi-tenant ({@link DomainProperty#tenantHasOne}); calculado uma vez (consultado a cada save). */
    boolean isTenantHasOne() {
        Boolean c = tenantHasOneCache
        if (c == null) {
            c = persistentProperties.values().any { it.tenantHasOne }
            tenantHasOneCache = c
        }
        c
    }

    /** Partes da chave composta, na ordem declarada. */
    List<DomainProperty> getCompositeProperties() {
        compositeId.collect { persistentProperties[it] }
    }

    String getIdGenerator() {
        (mapping.settings.id instanceof Map ? ((Map) mapping.settings.id).generator : null)?.toString() ?: 'identity'
    }

    String getTable() {
        parent != null && singleTable ? root.table : mapping.settings.table?.toString()
    }

    boolean isMultiTenant() {
        grails.gorm.MultiTenant.isAssignableFrom(type)
    }

    String getTenantPropertyName() {
        Object t = mapping.settings.tenantId
        (t instanceof Map ? ((Map) t).name : null)?.toString() ?: 'tenantId'
    }

    boolean isAutoTimestamp() {
        mapping.settings.autoTimestamp != false
    }

    DomainProperty getAt(String prop) {
        persistentProperties[prop]
    }

    boolean hasPersistentProperty(String prop) {
        persistentProperties.containsKey(prop)
    }

    /** Constraints efetivas: propriedades persistentes são obrigatórias por padrão. */
    Map<String, Map<String, Object>> effectiveConstraints() {
        Map<String, Map<String, Object>> result = [:]
        persistentProperties.values().each { DomainProperty p ->
            if (p.kind in [DomainProperty.Kind.ID, DomainProperty.Kind.VERSION, DomainProperty.Kind.TENANT_ID, DomainProperty.Kind.ONE_TO_MANY, DomainProperty.Kind.MANY_TO_MANY, DomainProperty.Kind.ELEMENT_COLLECTION]) return
            if (p.formula) return
            Map<String, Object> rules = [nullable: p.name in AUTO_TIMESTAMPS]
            rules.putAll(constraints[p.name] ?: [:])
            result[p.name] = rules
        }
        constraints.each { k, v -> if (!result.containsKey(k) && !(persistentProperties[k]?.kind in [DomainProperty.Kind.ID, DomainProperty.Kind.VERSION])) result[k] = v }
        // componentes: as constraints da classe do componente, no campo endereco.rua (propriedades opcionais por padrão)
        persistentProperties.values().findAll { it.kind == DomainProperty.Kind.EMBEDDED }.each { DomainProperty p ->
            ConstraintsBuilder.declared(p.referencedType).each { String k, Map<String, Object> v ->
                if (p.embeddedProperties.containsKey(k)) result["${p.name}.${k}".toString()] = [nullable: true] + v
            }
        }
        result
    }

    /** Constrói os metadados de todas as domain classes (as associações dependem umas das outras). */
    static Map<Class, DomainClass> buildAll(Collection<Class<?>> types, boolean parallel = false) {
        Map<Class, DomainClass> all = [:]
        if (!parallel || types.size() < 8) {
            types.each { all[it] = new DomainClass(it).readStatics() }
            withoutComponents(all)
            linkHierarchy(all)
            all.values().each { it.resolveProperties(all) }
            inheritProperties(all)
            return all
        }
        // subida: a inicialização das classes (closures mapping/constraints) e a introspecção são
        // independentes por domain class; o mapa 'all' só é lido na segunda etapa
        def pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(2, Runtime.runtime.availableProcessors()),
                Thread.ofPlatform().name('grox-domain-', 0).factory())
        try {
            List<java.util.concurrent.Future<DomainClass>> read = types.collect { Class<?> t ->
                pool.submit({ new DomainClass(t).readStatics() } as java.util.concurrent.Callable<DomainClass>)
            }
            read.each { java.util.concurrent.Future<DomainClass> f -> DomainClass d = unwrap(f); all[d.type] = d }
            withoutComponents(all)
            linkHierarchy(all)
            all.values().collect { DomainClass d -> pool.submit({ d.resolveProperties(all) } as Runnable) }.each { unwrap(it) }
            inheritProperties(all)
        } finally {
            pool.shutdown()
        }
        all
    }

    /**
     * A classe de um {@code embedded} declarada em {@code grails-app/domain} (no mesmo arquivo da domain, como no GORM)
     * é um componente, não uma entidade: sai da lista (sem tabela própria).
     */
    private static void withoutComponents(Map<Class, DomainClass> all) {
        Set<Class> components = [] as Set
        all.values().each { DomainClass d ->
            d.embedded.each { String n -> Class t = fields(d.type).find { it.name == n }?.type; if (t != null) components << t }
        }
        components.each { all.remove(it) }
    }

    /**
     * Herança: liga cada domain class à superclasse mais próxima que também é domain class. Na subclasse, o
     * {@code mapping} e os filtros são só os declarados nela (o da mãe vale para a raiz da hierarquia).
     */
    private static void linkHierarchy(Map<Class, DomainClass> all) {
        all.values().each { DomainClass d ->
            for (Class k = d.type.superclass; k != null && k != Object; k = k.superclass) {
                if (all.containsKey(k)) {
                    d.parent = all[k]
                    all[k].hasSubclasses = true
                    d.mapping = MappingDsl.evaluate(staticBetween(d.type, k, 'mapping'))
                    d.hibernateFilters = staticBetween(d.type, k, 'hibernateFilters') != null ? HibernateFilters.read(d.type) : []
                    break
                }
            }
        }
        all.values().each { DomainClass d ->
            if (d.hasSubclasses && d.parent == null && d.mapping.settings.tablePerConcreteClass == true) {
                throw new UnsupportedOperationException("${d.type.name}: tablePerConcreteClass não é suportado (use a tabela única, padrão, ou tablePerHierarchy false)")
            }
        }
    }

    /** O campo estático {@code name} declarado entre {@code type} (inclusive) e {@code stop} (exclusive). */
    private static Object staticBetween(Class type, Class stop, String name) {
        for (Class k = type; k != null && k != stop; k = k.superclass) {
            Field f = k.declaredFields.find { it.name == name && Modifier.isStatic(it.modifiers) }
            if (f != null) {
                f.accessible = true
                return f.get(null)
            }
        }
        null
    }

    /** Herança: a subclasse recebe as propriedades da mãe (id, version, tenant e as demais), das raízes para as folhas. */
    private static void inheritProperties(Map<Class, DomainClass> all) {
        all.values().findAll { it.parent != null }.sort { DomainClass d -> int n = 0; for (DomainClass p = d.parent; p != null; p = p.parent) n++; n }.each { DomainClass d ->
            Map<String, DomainProperty> own = new LinkedHashMap<>(d.persistentProperties)
            d.persistentProperties.clear()
            d.persistentProperties.putAll(d.parent.persistentProperties)
            d.persistentProperties.putAll(own)
            d.ownProperties = own.keySet()
            d.identity = d.parent.identity
            d.version = d.parent.version
            d.tenantProperty = d.parent.tenantProperty
            d.compositeId = d.parent.compositeId
        }
    }

    private static <T> T unwrap(java.util.concurrent.Future<T> f) {
        try {
            f.get()
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.cause
        }
    }

    private DomainClass readStatics() {
        Object hm = ClassUtils.staticProperty(type, 'hasMany')
        if (hm instanceof Map) hasMany = ((Map) hm).collectEntries { k, v -> [(k.toString()): (Class) v] }
        Object ho = ClassUtils.staticProperty(type, 'hasOne')
        if (ho instanceof Map) hasOne = ((Map) ho).collectEntries { k, v -> [(k.toString()): (Class) v] }
        Object bt = ClassUtils.staticProperty(type, 'belongsTo')
        if (bt instanceof Map) {
            belongsToMap = ((Map) bt).collectEntries { k, v -> [(k.toString()): (Class) v] }
            belongsToClasses.addAll(belongsToMap.values())
        } else if (bt instanceof Class) {
            belongsToClasses << (Class) bt
        } else if (bt instanceof Collection) {
            belongsToClasses.addAll((Collection<Class>) bt)
        }
        Object emb = ClassUtils.staticProperty(type, 'embedded')
        if (emb instanceof Collection) embedded = ((Collection) emb)*.toString()
        mapping = MappingDsl.evaluate(ClassUtils.staticProperty(type, 'mapping'))
        hibernateFilters = HibernateFilters.read(type)
        Object idSetting = mapping.settings.id
        if (idSetting instanceof Map && ((Map) idSetting).composite) {
            compositeId = (((Map) idSetting).composite as List)*.toString()
        }
        constraints = ConstraintsBuilder.declared(type)
        this
    }

    private void resolveProperties(Map<Class, DomainClass> all) {
        List<String> transients = ((ClassUtils.staticProperty(type, 'transients') ?: []) as List)*.toString()
        Map mappedByMap = (Map) ClassUtils.staticProperty(type, 'mappedBy') ?: [:]
        for (Field f : (parent == null ? fields(type) : fields(type, parent.type))) {
            String n = f.name
            if (n in transients || !hasAccessors(n)) {
                transientFields << n
                continue
            }
            def p = new DomainProperty(name: n, type: f.type, declaringClass: f.declaringClass, nullable: constraints[n]?.nullable == true || n in AUTO_TIMESTAMPS)
            Map<String, Object> col = mapping.columns[n] ?: [:]
            if (col.column) p.column.name = col.column
            if (col.formula) p.formula = col.formula.toString()
            if (col.sqlType) p.column.sqlType = col.sqlType
            // GORM: type: 'text' (texto longo); sem sqlType explícito vira coluna TEXT
            else if (col.type?.toString() == 'text') p.column.sqlType = 'text'
            if (col.unique) p.column.unique = true
            Object length = col.length ?: lengthFromConstraints(constraints[n])
            if (length) p.column.length = length
            if (constraints[n]?.unique == true) p.column.unique = true

            if (n == 'id' && composite) {
                transientFields << n
                continue
            } else if (n == 'id') {
                p.kind = DomainProperty.Kind.ID
                identity = p
            } else if (n == 'version') {
                if (mapping.settings.version == false) {
                    transientFields << n
                    continue
                }
                p.kind = DomainProperty.Kind.VERSION
                version = p
            } else if (multiTenant && n == tenantPropertyName) {
                p.kind = DomainProperty.Kind.TENANT_ID
                tenantProperty = p
            } else if (hasMany.containsKey(n) && isBasic(hasMany[n])) {
                // hasMany = [tags: String]: tabela <dono>_<prop>, colunas <dono>_id, <prop>_<classe> e, em List, <prop>_idx (GORM)
                p.kind = DomainProperty.Kind.ELEMENT_COLLECTION
                p.referencedType = hasMany[n]
                Map jt = (Map) (mapping.columns[n]?.joinTable ?: [:])
                p.joinTable = jt.name ?: "${snake(table ?: type.simpleName)}_${snake(n)}".toString()
                p.joinColumn = jt.key ?: "${snake(ClassUtils.propertyName(type))}_id".toString()
                p.elementColumn = jt.column ?: "${snake(n)}_${hasMany[n].name.replace('.', '_').toLowerCase()}".toString()
                if (List.isAssignableFrom(f.type)) p.orderColumn = "${snake(n)}_idx".toString()
            } else if (hasMany.containsKey(n)) {
                p.kind = DomainProperty.Kind.ONE_TO_MANY
                p.referencedType = hasMany[n]
                DomainClass target = all[p.referencedType]
                if (target == null) throw new IllegalStateException("${type.name}.hasMany.${n}: ${p.referencedType.name} não é domain class")
                String inverse = mappedByMap[n]?.toString() ?: target.hasMany.find { k, v -> v == type && !(target.is(this) && k == n) }?.key
                if (inverse != null && target.hasMany[inverse] == type && !(target.is(this) && inverse == n)) {
                    // many-to-many como no GORM: o dono é o lado de quem o outro faz belongsTo
                    p.kind = DomainProperty.Kind.MANY_TO_MANY
                    boolean owner = target.belongsToClasses.contains(type)
                    if (owner == belongsToClasses.contains(target.type)) {
                        throw new IllegalStateException("many-to-many ${type.name}.${n} <-> ${target.type.name}.${inverse}: " +
                                'exatamente um dos lados deve declarar belongsTo o outro')
                    }
                    if (owner) {
                        String ownerName = ClassUtils.propertyName(type)
                        p.joinTable = "${snake(table ?: type.simpleName)}_${snake(n)}"
                        p.joinColumn = "${snake(ownerName)}_id"
                        p.inverseJoinColumn = "${snake(ClassUtils.propertyName(target.type))}_id"
                    } else {
                        p.mappedBy = inverse
                    }
                } else {
                    p.mappedBy = mappedByMap[n]?.toString() ?: (target.is(this) ? null : target.backReferenceTo(type))
                    p.cascadeAll = target.belongsToClasses.contains(type)
                    if (p.mappedBy == null) {
                        // unidirecional: tabela de junção com os nomes do GORM (Book.authors → book_author, book_authors_id, author_id)
                        Map jt = (Map) (mapping.columns[n]?.joinTable ?: [:])
                        p.joinTable = jt.name ?: "${snake(table ?: type.simpleName)}_${snake(target.table ?: target.type.simpleName)}".toString()
                        p.joinColumn = jt.key ?: "${snake(ClassUtils.propertyName(type))}_${snake(n)}_id".toString()
                        p.inverseJoinColumn = jt.column ?: "${snake(ClassUtils.propertyName(target.type))}_id".toString()
                    }
                }
            } else if (n in embedded) {
                if (all.containsKey(f.type) || isBasic(f.type)) throw new IllegalStateException("${type.name}.embedded.${n}: ${f.type.name} não pode ser componente (domain class ou tipo simples)")
                p.kind = DomainProperty.Kind.EMBEDDED
                p.referencedType = f.type
                p.embeddedProperties = componentProperties(n, f.type)
            } else if (hasOne.containsKey(n) && all.containsKey(hasOne[n])) {
                // hasOne: one-to-one inverso; a chave estrangeira está na referência de volta do filho
                p.kind = DomainProperty.Kind.ONE_TO_ONE
                p.referencedType = hasOne[n]
                p.mappedBy = all[p.referencedType].backReferenceTo(type)
                if (p.mappedBy == null) throw new IllegalStateException("${type.name}.hasOne.${n}: ${p.referencedType.name} precisa de uma referência a ${type.simpleName} (belongsTo)")
                p.cascadeAll = true
                p.nullable = true
                p.tenantHasOne = all[p.referencedType].multiTenant && !all[p.referencedType].composite
            } else if (all.containsKey(f.type) && (p.mappedBy = inverseOneToOne(all[f.type], n)) != null) {
                // GORM: Face { Nose nose } e Nose { belongsTo = [face: Face] }: a chave fica no dono (face.nose_id),
                // e a referência de volta do filho é o lado inverso, sem coluna (sem cascade para o dono)
                p.kind = DomainProperty.Kind.ONE_TO_ONE
                p.referencedType = f.type
                p.nullable = true
            } else if (all.containsKey(f.type) && all[f.type].hasOne.containsValue(type) && backReferenceTo(f.type) == n) {
                // filho de um hasOne: one-to-one dono da chave estrangeira (mesma coluna do many-to-one)
                p.kind = DomainProperty.Kind.ONE_TO_ONE
                p.referencedType = f.type
            } else if (all.containsKey(f.type)) {
                p.kind = DomainProperty.Kind.MANY_TO_ONE
                p.referencedType = f.type
                p.cascadeAll = all[f.type].belongsToClasses.contains(type) && !belongsToClasses.contains(f.type)
            } else if (isBasic(f.type)) {
                p.kind = DomainProperty.Kind.BASIC
            } else {
                log.debug("${type.name}.${n} (${f.type.name}) não é persistente")
                transientFields << n
                continue
            }
            persistentProperties[n] = p
        }
        if (parent != null) {
            // subclasse: id, version e tenant vêm da mãe (inheritProperties). Na tabela única, as colunas dela
            // aceitam nulo (as linhas das outras classes da hierarquia não as preenchem); a validação usa as constraints
            if (singleTable) persistentProperties.values().each { it.nullable = true }
            return
        }
        if (composite) {
            List<String> missing = compositeId.findAll { !persistentProperties.containsKey(it) }
            if (missing) throw new IllegalStateException("${type.name}: id composite com propriedades inexistentes: ${missing}")
        } else if (identity == null) {
            throw new IllegalStateException("${type.name} sem propriedade id")
        }
        if (multiTenant && tenantProperty == null) throw new IllegalStateException("${type.name} é MultiTenant mas não tem a propriedade ${tenantPropertyName}")
    }

    /**
     * {@code GroupOfClients} → {@code group_of_clients}, com a regra do {@code ImprovedNamingStrategy} do GORM:
     * só separa a maiúscula entre duas minúsculas. Dígitos e siglas não separam ({@code valor10Meses} →
     * {@code valor10meses}, {@code cpfCNPJ} → {@code cpfcnpj}), ao contrário do snake_case do Hibernate.
     */
    static String snake(String name) {
        name.replace('.', '_').replaceAll(/(?<=[a-z])(?=[A-Z][a-z])/, '_').toLowerCase(Locale.ROOT)
    }

    /**
     * {@code n} é o inverso de um one-to-one cuja chave está no dono: esta classe faz belongsTo o dono, e o dono
     * tem uma propriedade simples deste tipo (não hasOne nem hasMany). Devolve o nome dessa propriedade do dono.
     */
    private String inverseOneToOne(DomainClass owner, String n) {
        if (owner.is(this) || !belongsToClasses.contains(owner.type)) return null
        // hasOne é o outro caso; com hasMany deste tipo, o belongsTo é o many-to-one da coleção
        if (owner.hasOne.containsValue(type) || owner.hasMany.containsValue(type)) return null
        if (belongsToMap && belongsToMap[n] != owner.type) return null
        List<String> refs = fields(owner.type).findAll { it.type == type }*.name
        refs.size() == 1 ? refs[0] : null   // mais de uma referência: ambíguo, fica many-to-one
    }

    /** Propriedade desta classe que aponta para {@code owner} (lado many-to-one). */
    String backReferenceTo(Class owner) {
        String fromMap = belongsToMap.find { k, v -> v == owner }?.key
        if (fromMap) return fromMap
        fields(type).find { it.type == owner }?.name
    }

    /**
     * Propriedades básicas do componente, com as colunas do GORM: {@code endereco} + {@code cep} → {@code endereco_cep}.
     * Colunas aceitam nulo (o componente inteiro pode ser nulo); {@code maxSize}/{@code size} do componente dão o tamanho.
     */
    private static Map<String, DomainProperty> componentProperties(String owner, Class<?> component) {
        Map<String, Map<String, Object>> rules = ConstraintsBuilder.declared(component)
        Map<String, DomainProperty> result = [:]
        for (Field f : fields(component)) {
            String n = f.name
            if (n in ['id', 'version', 'errors'] || !isBasic(f.type)) continue
            String cap = n.capitalize()
            if (!component.methods.any { it.name == 'set' + cap }) continue
            def p = new DomainProperty(name: n, type: f.type, kind: DomainProperty.Kind.BASIC, nullable: true)
            p.column.name = "${snake(owner)}_${snake(n)}".toString()
            Object length = lengthFromConstraints(rules[n])
            if (length) p.column.length = length
            result[n] = p
        }
        result
    }

    private boolean hasAccessors(String n) {
        String cap = n.capitalize()
        type.methods.any { it.name == 'get' + cap || it.name == 'is' + cap } && type.methods.any { it.name == 'set' + cap }
    }

    static boolean isBasic(Class<?> t) {
        t in BASIC_TYPES || t.enum || Temporal.isAssignableFrom(t) || Number.isAssignableFrom(t)
    }

    private static Object lengthFromConstraints(Map<String, Object> c) {
        if (!c) return null
        if (c.size instanceof Range) return ((Range) c.size).to
        c.maxSize
    }

    /** Campos de instância da hierarquia, sem os internos do Groovy e dos traits. */
    static List<Field> fields(Class<?> type, Class<?> stop = Object) {
        List<Field> result = []
        for (Class k = type; k != null && k != Object && k != stop; k = k.superclass) {
            k.declaredFields.each { Field f ->
                int m = f.modifiers
                if (Modifier.isStatic(m) || Modifier.isTransient(m) || f.synthetic || f.name.contains('$') || f.name.contains('__') || f.name == 'metaClass') return
                result << f
            }
        }
        result
    }

    @Override
    String toString() {
        "DomainClass(${type.name}: ${persistentProperties.values()})"
    }
}
