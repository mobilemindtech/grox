package org.grox

import com.budjb.rabbitmq.publisher.RabbitMessageProperties
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import org.grox.plugins.rabbitmq.RabbitPlugin
import org.grox.plugins.rabbitmq.RabbitPublisher
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

/** Plugin rabbitmq-native (publisher e declaração de filas). */
class RabbitPluginTest {

    @Test
    void 'publisher converte o corpo e repassa as propriedades'() {
        List enviados = []
        def channel = [basicPublish: { String ex, String rk, AMQP.BasicProperties props, byte[] body -> enviados << [ex, rk, props, body] }] as Channel
        def publisher = new RabbitPublisher([:], 'default')
        publisher.send(new RabbitMessageProperties(exchange: 'app.desk', routingKey: 'tenant-1', expiration: '60000',
                body: '{"a":1}'.getBytes('UTF-8'), channel: channel))
        publisher.send { exchange = 'app'; routingKey = 'app.alertas'; body = [tipo: 'x', n: 2]; headers = [origem: 'teste']; delegate.channel = channel }
        publisher.send(new RabbitMessageProperties(routingKey: 'fila', body: 'olá', channel: channel))

        assert enviados[0][0..1] == ['app.desk', 'tenant-1'] && enviados[0][2].expiration == '60000' && enviados[0][2].contentType == null
        assert new String((byte[]) enviados[0][3], StandardCharsets.UTF_8) == '{"a":1}'
        assert enviados[1][2].contentType == 'application/json' && enviados[1][2].headers == [origem: 'teste']
        assert new String((byte[]) enviados[1][3], StandardCharsets.UTF_8) == '{"tipo":"x","n":2}'
        assert enviados[2][0] == '' && enviados[2][2].contentType == 'text/plain' && new String((byte[]) enviados[2][3], 'UTF-8') == 'olá'
        assert RabbitPublisher.convert(null, true)[0] == new byte[0]
    }

    @Test
    void 'sem configuracao ou com enabled false o plugin fica desligado'() {
        assert RabbitPlugin.start(null, getClass().classLoader) == null
        assert RabbitPlugin.start([enabled: false, connections: [[host: 'x']]], getClass().classLoader) == null
    }

    @Test
    void 'contra um broker local: declara fila com binding e publica'() {
        Assumptions.assumeTrue(brokerDisponivel(), 'RabbitMQ local (guest) indisponível')
        String fila = "grox.teste.${System.nanoTime()}"
        def plugin = RabbitPlugin.start([connections: [[name: 'defaultConnection', host: 'localhost']],
                                         exchanges  : [[name: 'grox.teste', type: 'direct', autoDelete: true]],
                                         queues     : [[name: fila, type: 'direct', autoDelete: true, exchange: 'grox.teste', binding: 'chave']]],
                getClass().classLoader)
        try {
            plugin.publisher.send('grox.teste', 'chave', [ok: true])
            def ch = plugin.connections.defaultConnection.createChannel()
            def msg = null
            for (int i = 0; i < 50 && msg == null; i++) {
                msg = ch.basicGet(fila, true)
                if (msg == null) Thread.sleep(20)
            }
            assert msg != null && new String(msg.body, 'UTF-8') == '{"ok":true}' && msg.props.contentType == 'application/json'
            ch.close()
        } finally {
            plugin.close()
        }
    }

    static boolean brokerDisponivel() {
        try {
            new ConnectionFactory(host: 'localhost', connectionTimeout: 1000).newConnection().close()
            true
        } catch (Exception ignored) {
            false
        }
    }
}
