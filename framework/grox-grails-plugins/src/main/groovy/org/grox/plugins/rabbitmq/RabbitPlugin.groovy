package org.grox.plugins.rabbitmq

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Plugin rabbitmq-native no Grox (subconjunto): com {@code rabbitmq.connections} (ou {@code rabbitmq.connection})
 * configurado e o amqp-client no classpath, abre as conexões, declara {@code rabbitmq.exchanges} e
 * {@code rabbitmq.queues} (com {@code exchange}/{@code binding}) e cria o bean {@code rabbitMessagePublisher}.
 * Consumers ({@code grails-app/rabbit-consumers}) e RPC ainda não são suportados. {@code rabbitmq.enabled: false}
 * desliga. Sem o broker, a aplicação sobe e o envio falha.
 */
class RabbitPlugin {

    private static final Logger log = LoggerFactory.getLogger(RabbitPlugin)
    /** Chaves aceitas em cada fila/exchange (as outras são ignoradas). */
    static final Set<String> QUEUE_KEYS = ['name', 'durable', 'autoDelete', 'exclusive', 'arguments', 'exchange', 'binding', 'match', 'connection'] as Set

    final Map<String, Object> connections = [:]
    RabbitPublisher publisher

    /** null se não configurado ou desligado. */
    static RabbitPlugin start(Object config, ClassLoader loader) {
        if (!(config instanceof Map) || ((Map) config).enabled == false) return null
        Map c = (Map) config
        List<Map> defs = c.connections instanceof Collection ? (List<Map>) c.connections : (c.connection instanceof Map ? [(Map) c.connection] : [])
        if (!defs) return null
        try {
            Class.forName('com.rabbitmq.client.ConnectionFactory', false, loader)
        } catch (ClassNotFoundException ignored) {
            log.warn('rabbitmq configurado, mas o amqp-client não está no classpath: plugin rabbitmq desativado')
            return null
        }
        def plugin = new RabbitPlugin()
        String defaultName = (defs.find { it.isDefault } ?: defs[0]).name?.toString() ?: 'default'
        defs.each { Map d ->
            String name = d.name?.toString() ?: 'default'
            try {
                plugin.connections[name] = connect(d, name)
            } catch (Exception e) {
                log.error("rabbitmq: falha ao conectar em ${d.host ?: 'localhost'}:${d.port ?: 5672} (${name}): ${e.message}")
            }
        }
        plugin.declare((List<Map>) (c.exchanges ?: []), (List<Map>) (c.queues ?: []), defaultName)
        plugin.publisher = new RabbitPublisher(plugin.connections, defaultName)
        log.info("Plugin rabbitmq: ${plugin.connections.keySet()} conectada(s) de ${defs.size()}")
        plugin
    }

    private static Object connect(Map d, String name) {
        def factory = new com.rabbitmq.client.ConnectionFactory()
        factory.host = (d.host ?: 'localhost').toString()
        factory.port = (d.port ?: (d.ssl ? 5671 : 5672)) as int
        if (d.username) factory.username = d.username.toString()
        if (d.password) factory.password = d.password.toString()
        factory.virtualHost = (d.virtualHost ?: '/').toString()
        if (d.requestedHeartbeat) factory.requestedHeartbeat = d.requestedHeartbeat as int
        factory.automaticRecoveryEnabled = d.automaticReconnect != false
        if (d.ssl) {
            // useSslProtocol() sem argumentos confia em qualquer certificado (MITM captura usuário, senha e mensagens):
            // valida a cadeia pelo truststore da JVM (javax.net.ssl.trustStore) e o nome do host
            factory.useSslProtocol(javax.net.ssl.SSLContext.getDefault())
            factory.enableHostnameVerification()
        }
        factory.newConnection("grox-${name}".toString())
    }

    void declare(List<Map> exchanges, List<Map> queues, String defaultName) {
        exchanges.each { Map e ->
            withChannel(e.connection ?: defaultName) { ch ->
                ch.exchangeDeclare(e.name.toString(), (e.type ?: 'direct').toString().toLowerCase(), (e.durable ?: false) as boolean,
                        (e.autoDelete ?: false) as boolean, (Map) (e.arguments ?: [:]))
            }
        }
        queues.each { Map q ->
            (q.keySet() - QUEUE_KEYS).each { k -> log.debug("rabbitmq.queues[${q.name}].${k}: ignorado") }
            withChannel(q.connection ?: defaultName) { ch ->
                ch.queueDeclare(q.name.toString(), (q.durable ?: false) as boolean, (q.exclusive ?: false) as boolean,
                        (q.autoDelete ?: false) as boolean, (Map) (q.arguments ?: [:]))
            }
            if (q.exchange) withChannel(q.connection ?: defaultName) { ch ->
                def b = q.binding
                if (b instanceof Map) ch.queueBind(q.name.toString(), q.exchange.toString(), '', ((Map) b) + ['x-match': (q.match ?: 'all').toString().toLowerCase()])
                else (b instanceof Collection ? b : [b ?: '']).each { ch.queueBind(q.name.toString(), q.exchange.toString(), it.toString()) }
            }
        }
    }

    /** Um canal por operação: erro de declaração (ex.: exchange inexistente) fecha o canal, não a conexão. */
    private void withChannel(Object connectionName, Closure body) {
        def connection = connections[connectionName.toString()]
        if (connection == null) return
        def ch = connection.createChannel()
        try {
            body(ch)
        } catch (Exception e) {
            log.error("rabbitmq: ${e.cause?.message ?: e.message}")
        } finally {
            if (ch.open) ch.close()
        }
    }

    void close() {
        connections.values().each { try { it.close() } catch (Exception ignored) {} }
        connections.clear()
    }
}
