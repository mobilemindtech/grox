package org.grox.core

import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/** Entrega síncrona de {@link ApplicationEvent} aos beans {@link ApplicationListener} do tipo do evento. */
@CompileStatic
class ApplicationEvents {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEvents)

    /**
     * Chama {@code onApplicationEvent} de cada listener cujo tipo genérico aceita o evento. Uma falha num
     * listener é registrada no log e não interrompe os demais nem quem publicou.
     */
    static void publish(Collection<Object> beans, ApplicationEvent event) {
        for (Object bean : beans) {
            if (!(bean instanceof ApplicationListener) || !eventType(bean.getClass()).isInstance(event)) continue
            try {
                ((ApplicationListener) bean).onApplicationEvent(event)
            } catch (Exception e) {
                log.error("Listener ${bean.getClass().name} falhou com ${event.getClass().simpleName}: ${e.message}", e)
            }
        }
    }

    /** O {@code E} de {@code ApplicationListener<E>} na hierarquia da classe ({@code ApplicationEvent} se cru). */
    static Class<?> eventType(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object; c = c.superclass) {
            for (Type t : c.genericInterfaces) {
                if (t instanceof ParameterizedType && ((ParameterizedType) t).rawType == ApplicationListener) {
                    Type arg = ((ParameterizedType) t).actualTypeArguments[0]
                    if (arg instanceof Class) return (Class<?>) arg
                    if (arg instanceof ParameterizedType) return (Class<?>) ((ParameterizedType) arg).rawType
                }
            }
        }
        ApplicationEvent
    }
}
