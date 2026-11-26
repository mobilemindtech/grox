package org.grox.http

import java.util.concurrent.CompletableFuture

interface RequestDispatcher {
    CompletableFuture<GroxResponse> dispatch(GroxRequest request)
}
