package org.grox.service

import groovy.concurrent.Actor
import org.grox.core.ClassUtils

/**
 * Services singleton. Os marcados com {@code static actor = true} ficam atrás de um ator e os
 * interceptados ({@link ServiceInterceptor}: {@code @Transactional}, tenant) ganham o comportamento
 * do módulo; nesses casos o bean exposto é um proxy.
 */
class ServiceRegistry implements AutoCloseable {

    static final List<ServiceInterceptor> INTERCEPTORS =
            ServiceLoader.load(ServiceInterceptor, ServiceRegistry.classLoader).toList().sort { it.order }.asImmutable()

    /** nome → bean exposto (proxy do ator ou a própria instância) */
    final Map<String, Object> beans = [:]
    /** nome → instância real */
    final Map<String, Object> targets = [:]
    private final List<Actor> actors = []

    void register(Class<?> type) {
        String name = ClassUtils.propertyName(type)
        Object target = type.getDeclaredConstructor().newInstance()
        targets[name] = target
        boolean actor = isActor(type)
        Map<String, List<Closure>> arounds = interceptors(type, target)
        beans[name] = actor || arounds ? ServiceProxies.create(type, target, actor, arounds, actors) : target
    }

    /** Interceptadores de cada método, na ordem (o primeiro por fora). */
    static Map<String, List<Closure>> interceptors(Class<?> type, Object target) {
        Set<String> methods = ServiceProxies.proxiedMethods(type)
        Map<String, List<Closure>> arounds = [:]
        INTERCEPTORS.each { ServiceInterceptor i ->
            i.interceptors(type, target, methods).each { String m, Closure c -> arounds.computeIfAbsent(m) { [] } << c }
        }
        arounds
    }

    static boolean isActor(Class<?> type) {
        ClassUtils.staticProperty(type, 'actor') == true
    }

    int getActorCount() {
        actors.size()
    }

    @Override
    void close() {
        actors*.stop()
    }
}
