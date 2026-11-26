package org.grox.view.taglib

import org.grox.core.ClassUtils
import org.grox.http.SafeHtml
import org.grox.view.gsp.GspException
import org.grox.view.gsp.GspRenderContext

/**
 * TagLibs registradas por namespace ({@code static namespace = 'fmt'}, padrão {@code g}).
 * Cada tag é uma propriedade Closure {@code def tag = { attrs, body -> }} ou, como no Grails 3+, um
 * método público {@code def tag(Map attrs)} / {@code def tag(Map attrs, Closure body)} da TagLib (singleton).
 */
@groovy.transform.CompileStatic
class TagLibRegistry {

    static final Set<String> NOT_TAGS = ['class', 'metaClass', 'out', 'g', 'log', 'properties'] as Set

    static class TagDef {
        Object owner
        String name
        Closure closure
        /** Tag escrita como método (closure null); chamada por reflexão, sem passar pelo metaclass. */
        java.lang.reflect.Method method
        boolean returnsObject
    }

    private final Map<String, Map<String, TagDef>> namespaces = [:]

    void register(Object tagLib) {
        Class<?> type = tagLib.getClass()
        String ns = ClassUtils.staticProperty(type, 'namespace')?.toString() ?: 'g'
        Set<String> returning = ((ClassUtils.staticProperty(type, 'returnObjectForTags') ?: []) as List)*.toString() as Set
        Map<String, TagDef> tags = namespaces.computeIfAbsent(ns) { [:] }
        // campos de instância com closure (def tag = { attrs -> }) da classe e das superclasses; não chama getters
        // (os do trait, como webRequest/grailsAttributes, só funcionam durante uma renderização)
        for (Class<?> c = type; c != null && c != Object; c = c.superclass) {
            for (java.lang.reflect.Field f : c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers) || f.synthetic || f.name.contains('$') || f.name in NOT_TAGS) continue
                if (!Closure.isAssignableFrom(f.type) && f.type != Object) continue
                if (tags.containsKey(f.name) && tags[f.name].owner.is(tagLib)) continue
                f.accessible = true
                Object value = f.get(tagLib)
                if (value instanceof Closure) {
                    tags[f.name] = new TagDef(owner: tagLib, name: f.name, closure: (Closure) value, returnsObject: f.name in returning)
                }
            }
            // métodos de tag: (Map attrs) ou (Map attrs, body); com sobrecarga, vale a que recebe o corpo
            List<java.lang.reflect.Method> methods = c.declaredMethods.findAll { isTagMethod(it) }
                    .sort { java.lang.reflect.Method m -> -m.parameterCount }
            for (java.lang.reflect.Method m : methods) {
                if (tags.containsKey(m.name) && tags[m.name].owner.is(tagLib)) continue
                m.trySetAccessible()
                tags[m.name] = new TagDef(owner: tagLib, name: m.name, method: m, returnsObject: m.name in returning)
            }
        }
    }

    /**
     * Método público de instância escrito na TagLib com assinatura de tag. Ficam de fora os gerados
     * pelo Groovy (getters/setters, GroovyObject) e os que vêm do trait {@link TagLibApi}.
     */
    private static boolean isTagMethod(java.lang.reflect.Method m) {
        int mod = m.modifiers
        if (!java.lang.reflect.Modifier.isPublic(mod) || java.lang.reflect.Modifier.isStatic(mod) || m.synthetic || m.bridge) return false
        if (m.name.contains('$') || m.name in NOT_TAGS) return false
        if (m.isAnnotationPresent(groovy.transform.Generated) || m.isAnnotationPresent(org.codehaus.groovy.transform.trait.Traits.TraitBridge)) return false
        Class<?>[] p = m.parameterTypes
        if (p.length == 0 || p.length > 2 || p[0] != Map) return false
        p.length == 1 || p[1] == Closure || p[1] == Object
    }

    Set<String> getNamespaceNames() {
        namespaces.keySet()
    }

    boolean hasNamespace(String ns) {
        namespaces.containsKey(ns)
    }

    /**
     * Namespace de uma tag chamada sem prefixo em expressões ({@code ${assetPath(src: 'x')}}):
     * {@code g} primeiro; senão, o único namespace que tem a tag (null se nenhum ou ambíguo).
     */
    String bareTagNamespace(String name) {
        if (has('g', name)) return 'g'
        List<String> found = namespaces.findAll { k, v -> v.containsKey(name) }.keySet() as List<String>
        found.size() == 1 ? found[0] : null
    }

    boolean has(String ns, String name) {
        namespaces[ns]?.containsKey(name)
    }

    Map<String, TagDef> tagsOf(String ns) {
        namespaces[ns] ?: [:]
    }

    TagDef lookup(String ns, String name) {
        TagDef t = namespaces[ns]?.get(name)
        if (t == null) throw new GspException(null, 0, "tag desconhecida <${ns}:${name}>")
        t
    }

    /** Uso como tag: escreve direto no writer corrente. */
    void invoke(String ns, String name, Map attrs, Closure body, GspRenderContext rc) {
        TagDef t = lookup(ns, name)
        Object result = callTag(t, attrs, body)
        if (t.returnsObject && result != null) rc.out.write(result.toString())
    }

    /** Uso como método: captura a saída (ou devolve o objeto, em returnObjectForTags). */
    Object invokeAsMethod(String ns, String name, Map attrs, Closure body, GspRenderContext rc) {
        TagDef t = lookup(ns, name)
        Object result = null
        SafeHtml out = rc.capture { result = callTag(t, attrs, body) }
        t.returnsObject ? result : out
    }

    private static Object callTag(TagDef t, Map attrs, Closure body) {
        Closure b = body ?: EMPTY_BODY
        Map a = attrs instanceof TagAttributes ? attrs : new TagAttributes(attrs)
        if (t.method != null) {
            try {
                return t.method.parameterCount == 1 ? t.method.invoke(t.owner, a) : t.method.invoke(t.owner, a, b)
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.cause   // a exceção da própria tag, como na closure
            }
        }
        return switch (t.closure.maximumNumberOfParameters) {
            case 0 -> t.closure.call()
            case 1 -> t.closure.call(a)
            default -> t.closure.call(a, b)
        }
    }

    static final Closure EMPTY_BODY = { Object arg = null -> new SafeHtml('') }

    TagNamespace namespace(String ns) {
        new TagNamespace(ns, this)
    }
}
