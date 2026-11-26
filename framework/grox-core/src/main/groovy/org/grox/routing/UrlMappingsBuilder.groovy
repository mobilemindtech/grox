package org.grox.routing

import static org.grox.routing.UrlMapping.MARK

/**
 * Delegate do closure {@code static mappings = { ... }} do Grails.
 *
 * Suporta: padrões com variáveis ({@code "/$controller/$action?/$id?(.$format)?"}),
 * atributos ({@code controller, action, view, redirect}), corpo com
 * {@code constraints { }} e parâmetros extras, verbos ({@code get "/x"(...)}),
 * {@code method:}, {@code resources:}, {@code group} e códigos de erro ({@code "404"(...)}).
 */
class UrlMappingsBuilder {

    static final Set<String> MAPPING_ATTRS = ['controller', 'action', 'view', 'redirect', 'uri', 'namespace', 'id', 'permanent', 'params', 'plugin'] as Set
    static final Set<String> HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'] as Set

    final List<UrlMapping> mappings = []
    /** Dono do closure {@code mappings} (a classe UrlMappings), para métodos auxiliares da app. */
    Object owner
    private String prefix = ''
    private int counter

    /** {@code $controller} dentro do GString vira um marcador de variável. */
    def propertyMissing(String name) {
        MARK + name + MARK
    }

    def methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj
        // ex.: findControllersInPackage('pkg').each { ... } — método da própria classe UrlMappings
        if (owner != null && !name.startsWith('/') && name != '*' && !(name ==~ /\d{3}/) && ownerResponds(name, args)) {
            return owner instanceof Class ? org.codehaus.groovy.runtime.InvokerHelper.invokeStaticMethod((Class) owner, name, args)
                    : org.codehaus.groovy.runtime.InvokerHelper.invokeMethod(owner, name, args)
        }
        String verb = name.toUpperCase()
        if (name == verb.toLowerCase() && verb in HTTP_METHODS) {
            List<UrlMapping> nested = args.findAll { it instanceof UrlMapping } as List<UrlMapping>
            if (nested) {
                nested.each { it.httpMethod = verb }
                return nested[0]
            }
            Map attrs = (Map) (args.find { it instanceof Map } ?: [:])
            String p = args.find { it instanceof CharSequence }?.toString()
            Closure body = (Closure) args.find { it instanceof Closure }
            return add(p, attrs + [method: verb], body)
        }
        if (name == 'group' && args.length == 2 && args[1] instanceof Closure) {
            String previous = prefix
            prefix = previous + args[0].toString()
            try {
                Closure c = (Closure) args[1]
                c.delegate = this
                c.resolveStrategy = Closure.DELEGATE_FIRST
                c.call()
            } finally {
                prefix = previous
            }
            return null
        }
        Map attrs = [:]
        Closure body = null
        for (Object a : args) {
            if (a instanceof Map) attrs.putAll((Map) a)
            else if (a instanceof Closure) body = (Closure) a
        }
        add(name, attrs, body)
    }

    private boolean ownerResponds(String name, Object[] args) {
        Class<?> type = owner instanceof Class ? (Class) owner : owner.getClass()
        type.declaredMethods.any { it.name == name && it.parameterCount == args.length }
    }

    UrlMapping add(String rawPattern, Map attrs, Closure body) {
        if (rawPattern ==~ /\d{3}/) {
            return register(new UrlMapping(statusCode: rawPattern as int, pattern: rawPattern), attrs, body)
        }
        String full = prefix ? prefix + (rawPattern == '/' ? '' : rawPattern) : rawPattern
        if (attrs.resources) return addResources(full, attrs.resources.toString(), attrs.findAll { k, v -> k != 'resources' })
        register(UrlMapping.parse(full), attrs, body)
    }

    private UrlMapping register(UrlMapping m, Map attrs, Closure body) {
        attrs.each { k, v ->
            String key = k.toString()
            if (key == 'method') m.httpMethod = v.toString().toUpperCase()
            else if (key == 'constraints') return
            else if (key in MAPPING_ATTRS) m.attrs[key] = v
            // demais atributos (ex. format: 'json') viram parâmetros
            else ((Map) m.attrs.computeIfAbsent('params') { [:] })[key] = v
        }
        if (body) {
            Closure c = (Closure) body.clone()
            c.delegate = new MappingBody(m)
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
        }
        m.order = counter++
        mappings << m
        m
    }

    /** {@code "/usuarios"(resources: 'usuario')} → rotas REST do Grails. */
    UrlMapping addResources(String base, String controller, Map extra = [:]) {
        String id = base + "/${MARK}id${MARK}"
        String fmt = "(.${MARK}format${MARK})?"
        List<UrlMapping> created = [
                ['GET', base + '/create', 'create'],
                ['GET', id + '/edit', 'edit'],
                ['GET', base, 'index'],
                ['POST', base, 'save'],
                ['GET', id, 'show'],
                ['PUT', id, 'update'],
                ['PATCH', id, 'patch'],
                ['DELETE', id, 'delete'],
        ].collect { List<String> r -> register(UrlMapping.parse(r[1] + fmt), extra + [controller: controller, action: r[2], method: r[0]], null) }
        created[0]
    }

    static class MappingBody {
        static final Set<String> ATTRS = ['controller', 'action', 'view', 'namespace', 'redirect', 'uri', 'id', 'plugin'] as Set
        final UrlMapping mapping

        MappingBody(UrlMapping mapping) {
            this.mapping = mapping
        }

        void constraints(Closure c) {
            Closure cl = (Closure) c.clone()
            cl.delegate = new ConstraintsBuilder(mapping.constraints)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl.call()
        }

        def propertyMissing(String name) {
            name in ATTRS ? mapping.attrs[name] : ((Map) mapping.attrs.params)?.get(name)
        }

        void propertyMissing(String name, Object value) {
            if (name == 'method') mapping.httpMethod = value.toString().toUpperCase()
            else if (name in ATTRS) mapping.attrs[name] = value
            else ((Map) mapping.attrs.computeIfAbsent('params') { [:] })[name] = value
        }
    }

    static class ConstraintsBuilder {
        final Map<String, Map> target

        ConstraintsBuilder(Map<String, Map> target) {
            this.target = target
        }

        def methodMissing(String name, Object args) {
            Object[] a = (Object[]) args
            target[name] = a.length ? (Map) a[0] : [:]
            null
        }
    }
}
