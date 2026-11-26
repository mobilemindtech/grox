package com.budjb.rabbitmq.publisher

/**
 * Bean {@code rabbitMessagePublisher} do plugin rabbitmq-native: publica mensagens. O corpo é
 * convertido como no plugin: {@code byte[]} como está, String em UTF-8 ({@code text/plain}), Map/List em
 * JSON ({@code application/json}), números e booleanos como texto.
 */
interface RabbitMessagePublisher {
    void send(RabbitMessageProperties properties)

    void send(@DelegatesTo(RabbitMessageProperties) Closure closure)

    void send(String routingKey, Object body)

    void send(String exchange, String routingKey, Object body)
}
