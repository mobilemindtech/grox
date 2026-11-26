package org.grox.dsl

import org.grox.async.ActorPool
import org.grox.data.binding.DataBinder

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/** Uma rota: método HTTP, caminho e o handler (closure ou método de um {@link Handler}). */
class Route {

    RouteGroup group
    /** Nome da rota no grupo (o "action" nos links): o do método, a opção {@code name:} ou gerado. */
    String action
    String httpMethod
    /** Caminho completo como declarado ({@code /livros/{id}}). */
    String path
    /** Handler por closure: roda com o {@link RouteContext} como delegate e o owner da declaração. */
    Closure closure
    /** Handler por anotação. */
    Method method
    /** {@code virtual} ou {@code actor}. */
    String execution
    int poolSize
    /** {@code view:} da rota: um Map devolvido pelo handler (sem {@code render}) vira o modelo dessa view. */
    String view
    /** {@code access:} da rota ou do grupo: vale como um {@code @Secured}, antes das regras de URL. */
    List<String> access
    /** Instância para {@code virtual} (closures: a classe de rotas; anotação: uma instância compartilhada). */
    Object instance
    /** Atores da rota com {@code execution: 'actor'} (cada um com a sua instância da classe anotada). */
    ActorPool<RouteContext> pool
    /** Escopo de declaração na DSL de rotas (null nas rotas por anotação). */
    RouteBuilder.Scope scope
    /** Interceptores da rota, do mais externo para o mais interno (montados na subida pelo {@link DslModule}). */
    List<Interceptor> befores = []
    List<Interceptor> afters = []
    List<Interceptor> errors = []

    boolean isActor() { execution == 'actor' }

    /** Separa os interceptores por tipo; os {@code after} e {@code error} rodam do mais interno para o mais externo. */
    void setInterceptors(List<Interceptor> all) {
        befores = all.findAll { it.kind == Interceptor.BEFORE }
        afters = all.findAll { it.kind == Interceptor.AFTER }.reverse()
        errors = all.findAll { it.kind == Interceptor.ERROR }.reverse()
    }

    /**
     * Os {@code before}, o handler e os {@code after}; numa exceção, os {@code error}. Devolve o valor do handler
     * (o modelo da {@code view:} da rota), ou null se um {@code before} interrompeu ou um {@code error} respondeu.
     */
    @groovy.transform.CompileStatic
    Object execute(Object target, RouteContext ctx) {
        if (befores.empty && afters.empty && errors.empty) return invoke(target, ctx)
        String path = ctx.request.path
        try {
            for (Interceptor i : befores) {
                if (!i.matches(path)) continue
                Object r = i.call(target, ctx, null)
                if (r == Boolean.FALSE || ctx.rendered) return null
            }
            Object result = invoke(target, ctx)
            for (Interceptor i : afters) {
                if (i.matches(path)) i.call(target, ctx, result)
            }
            return result
        } catch (Throwable t) {
            for (Interceptor i : errors) {
                if (!i.matches(path)) continue
                // a resposta parcial do handler não vale: o interceptor responde do zero
                ctx.response.reset()
                ctx.rendered = false
                i.call(target, ctx, t)
                if (ctx.rendered) return null
            }
            throw t
        }
    }

    /** Chama o handler com a requisição já vinculada (sessão de dados, autenticação). */
    Object invoke(Object target, RouteContext ctx) {
        if (closure != null) {
            Closure c = (Closure) closure.clone()
            c.delegate = ctx
            c.resolveStrategy = Closure.DELEGATE_FIRST
            return c.maximumNumberOfParameters == 0 ? c.call() : c.call(ctx)
        }
        try {
            method.invoke(target, arguments(ctx))
        } catch (InvocationTargetException e) {
            throw e.cause ?: e
        }
    }

    /**
     * Argumentos pelo nome dos parâmetros (compile com {@code groovyOptions.parameters = true}):
     * {@link RouteContext}, {@code Map params}, valores simples convertidos dos parâmetros e objetos
     * (domain classes, command objects) preenchidos por binding.
     */
    private Object[] arguments(RouteContext ctx) {
        Parameter[] ps = method.parameters
        Object[] args = new Object[ps.length]
        for (int i = 0; i < ps.length; i++) {
            Parameter p = ps[i]
            Class<?> t = p.type
            if (RouteContext.isAssignableFrom(t)) { args[i] = ctx; continue }
            if (!p.namePresent) {
                throw new IllegalStateException("${method.declaringClass.simpleName}.${method.name}: compile com os nomes dos parâmetros (groovyOptions.parameters = true)")
            }
            if (Map.isAssignableFrom(t) && p.name == 'params') { args[i] = ctx.params; continue }
            Object raw = ctx.params.get(p.name)
            if (DataBinder.isSimple(t)) {
                args[i] = raw == null ? DataBinder.primitiveDefault(t) : DataBinder.bindValue(raw, t)
            } else {
                args[i] = DataBinder.bind(t.getDeclaredConstructor().newInstance(), ctx.params)
            }
        }
        args
    }
}
