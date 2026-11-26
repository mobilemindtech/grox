package org.grox.dsl

import org.grox.security.AccessRule

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.regex.Pattern

/**
 * Interceptor das rotas do {@code grox-dsl}: {@code before}, {@code after} ou {@code error}, por closure
 * (DSL de rotas) ou por método de um {@link Handler} ({@link Before}, {@link After}, {@link OnError}), com um
 * padrão Ant de caminho opcional ({@code before('/api/**') { }}).
 * <ul>
 *   <li>{@code before}: antes do handler; devolver {@code false} ou responder ({@code render}, {@code redirect})
 *       interrompe a rota</li>
 *   <li>{@code after}: depois do handler, com o valor que ele devolveu (o modelo, antes da view da rota)</li>
 *   <li>{@code error}: com a exceção do handler ou de um interceptor; se responder, a resposta é a dele,
 *       senão a exceção segue para a página de erro</li>
 * </ul>
 */
class Interceptor {

    static final String BEFORE = 'before'
    static final String AFTER = 'after'
    static final String ERROR = 'error'

    final String kind
    /** Padrão do caminho ({@code /api/**}), ou null: todas as rotas do escopo. */
    final String path
    private final Pattern pattern
    final Closure closure
    final Method method

    Interceptor(String kind, String path, Closure closure, Method method = null) {
        this.kind = kind
        this.path = path ?: null
        this.pattern = this.path ? Pattern.compile(AccessRule.antToRegex(this.path)) : null
        this.closure = closure
        this.method = method
        method?.accessible = true
    }

    @groovy.transform.CompileStatic
    boolean matches(String requestPath) {
        pattern == null || pattern.matcher(requestPath).matches()
    }

    /**
     * Chama o interceptor. O closure roda com o {@link RouteContext} como delegate e recebe, se declarar um
     * parâmetro, o valor devolvido pelo handler ({@code after}), a exceção ({@code error}) ou o contexto
     * ({@code before}). O método recebe pelo tipo: {@link RouteContext}, {@link Throwable} ou o valor.
     */
    Object call(Object instance, RouteContext ctx, Object arg) {
        if (closure != null) {
            Closure c = (Closure) closure.clone()
            c.delegate = ctx
            c.resolveStrategy = Closure.DELEGATE_FIRST
            if (c.maximumNumberOfParameters == 0) return c.call()
            return c.call(kind == BEFORE ? ctx : arg)
        }
        Class<?>[] types = method.parameterTypes
        Object[] args = new Object[types.length]
        for (int i = 0; i < types.length; i++) {
            args[i] = RouteContext.isAssignableFrom(types[i]) ? ctx : arg
        }
        try {
            method.invoke(instance, args)
        } catch (InvocationTargetException e) {
            throw e.cause ?: e
        }
    }

    /** Interceptores dos métodos anotados de uma classe {@link Handler}, na ordem dos nomes. */
    static List<Interceptor> of(Class<?> handler) {
        List<Interceptor> result = []
        handler.methods.sort { it.name }.each { Method m ->
            if (m.isAnnotationPresent(Before)) result << new Interceptor(BEFORE, m.getAnnotation(Before).value(), null, m)
            if (m.isAnnotationPresent(After)) result << new Interceptor(AFTER, m.getAnnotation(After).value(), null, m)
            if (m.isAnnotationPresent(OnError)) result << new Interceptor(ERROR, m.getAnnotation(OnError).value(), null, m)
        }
        result
    }
}
