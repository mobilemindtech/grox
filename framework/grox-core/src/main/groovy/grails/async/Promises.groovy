package grails.async

import org.grox.async.AsyncSupport
import org.grox.async.GroxPromise

/**
 * {@code import static grails.async.Promises.*}: {@code task { }}, {@code tasks(...)},
 * {@code waitAll(...)}, {@code onComplete(...)}, {@code createBoundPromise(v)}.
 *
 * As tarefas rodam em virtual threads com o contexto de quem as criou (requisição, usuário,
 * tenant) e uma sessão de dados própria.
 */
class Promises {

    static <T> Promise<T> task(Closure<T> body) {
        AsyncSupport.task(body)
    }

    /** Várias tarefas em paralelo: Promise da lista de resultados (na ordem). */
    static Promise<List> tasks(Closure... bodies) {
        tasks(bodies.toList())
    }

    static Promise<List> tasks(List<Closure> bodies) {
        GroxPromise.all(bodies.collect { AsyncSupport.task(it) })
    }

    /** {@code tasks(a: { }, b: { })}: Promise do Map de resultados. */
    static Promise<Map> tasks(Map<String, Closure> bodies) {
        List<String> keys = bodies.keySet().toList()
        GroxPromise.all(keys.collect { AsyncSupport.task(bodies[it]) }).then { List values ->
            Map result = [:]
            keys.eachWithIndex { String k, int i -> result[k] = values[i] }
            result
        } as Promise<Map>
    }

    static List waitAll(Promise... promises) {
        waitAll(promises.toList())
    }

    static List waitAll(List<Promise> promises) {
        promises.collect { it.get() }
    }

    static Promise<List> onComplete(List<Promise> promises, Closure callable) {
        GroxPromise.all(promises.collect { (GroxPromise) it }).onComplete(callable)
    }

    static <T> Promise<T> createBoundPromise(T value) {
        GroxPromise.completed(value)
    }

    static <T> Promise<T> createPromise() {
        new GroxPromise<T>(new java.util.concurrent.CompletableFuture<T>())
    }
}
