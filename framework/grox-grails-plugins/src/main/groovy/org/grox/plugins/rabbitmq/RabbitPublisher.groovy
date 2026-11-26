package org.grox.plugins.rabbitmq

import com.budjb.rabbitmq.publisher.RabbitMessageProperties
import com.budjb.rabbitmq.publisher.RabbitMessagePublisher
import groovy.json.JsonOutput

import java.nio.charset.StandardCharsets

/**
 * Implementação do {@link RabbitMessagePublisher}. O Channel do amqp-client não é thread-safe: cada envio pega um
 * canal só seu de um pool pequeno por conexão e o devolve no fim (abrir e fechar um canal são duas idas ao broker).
 */
class RabbitPublisher implements RabbitMessagePublisher {

    /** Canais ociosos guardados por conexão; os excedentes são fechados. */
    static final int MAX_IDLE_CHANNELS = 16

    /** Conexões por nome; a primeira (ou a {@code isDefault}) é a padrão. */
    final Map<String, Object> connections
    final String defaultConnection
    private final Map<String, java.util.concurrent.ConcurrentLinkedDeque<Object>> idle = new java.util.concurrent.ConcurrentHashMap<>()

    RabbitPublisher(Map<String, Object> connections, String defaultConnection) {
        this.connections = connections
        this.defaultConnection = defaultConnection
    }

    @Override
    void send(RabbitMessageProperties p) {
        if (!p.exchange && !p.routingKey) throw new IllegalArgumentException('exchange ou routingKey é obrigatório')
        List converted = convert(p.body, p.autoConvert != false)
        def props = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                .contentType(p.contentType ?: (String) converted[1])
                .contentEncoding(p.contentEncoding ?: (String) converted[2])
                .headers(p.headers ?: null)
                .deliveryMode(p.deliveryMode ?: null)
                .priority(p.priority ?: null)
                .correlationId(p.correlationId)
                .replyTo(p.replyTo)
                .expiration(p.expiration)
                .messageId(p.messageId)
                .timestamp(p.timestamp ? Date.from(p.timestamp.toInstant()) : null)
                .type(p.type)
                .userId(p.userId)
                .appId(p.appId)
                .build()
        if (p.channel != null) {
            p.channel.basicPublish(p.exchange ?: '', p.routingKey ?: '', props, (byte[]) converted[0])
            return
        }
        String name = p.connection ?: defaultConnection
        def connection = connections[name]
        if (connection == null) throw new IllegalStateException("Conexão rabbitmq '${name}' inexistente ou indisponível")
        def pool = idle.computeIfAbsent(name) { new java.util.concurrent.ConcurrentLinkedDeque<Object>() }
        def channel = pool.pollFirst()
        while (channel != null && !channel.open) channel = pool.pollFirst()
        if (channel == null) channel = connection.createChannel()
        boolean published = false
        try {
            channel.basicPublish(p.exchange ?: '', p.routingKey ?: '', props, (byte[]) converted[0])
            published = true
        } finally {
            // canal com erro não volta ao pool (o broker pode tê-lo fechado)
            if (published && channel.open && pool.size() < MAX_IDLE_CHANNELS) pool.addFirst(channel)
            else if (channel.open) channel.close()
        }
    }

    @Override
    void send(Closure closure) {
        send(new RabbitMessageProperties().build(closure))
    }

    @Override
    void send(String routingKey, Object body) {
        send(new RabbitMessageProperties(routingKey: routingKey, body: body))
    }

    @Override
    void send(String exchange, String routingKey, Object body) {
        send(new RabbitMessageProperties(exchange: exchange, routingKey: routingKey, body: body))
    }

    /** @return [bytes, contentType, contentEncoding] */
    static List convert(Object body, boolean auto) {
        if (body == null) return [new byte[0], null, null]
        if (body instanceof byte[]) return [body, null, null]
        if (!auto) throw new IllegalArgumentException("autoConvert desligado: o corpo deve ser byte[] (${body.getClass().name})")
        if (body instanceof Map || body instanceof Collection) {
            return [JsonOutput.toJson(body).getBytes(StandardCharsets.UTF_8), 'application/json', 'UTF-8']
        }
        [body.toString().getBytes(StandardCharsets.UTF_8), 'text/plain', 'UTF-8']
    }
}
