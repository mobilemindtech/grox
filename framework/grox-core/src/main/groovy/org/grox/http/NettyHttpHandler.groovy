package org.grox.http

import groovy.util.logging.Slf4j
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.cookie.ServerCookieDecoder
import io.netty.handler.codec.http.multipart.Attribute
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.FileUpload
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import io.netty.handler.timeout.IdleStateEvent

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * Converte requisições Netty em {@link GroxRequest}, despacha de forma assíncrona
 * e escreve as respostas na ordem de chegada (HTTP/1.1 pipelining).
 * Uma instância por conexão; estado acessado apenas no event loop. Estático: roda em toda requisição.
 */
@Slf4j
@groovy.transform.CompileStatic
class NettyHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final RequestDispatcher dispatcher
    private final NettyServer server
    private long nextSeq
    private long writeSeq
    private final TreeMap<Long, Object[]> pending = new TreeMap<>()
    /** Requisições já decodificadas além de {@code maxPipelined} em voo: esperam as respostas saírem. */
    private final ArrayDeque<FullHttpRequest> waiting = new ArrayDeque<>()

    NettyHttpHandler(RequestDispatcher dispatcher, NettyServer server = null) {
        this.dispatcher = dispatcher
        this.server = server
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        ctx.pipeline().get(RequestDeadlineHandler)?.requestComplete()
        server?.inFlight?.incrementAndGet()
        // pipelining: com maxPipelined em voo, a leitura pausa; as que o codec já decodificou do mesmo read esperam
        if (server != null && nextSeq - writeSeq >= server.maxPipelined) {
            ctx.channel().config().autoRead = false
            waiting.addLast(msg.retain())
            return
        }
        process(ctx, msg)
    }

    private void process(ChannelHandlerContext ctx, FullHttpRequest msg) {
        long seq = nextSeq++
        if (server != null && nextSeq - writeSeq >= server.maxPipelined) ctx.channel().config().autoRead = false
        boolean keepAlive = HttpUtil.isKeepAlive(msg)
        boolean head = HttpMethod.HEAD.equals(msg.method())
        if (!msg.decoderResult().success) {
            enqueue(ctx, seq, plain(HttpResponseStatus.BAD_REQUEST, 'Bad Request'), false)
            return
        }
        GroxRequest request
        try {
            request = convert(msg, ctx, server != null ? server.maxFormFields : DEFAULT_MAX_FORM_FIELDS)
        } catch (HttpPostRequestDecoder.TooManyFormFieldsException e) {
            log.debug("Campos demais em ${msg.method()} ${msg.uri()} (grox.http.maxFormFields)")
            enqueue(ctx, seq, plain(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, 'Payload Too Large'), false)
            return
        } catch (io.netty.handler.codec.DecoderException | IllegalArgumentException e) {
            // corpo malformado (multipart, formulário): erro do cliente, sem stack trace no log
            log.debug("Corpo inválido em ${msg.method()} ${msg.uri()}: ${e}")
            enqueue(ctx, seq, plain(HttpResponseStatus.BAD_REQUEST, 'Bad Request'), false)
            return
        }
        CompletableFuture<GroxResponse> future
        try {
            future = dispatcher.dispatch(request)
        } catch (Throwable t) {
            future = CompletableFuture.failedFuture(t)
        }
        future.whenComplete((GroxResponse r, Throwable e) -> {
            FullHttpResponse out
            if (e != null) {
                log.error("Erro não tratado em ${msg.method()} ${msg.uri()}", e)
                out = plain(HttpResponseStatus.INTERNAL_SERVER_ERROR, 'Internal Server Error')
            } else {
                try {
                    out = toNetty(r, head)
                } catch (Throwable t) {
                    // ex.: cabeçalho com CRLF ou status inválido; sem isto a conexão ficaria esperando para sempre
                    log.error("Resposta inválida para ${msg.method()} ${msg.uri()}: ${t}")
                    out = plain(HttpResponseStatus.INTERNAL_SERVER_ERROR, 'Internal Server Error')
                }
            }
            FullHttpResponse resposta = out
            ctx.executor().execute(() -> enqueue(ctx, seq, resposta, keepAlive))
        })
    }

    private void enqueue(ChannelHandlerContext ctx, long seq, FullHttpResponse out, boolean keepAlive) {
        pending.put(seq, [out, keepAlive] as Object[])
        while (pending.containsKey(writeSeq)) {
            Object[] entry = pending.remove(writeSeq++)
            FullHttpResponse resp = (FullHttpResponse) entry[0]
            boolean ka = (boolean) entry[1] && !server?.draining
            HttpUtil.setKeepAlive(resp, ka)
            def f = ctx.writeAndFlush(resp)
            // em andamento até a resposta ser escrita (o desligamento espera por isso)
            f.addListener { server?.inFlight?.decrementAndGet() }
            if (!ka) f.addListener(ChannelFutureListener.CLOSE)
        }
        while (!waiting.isEmpty() && (server == null || nextSeq - writeSeq < server.maxPipelined)) {
            FullHttpRequest next = waiting.pollFirst()
            try {
                process(ctx, next)
            } finally {
                next.release()
            }
        }
        if (server != null && waiting.isEmpty() && !ctx.channel().config().autoRead && nextSeq - writeSeq < server.maxPipelined) {
            ctx.channel().config().autoRead = true
        }
    }

    /** Conexão fechada com requisições esperando: libera os buffers e o contador de em andamento. */
    @Override
    void channelInactive(ChannelHandlerContext ctx) {
        while (!waiting.isEmpty()) {
            waiting.pollFirst().release()
            server?.inFlight?.decrementAndGet()
        }
        ctx.fireChannelInactive()
    }

    /** Conexão ociosa (sem tráfego e sem resposta pendente): fecha. */
    @Override
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            if (nextSeq == writeSeq) ctx.close()
            return
        }
        ctx.fireUserEventTriggered(evt)
    }

    /** Padrão de {@code grox.http.maxFormFields} (o mesmo {@code maxParameterCount} do Tomcat). */
    static final int DEFAULT_MAX_FORM_FIELDS = 1000

    static GroxRequest convert(FullHttpRequest msg, ChannelHandlerContext ctx, int maxFormFields = DEFAULT_MAX_FORM_FIELDS) {
        def decoder = new QueryStringDecoder(msg.uri())
        def headers = new HeaderMap()
        for (Map.Entry<String, String> h : msg.headers()) headers.add(h.key, h.value)
        byte[] body = ByteBufUtil.getBytes(msg.content())
        def request = new GroxRequest(
                method: msg.method().name(),
                uri: msg.uri(),
                path: decoder.path(),
                queryString: decoder.rawQuery(),
                headers: headers,
                body: body,
                queryParameters: decoder.parameters(),
                remoteAddr: ctx.channel().remoteAddress()?.toString())
        String ct = headers.getFirst('Content-Type')?.toLowerCase()
        if (ct?.startsWith('application/x-www-form-urlencoded') && body.length) {
            request.formParameters = new QueryStringDecoder(new String(body, request.charset), request.charset, false).parameters()
        } else if (ct?.startsWith('multipart/form-data') && body.length) {
            decodeMultipart(msg, request, maxFormFields)
        }
        String cookieHeader = headers.getFirst('Cookie')
        if (cookieHeader) {
            request.cookieValues = ServerCookieDecoder.LAX.decode(cookieHeader).collectEntries { [(it.name()): it.value()] }
        }
        request
    }

    /** Campos e arquivos de multipart/form-data (a requisição já está agregada em memória). */
    static void decodeMultipart(FullHttpRequest msg, GroxRequest request, int maxFields = DEFAULT_MAX_FORM_FIELDS) {
        // 1024: o padrão do Netty para o que o decoder acumula enquanto procura o fim de um campo
        def decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), msg, StandardCharsets.UTF_8, maxFields, 1024)
        try {
            Map<String, List<String>> fields = [:]
            Map<String, List<GroxMultipartFile>> files = [:]
            for (InterfaceHttpData data : decoder.bodyHttpDatas) {
                if (data instanceof FileUpload) {
                    FileUpload f = (FileUpload) data
                    files.computeIfAbsent(f.name) { [] } << new GroxMultipartFile(f.name, f.filename, f.contentType, f.get())
                } else if (data instanceof Attribute) {
                    Attribute a = (Attribute) data
                    fields.computeIfAbsent(a.name) { [] } << a.value
                }
            }
            request.formParameters = fields
            request.files = files
        } finally {
            decoder.destroy()
        }
    }

    static FullHttpResponse toNetty(GroxResponse r, boolean head) {
        // o array do corpo embrulhado direto (antes: toByteArray, uma cópia a mais por resposta)
        int length = r.size()
        def out = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(r.status),
                head || length == 0 ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(r.bodyArray(), 0, length))
        r.headers.each { String k, List<String> vs -> out.headers().add(k, vs) }
        String ct = r.contentTypeHeader
        if (ct && !out.headers().contains(HttpHeaderNames.CONTENT_TYPE)) out.headers().set(HttpHeaderNames.CONTENT_TYPE, ct)
        HttpUtil.setContentLength(out, length)
        out
    }

    static FullHttpResponse plain(HttpResponseStatus status, String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8)
        def out = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(b))
        out.headers().set(HttpHeaderNames.CONTENT_TYPE, 'text/plain;charset=UTF-8')
        HttpUtil.setContentLength(out, b.length)
        out
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug('Erro na conexão', cause)
        ctx.close()
    }
}
