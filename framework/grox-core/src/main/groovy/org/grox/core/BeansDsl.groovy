package org.grox.core

import groovy.util.logging.Slf4j

/**
 * Avalia {@code grails-app/conf/spring/resources.groovy} (no classpath como
 * {@code spring/resources.groovy}) no subconjunto usado na prática:
 * <pre>
 * beans = {
 *     meuBean(MinhaClasse)                    // instância via construtor sem argumentos
 *     outro(OutraClasse, 'arg1', 2)           // argumentos de construtor
 *     terceiro(Classe) { prop = 'x'; dep = ref('meuBean') }
 *     relogio = java.time.Clock.systemUTC()   // instância pronta (extensão do Grox)
 * }
 * </pre>
 * Os beans são depois injetados por nome (como os services) e podem ter métodos
 * {@code @Listener} de eventos GORM.
 */
@Slf4j
class BeansDsl {

    static final String RESOURCE = 'spring/resources.groovy'

    /** Beans definidos (em ordem) ou mapa vazio se não houver resources.groovy. */
    static Map<String, Object> load(ClassLoader loader, Map<String, Object> existing) {
        URL url = loader.getResource(RESOURCE)
        if (url == null) return [:]
        def binding = new Binding()
        new GroovyShell(loader, binding).evaluate(url.getText('UTF-8'), 'resources.groovy')
        Object beans = binding.hasVariable('beans') ? binding.getVariable('beans') : null
        beans instanceof Closure ? evaluate((Closure) beans, existing, 'resources.groovy') : [:]
    }

    /**
     * Avalia um bloco de beans (resources.groovy, {@code doWithSpring()} da {@code Application} ou de um
     * descritor de plugin). Refs a beans declarados depois, em propriedades, são resolvidas no fim do bloco.
     */
    static Map<String, Object> evaluate(Closure beans, Map<String, Object> existing, String source) {
        def dsl = new BeansDsl(existing, source)
        Closure c = (Closure) beans.clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = dsl
        c.call()
        dsl.resolvePending()
        dsl.defined
    }

    private final Map<String, Object> existing
    private final String source
    final Map<String, Object> defined = [:]
    /** Propriedades com ref a bean ainda não definido: [bean, propriedade, valor com LazyRef]. */
    private final List<List> pending = []

    private BeansDsl(Map<String, Object> existing, String source) {
        this.existing = existing
        this.source = source
    }

    /** {@code nome = instancia}: um bean já construído (extensão do Grox; o BeanBuilder do Spring não tem). */
    void propertyMissing(String name, Object value) {
        defined[name] = value
    }

    /** Referência a outro bean; se ainda não definido, resolvida no fim do bloco (só em propriedades). */
    Object ref(String name) {
        Object b = defined[name] ?: existing[name]
        b != null ? b : new LazyRef(name)
    }

    private Object resolve(Object v) {
        if (v instanceof LazyRef) {
            Object b = defined[v.name] ?: existing[v.name]
            if (b == null) throw new IllegalStateException("${source}: bean '${v.name}' não definido")
            return b
        }
        if (v instanceof List) return ((List) v).collect { resolve(it) }
        if (v instanceof Map) return ((Map) v).collectEntries { k, x -> [k, resolve(x)] }
        v
    }

    private static boolean hasLazy(Object v) {
        v instanceof LazyRef || (v instanceof Collection && ((Collection) v).any { hasLazy(it) }) ||
                (v instanceof Map && ((Map) v).values().any { hasLazy(it) })
    }

    private void resolvePending() {
        pending.each { List p -> p[0][(String) p[1]] = resolve(p[2]) }
        pending.clear()
    }

    /** Ref pendente. */
    static class LazyRef {
        final String name

        LazyRef(String name) { this.name = name }

        String toString() { "ref('${name}')" }
    }

    /** Nome de um bean já definido usado direto, sem {@code ref()} ({@code estrategia(Classe, sessionRegistry)}), como no DSL do Spring. */
    Object propertyMissing(String name) {
        Object b = defined[name] ?: existing[name]
        if (b == null) throw new MissingPropertyException("${source}: bean ou propriedade '${name}' não definido", name, BeansDsl)
        b
    }

    /** Blocos do DSL do Spring que não se aplicam ao Grox. */
    void xmlns(Map ignored) {}

    void importBeans(String ignored) { log.warn("resources.groovy: importBeans('$ignored') ignorado no Grox") }

    def methodMissing(String name, Object args) {
        Object[] a = (Object[]) args
        if (!a || !(a[0] instanceof Class)) {
            throw new IllegalArgumentException("${source}: bean '$name' sem classe (só a forma nome(Classe, ...) é suportada)")
        }
        Class<?> type = (Class<?>) a[0]
        Closure config = a.length > 1 && a[-1] instanceof Closure ? (Closure) a[-1] : null
        int end = config ? a.length - 1 : a.length
        List ctorArgs = end > 1 ? a[1..<end].toList() : []   // a[1..-2] com só a closure seria o intervalo invertido a[1..0]
        if (ctorArgs.any { hasLazy(it) }) throw new IllegalStateException("${source}: bean '$name' usa no construtor um bean declarado depois")
        Object bean = ctorArgs ? type.newInstance(ctorArgs as Object[]) : type.getDeclaredConstructor().newInstance()
        if (config) {
            config.resolveStrategy = Closure.DELEGATE_FIRST
            config.delegate = new BeanConfigurer(bean, this)
            // { bean -> bean.autowire = true; bean.scope = '...' }: definição do Spring (o Grox injeta por nome sempre)
            config.maximumNumberOfParameters == 0 ? config.call() : config.call(new BeanDefinition())
        }
        defined[name] = bean
        bean
    }

    /** Parâmetro do bloco de configuração ({@code bean.autowire}, {@code bean.scope}...): aceito e ignorado. */
    static class BeanDefinition {
        Object autowire
        String scope
        String initMethod
        String destroyMethod
        boolean lazyInit
        Object parent
        boolean abstractBean
    }

    private static class BeanConfigurer {
        final Object bean
        final BeansDsl dsl

        BeanConfigurer(Object bean, BeansDsl dsl) { this.bean = bean; this.dsl = dsl }

        Object ref(String name) { dsl.ref(name) }

        void propertyMissing(String name, Object value) {
            if (hasLazy(value)) dsl.pending << [bean, name, value] else bean[name] = value
        }

        Object propertyMissing(String name) { bean[name] }
    }
}
