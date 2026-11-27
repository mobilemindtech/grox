package org.grox.service

import groovy.concurrent.Actor
import groovy.concurrent.Awaitable
import org.codehaus.groovy.runtime.InvokerHelper
import org.grox.core.ContextPropagation

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ExecutionException
import java.util.function.Function

/**
 * Proxy-subclasse de um service (injeção tipada continua funcionando) que aplica:
 * <ul>
 *   <li>{@code static actor = true}: cada chamada vira mensagem para um ator; quem chama
 *       bloqueia (em virtual thread) e o contexto ({@link ContextPropagation}) é propagado;</li>
 *   <li>os {@link ServiceInterceptor} dos módulos (transação e tenant do GORM), do primeiro (por fora)
 *       ao último.</li>
 * </ul>
 * Chamadas internas ({@code this.metodo()}) não passam pelo proxy.
 * <p>{@code @CompileStatic}: roda em toda chamada de service, e o mesmo call site atende services de tipos
 * diferentes (no despacho dinâmico, o Groovy religava o call site a cada troca de tipo).
 */
@groovy.transform.CompileStatic
class ServiceProxies {

    static final Set<String> SKIP = ['getMetaClass', 'setMetaClass', 'invokeMethod', 'getProperty', 'setProperty',
                                     'equals', 'hashCode', 'toString', 'getClass', 'getLog'] as Set

    /** @param arounds por método, os interceptadores na ordem (o primeiro por fora) */
    static Object create(Class<?> type, Object target, boolean actor, Map<String, List<Closure>> arounds, List<Actor> actors) {
        Closure invoker = { String name, Object[] args ->
            Closure call = { -> InvokerHelper.invokeMethod(target, name, args) }
            List<Closure> chain = arounds[name]
            if (chain) {
                for (int i = chain.size() - 1; i >= 0; i--) {
                    Closure around = chain[i]
                    Closure inner = call
                    call = { -> around.call(inner) }
                }
            }
            call.call()
        }
        Map<String, Closure> impl = [:]
        if (actor) {
            Actor<ServiceCall> a = Actor.reactor({ ServiceCall call ->
                ContextPropagation.bind(call.context()) { invoker.call(call.method(), call.args()) }
            } as Function<ServiceCall, Object>)
            actors << a
            proxiedMethods(type).each { String name ->
                impl[name] = { Object[] args -> await(a.sendAndGet(new ServiceCall(name, args, ContextPropagation.capture()))) }
            }
        } else {
            // todos os métodos vão ao alvo, não só os interceptados: o proxy é outra instância, sem as dependências
            // injetadas (um método sem anotação rodava no proxy e via o service injetado como null)
            proxiedMethods(type).each { String name ->
                impl[name] = arounds.containsKey(name) ?
                        { Object[] args -> invoker.call(name, args) } :
                        { Object[] args -> InvokerHelper.invokeMethod(target, name, args) }   // só delega, sem as closures do invoker
            }
        }
        ProxyGenerator.INSTANCE.instantiateAggregateFromBaseClass(impl, type)
    }

    static Set<String> proxiedMethods(Class<?> type) {
        Set<String> names = new LinkedHashSet<>()
        for (Method m : type.methods) {
            if (!Modifier.isStatic(m.modifiers) && !Modifier.isFinal(m.modifiers) && !m.synthetic &&
                    m.declaringClass != Object && m.declaringClass != GroovyObject &&
                    !m.name.contains('$') && !(m.name in SKIP)) names << m.name
        }
        names
    }

    static Object await(Awaitable<Object> result) {
        try {
            result.get()
        } catch (ExecutionException e) {
            throw e.cause ?: e
        }
    }
}
