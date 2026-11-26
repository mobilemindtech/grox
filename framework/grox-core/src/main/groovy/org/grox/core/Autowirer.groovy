package org.grox.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Injeção de dependências por nome de propriedade, como no Grails ({@code def usuarioService}).
 * As propriedades injetáveis de cada classe são calculadas uma vez (controllers no modo
 * {@code request} são instanciados a cada requisição).
 */
class Autowirer {

    final Map<String, Object> beans
    private final Map<Class, List<List>> plan = new ConcurrentHashMap<>()

    Autowirer(Map<String, Object> beans) {
        this.beans = beans
    }

    /**
     * Estático: chamado a cada requisição nos controllers {@code concurrency: 'request'}. Dinâmico, o
     * {@code setProperty} recebia tipos diferentes de propriedade e o Groovy religava a chamada a cada
     * injeção (perfil JFR: ~30% do custo de uma action simples).
     */
    @groovy.transform.CompileStatic
    Object autowire(Object target) {
        Class c = target.getClass()
        List<List> injections = plan.get(c)
        if (injections == null) {
            injections = planFor(target)
            plan.put(c, injections)
        }
        for (List inj : injections) ((MetaProperty) inj[0]).setProperty(target, inj[1])
        target
    }

    /** Métodos {@code @PostConstruct} (sem argumentos) do bean, da superclasse para a subclasse, como no Spring. */
    static void postConstruct(Object bean) {
        List<Class> hierarchy = []
        for (Class c = org.grox.core.ClassUtils.userClass(bean.getClass()); c != null && c != Object; c = c.superclass) hierarchy.add(0, c)
        for (Class c : hierarchy) {
            for (java.lang.reflect.Method m : c.declaredMethods) {
                if (m.parameterCount == 0 && m.isAnnotationPresent(jakarta.annotation.PostConstruct)) {
                    m.accessible = true
                    try {
                        m.invoke(bean)
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.cause
                    }
                }
            }
        }
    }

    private List<List> planFor(Object target) {
        List<List> result = []
        for (MetaProperty mp : target.metaClass.properties) {
            Object bean = beans[mp.name]
            if (bean == null) continue
            if (!(mp instanceof MetaBeanProperty) || ((MetaBeanProperty) mp).setter == null) continue
            if (mp.type != Object && !mp.type.isInstance(bean)) continue
            result << [mp, bean]
        }
        result
    }
}
