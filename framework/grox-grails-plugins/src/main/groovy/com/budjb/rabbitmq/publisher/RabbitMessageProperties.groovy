package com.budjb.rabbitmq.publisher

import java.time.OffsetDateTime

/** Propriedades de uma mensagem do plugin rabbitmq-native ({@code rabbitMessagePublisher.send(props)}). */
class RabbitMessageProperties {
    static final int DEFAULT_TIMEOUT = 5000

    String routingKey = ''
    String exchange = ''
    Integer timeout = DEFAULT_TIMEOUT
    Object body
    Map headers = [:]
    String contentType
    String contentEncoding
    Integer deliveryMode = 0
    Integer priority = 0
    String correlationId
    String replyTo
    String expiration
    String messageId
    OffsetDateTime timestamp
    String type
    String userId
    String appId
    Boolean autoConvert = true
    String connection
    Object channel

    /** Aplica um bloco de configuração ({@code send { routingKey = 'x'; body = [...] }}). */
    RabbitMessageProperties build(Closure closure) {
        Closure c = (Closure) closure.clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = this
        c.call()
        this
    }
}
