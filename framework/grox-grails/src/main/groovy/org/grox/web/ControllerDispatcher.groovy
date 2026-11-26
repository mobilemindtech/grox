package org.grox.web

import groovy.concurrent.Awaitable
import org.grox.http.GroxResponse

/** submit devolve a GroxResponse ou, para actions assíncronas, um CompletableFuture dela. */
interface ControllerDispatcher extends AutoCloseable {
    Awaitable<Object> submit(Invocation invocation)
}
