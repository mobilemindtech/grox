package org.grox.http

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.concurrent.ScheduledFuture

import java.util.concurrent.TimeUnit

/**
 * Prazo para receber uma requisição inteira (contra slowloris): conta a partir do primeiro byte
 * e fecha a conexão se o {@link NettyHttpHandler} não recebê-la completa a tempo. Um
 * {@code IdleStateHandler} não basta: enviar um byte de vez em quando o mantém satisfeito.
 * Fica antes do codec; estado acessado só no event loop.
 */
@Slf4j
@CompileStatic
class RequestDeadlineHandler extends ChannelInboundHandlerAdapter {

    final long timeoutMillis
    private ScheduledFuture<?> timer

    RequestDeadlineHandler(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis
    }

    @Override
    void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (timer == null && timeoutMillis > 0) {
            timer = ctx.executor().schedule({
                log.debug("Requisição incompleta após ${timeoutMillis} ms: fechando ${ctx.channel().remoteAddress()}")
                ctx.close()
            } as Runnable, timeoutMillis, TimeUnit.MILLISECONDS)
        }
        ctx.fireChannelRead(msg)
    }

    /** A requisição chegou inteira. */
    void requestComplete() {
        timer?.cancel(false)
        timer = null
    }

    @Override
    void channelInactive(ChannelHandlerContext ctx) {
        requestComplete()
        ctx.fireChannelInactive()
    }
}
