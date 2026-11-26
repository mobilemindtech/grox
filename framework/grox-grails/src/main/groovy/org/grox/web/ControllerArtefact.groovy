package org.grox.web

import org.grox.core.ClassUtils

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Metadados de um controller: actions, action padrão, {@code allowedMethods},
 * {@code responseFormats} e modelo de concorrência
 * ({@code static concurrency = 'pool' | 'request'}, {@code static poolSize = N}).
 */
class ControllerArtefact implements org.grox.routing.RouteTarget {

    static final Set<String> NON_ACTIONS = ['invokeMethod', 'getProperty', 'setProperty', 'getMetaClass', 'setMetaClass',
                                            'methodMissing', 'propertyMissing', 'toString', 'equals', 'hashCode'] as Set

    final Class<?> type
    final String logicalName
    final Map<String, ActionMethod> actions
    final String defaultAction
    final Map<String, List<String>> allowedMethods
    final List<String> responseFormats
    final String concurrency
    final Integer poolSize
    ControllerDispatcher dispatcher

    ControllerArtefact(Class<?> type) {
        this.type = type
        this.logicalName = ClassUtils.logicalName(type, 'Controller')
        this.actions = discoverActions(type)
        Object da = ClassUtils.staticProperty(type, 'defaultAction')
        this.defaultAction = da?.toString() ?: (actions.containsKey('index') ? 'index' : (actions.size() == 1 ? actions.keySet().first() : null))
        Map am = (Map) ClassUtils.staticProperty(type, 'allowedMethods') ?: [:]
        this.allowedMethods = am.collectEntries { k, v ->
            [(k.toString()): (v instanceof Collection ? v : [v]).collect { it.toString().toUpperCase() }]
        } as Map<String, List<String>>
        Object rf = ClassUtils.staticProperty(type, 'responseFormats')
        this.responseFormats = rf ? (rf as List)*.toString() : null
        this.concurrency = ClassUtils.staticProperty(type, 'concurrency')?.toString()
        Object ps = ClassUtils.staticProperty(type, 'poolSize')
        this.poolSize = ps != null ? ps as Integer : null
    }

    static Map<String, ActionMethod> discoverActions(Class<?> type) {
        List<Method> annotated = type.methods.findAll { it.isAnnotationPresent(Action) }
        List<Method> candidates = annotated ?: type.declaredMethods.findAll { Method m ->
            Modifier.isPublic(m.modifiers) && !Modifier.isStatic(m.modifiers) && !m.synthetic &&
                    !m.name.contains('$') && !(m.name in NON_ACTIONS) &&
                    !(m.name ==~ /(get|set|is)[A-Z].*/) &&
                    // métodos do trait ControllerApi (redirect, render...) e os gerados: nunca são actions
                    !m.isAnnotationPresent(groovy.transform.Generated) &&
                    !m.isAnnotationPresent(org.codehaus.groovy.transform.trait.Traits.TraitBridge)
        }
        Map<String, ActionMethod> result = [:]
        // com parâmetros default o Groovy gera sobrecargas: usa a de mais parâmetros
        candidates.groupBy { it.name }.each { String n, List<Method> ms ->
            result[n] = new ActionMethod(ms.max { it.parameterCount })
        }
        closureActions(type).each { String n -> if (!result.containsKey(n)) result[n] = new ActionMethod(n) }
        result
    }

    /**
     * Actions como propriedades closure ({@code def list = { ... }}, estilo Grails 2, ainda aceito pelo Grails):
     * propriedades de instância que, num controller recém-criado, guardam uma Closure. Só propriedades (com getter
     * público), como no Grails: um campo {@code private helper = { ... }} não vira action roteável.
     */
    private static List<String> closureActions(Class<?> type) {
        List<java.lang.reflect.Field> fields = type.declaredFields.findAll { java.lang.reflect.Field f ->
            !Modifier.isStatic(f.modifiers) && !f.synthetic && !f.name.contains('$') && (f.type == Object || f.type == Closure) &&
                    hasPublicGetter(type, f.name)
        }
        if (!fields) return []
        Object sample
        try {
            sample = type.getDeclaredConstructor().newInstance()
        } catch (Exception ignored) {
            return []
        }
        fields.findAll { java.lang.reflect.Field f ->
            f.accessible = true
            f.get(sample) instanceof Closure
        }*.name
    }

    private static boolean hasPublicGetter(Class<?> type, String name) {
        try {
            Modifier.isPublic(type.getMethod('get' + name.capitalize()).modifiers)
        } catch (NoSuchMethodException ignored) {
            false
        }
    }

    @Override
    boolean hasAction(String name) {
        actions.containsKey(name)
    }

    List<String> allowedMethodsFor(String action) {
        allowedMethods[action]
    }
}
