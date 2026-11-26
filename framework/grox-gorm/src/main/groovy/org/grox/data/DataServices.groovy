package org.grox.data

import grails.gorm.services.Service
import org.codehaus.groovy.runtime.InvokerHelper

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Implementação dinâmica (proxy) de GORM Data Services. Convenções suportadas:
 * <ul>
 *   <li>{@code get(id)}, {@code read(id)}, {@code count()}, {@code list(Map)}</li>
 *   <li>{@code save(entidade)}: grava com {@code failOnError} (lança {@code ValidationException})</li>
 *   <li>{@code delete(id)} (ou {@code delete(entidade)})</li>
 *   <li>{@code find*}, {@code findAll*}, {@code count*}: finders dinâmicos da domain class</li>
 * </ul>
 * Cada chamada roda numa transação ({@code @ReadOnly} não faz diferença aqui).
 */
class DataServices {

    @groovy.transform.CompileStatic
    static boolean isDataService(Class<?> c) {
        (c.interface || java.lang.reflect.Modifier.isAbstract(c.modifiers)) && c.getAnnotation(Service) != null
    }

    static Object create(Class<?> iface, ClassLoader loader) {
        if (!iface.interface) throw new IllegalStateException("${iface.name}: só Data Services como interface são suportados")
        Class<?> domain = iface.getAnnotation(Service).value()
        if (domain == Object) throw new IllegalStateException("${iface.name}: informe a domain class em @Service(Dominio)")
        InvocationHandler handler = { Object proxy, Method m, Object[] args ->
            if (m.declaringClass == Object) return objectMethod(iface, proxy, m, args)
            Persistence.withTransaction { invoke(domain, m, args ?: new Object[0]) }
        } as InvocationHandler
        Proxy.newProxyInstance(loader, [iface] as Class[], handler)
    }

    private static Object invoke(Class<?> domain, Method m, Object[] args) {
        String n = m.name
        switch (n) {
            case 'get':
            case 'read':
                return InvokerHelper.invokeStaticMethod(domain, 'get', args[0])
            case 'count':
                if (args.length == 0) return convert(InvokerHelper.invokeStaticMethod(domain, 'count', null), m.returnType)
                break
            case 'list':
                return InvokerHelper.invokeStaticMethod(domain, 'list', args.length ? args[0] : [:])
            case 'save':
                Object entity = args[0] instanceof Map ? domain.newInstance(args[0]) : args[0]
                return InvokerHelper.invokeMethod(entity, 'save', [failOnError: true])
            case 'delete':
                Object entity = domain.isInstance(args[0]) ? args[0] : InvokerHelper.invokeStaticMethod(domain, 'get', args[0])
                if (entity != null) InvokerHelper.invokeMethod(entity, 'delete', null)
                return m.returnType == Void.TYPE ? null : entity
        }
        if (n.startsWith('find') || n.startsWith('count') || n.startsWith('list')) {
            return convert(InvokerHelper.invokeStaticMethod(domain, n, args), m.returnType)
        }
        throw new UnsupportedOperationException("Data Service ${m.declaringClass.simpleName}.${n}: método sem implementação por convenção no Grox")
    }

    private static Object convert(Object v, Class<?> type) {
        if (v == null || type == Object || type.isInstance(v)) return v
        if (type == Long || type == long) return ((Number) v).longValue()
        if (type == Integer || type == int) return ((Number) v).intValue()
        v
    }

    private static Object objectMethod(Class<?> iface, Object proxy, Method m, Object[] args) {
        switch (m.name) {
            case 'toString': return "DataService(${iface.simpleName})".toString()
            case 'hashCode': return System.identityHashCode(proxy)
            case 'equals': return proxy.is(args[0])
        }
        null
    }
}
