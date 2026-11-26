package org.grox.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.timeout.IdleStateHandler

import java.util.concurrent.TimeUnit

class NettyServer implements AutoCloseable {

    final String host
    final int requestedPort
    final int maxContentLength
    final int workerThreads
    final RequestDispatcher dispatcher
    private EventLoopGroup boss
    private EventLoopGroup workers
    private Channel channel
    /** Desligando: novas respostas saem com Connection: close. */
    volatile boolean draining
    /** Requisições lidas cuja resposta ainda não foi escrita. */
    final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger()
    /** Conexões abertas. */
    final java.util.concurrent.atomic.AtomicInteger connections = new java.util.concurrent.atomic.AtomicInteger()

    /** {@code grox.http.requestTimeout}: segundos para receber uma requisição inteira (0 desliga). */
    int requestTimeoutSeconds = 30
    /** {@code grox.http.idleTimeout}: segundos sem tráfego e sem resposta pendente até fechar (0 desliga). */
    int idleTimeoutSeconds = 60
    /** {@code grox.http.maxConnections}: acima disso, novas conexões são fechadas (0 sem limite). */
    int maxConnections = 10_000
    /** {@code grox.http.maxPipelined}: requisições em voo por conexão antes de pausar a leitura. */
    int maxPipelined = 16
    /** {@code grox.http.maxFormFields}: campos de um multipart/form-data; acima disso, 413. */
    int maxFormFields = NettyHttpHandler.DEFAULT_MAX_FORM_FIELDS

    NettyServer(String host, int port, int maxContentLength, int workerThreads, RequestDispatcher dispatcher) {
        this.host = host
        this.requestedPort = port
        this.maxContentLength = maxContentLength
        this.workerThreads = workerThreads
        this.dispatcher = dispatcher
    }

    NettyServer start() {
        boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        workers = new MultiThreadIoEventLoopGroup(workerThreads, NioIoHandler.newFactory())
        int maxContent = maxContentLength
        RequestDispatcher d = dispatcher
        NettyServer server = this
        long deadline = requestTimeoutSeconds * 1000L
        int idle = idleTimeoutSeconds
        int maxConn = maxConnections
        def bootstrap = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (maxConn > 0 && server.connections.incrementAndGet() > maxConn) {
                            server.connections.decrementAndGet()
                            ch.close()
                            return
                        }
                        if (maxConn > 0) ch.closeFuture().addListener { server.connections.decrementAndGet() }
                        ch.pipeline().addLast('deadline', new RequestDeadlineHandler(deadline))
                        if (idle > 0) ch.pipeline().addLast('idle', new IdleStateHandler(0, 0, idle))
                        // o aggregator responde ao Expect: 100-continue (ou 413, se o corpo declarado passar do limite)
                        ch.pipeline()
                                .addLast('codec', new HttpServerCodec())
                                .addLast('aggregator', new HttpObjectAggregator(maxContent))
                                .addLast('grox', new NettyHttpHandler(d, server))
                    }
                })
        channel = bootstrap.bind(host, requestedPort).sync().channel()
        this
    }

    int getPort() {
        ((InetSocketAddress) channel.localAddress()).port
    }

    /** Para de aceitar conexões (as abertas continuam sendo atendidas). */
    void stopAccepting() {
        draining = true
        channel?.close()?.syncUninterruptibly()
    }

    @Override
    void close() {
        channel?.close()?.syncUninterruptibly()
        boss?.shutdownGracefully(0, 2, TimeUnit.SECONDS)
        // período de silêncio: escritas de respostas já prontas ainda são executadas
        workers?.shutdownGracefully(draining ? 200 : 0, 3000, TimeUnit.MILLISECONDS)?.syncUninterruptibly()
    }
}
