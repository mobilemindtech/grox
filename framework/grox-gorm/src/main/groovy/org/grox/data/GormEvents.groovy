package org.grox.data

import grails.events.annotation.gorm.Listener
import groovy.transform.CompileStatic
import org.grox.core.ClassUtils
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener

import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Listeners de persistência registrados a partir de beans com métodos {@link Listener}
 * (ex.: o {@code UserPasswordEncoderListener} do spring-security). Disparados pelo
 * {@code save()} do Grox antes de gravar ({@code Pre*}) e depois ({@code Post*}).
 */
@CompileStatic
class GormEvents {

    private static final List<Registration> LISTENERS = new CopyOnWriteArrayList<>()

    private static class Registration {
        Object bean
        Method method
        Class<?> eventType
        Class[] entities
    }

    /** Listeners no estilo do GORM (subclasses de {@code AbstractPersistenceEventListener}). */
    private static final List<AbstractPersistenceEventListener> PERSISTENCE_LISTENERS = new CopyOnWriteArrayList<>()

    /** Registra os métodos {@code @Listener} do bean (ou o próprio bean, se for um listener do GORM); devolve quantos. */
    static int register(Object bean) {
        if (bean instanceof AbstractPersistenceEventListener) {
            PERSISTENCE_LISTENERS << (AbstractPersistenceEventListener) bean
            return 1
        }
        int n = 0
        for (Method m : ClassUtils.userClass(bean.getClass()).methods) {
            Listener l = m.getAnnotation(Listener)
            if (l == null || m.parameterCount != 1 || !AbstractPersistenceEvent.isAssignableFrom(m.parameterTypes[0])) continue
            m.accessible = true
            LISTENERS << new Registration(bean: bean, method: m, eventType: m.parameterTypes[0], entities: l.value())
            n++
        }
        n
    }

    static void reset() {
        LISTENERS.clear()
        PERSISTENCE_LISTENERS.clear()
    }

    static boolean isEmpty() { LISTENERS.isEmpty() && PERSISTENCE_LISTENERS.isEmpty() }

    /** Dispara o evento; devolve {@code false} se algum listener o cancelou. */
    static boolean fire(AbstractPersistenceEvent event) {
        if (LISTENERS.isEmpty() && PERSISTENCE_LISTENERS.isEmpty()) return true
        for (AbstractPersistenceEventListener l : PERSISTENCE_LISTENERS) {
            if (l.supportsEventType(event.getClass())) l.onApplicationEvent(event)
        }
        Class<?> entityClass = ClassUtils.userClass(event.entityObject.getClass())
        for (Registration r : LISTENERS) {
            if (!r.eventType.isInstance(event)) continue
            if (r.entities.length && !r.entities.any { Class c -> c.isAssignableFrom(entityClass) }) continue
            try {
                r.method.invoke(r.bean, event)
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.cause
            }
        }
        !event.cancelled
    }
}
