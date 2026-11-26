package grails.async

import java.util.concurrent.TimeUnit

/** Promessa de valor (API do {@code grails.async}). */
interface Promise<T> {

    /** Bloqueia até o valor (ou relança a exceção da tarefa). */
    T get()

    T get(long timeout, TimeUnit unit)

    boolean isDone()

    /** Encadeia uma transformação (roda no mesmo contexto da tarefa). */
    Promise then(Closure callable)

    Promise<T> onComplete(Closure callable)

    Promise<T> onError(Closure callable)
}
