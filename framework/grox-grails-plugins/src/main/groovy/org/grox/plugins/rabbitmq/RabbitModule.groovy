package org.grox.plugins.rabbitmq

import groovy.util.logging.Slf4j
import org.grox.core.ClassUtils
import org.grox.core.GroxModule

/** Plugin rabbitmq-native ({@code rabbitmq.connections}): bean {@code rabbitMessagePublisher}, fechado no fim. */
@Slf4j
class RabbitModule extends GroxModule {

    RabbitPlugin rabbit

    int getOrder() { 620 }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        // rabbitmq.enabled também por variável de ambiente (RABBITMQ_ENABLED=false) ou -Drabbitmq.enabled
        rabbit = config.getProperty('rabbitmq.enabled', Boolean, true) ?
                RabbitPlugin.start(config.get('rabbitmq'), runtime.application.classLoader) : null
        if (rabbit) beans.rabbitMessagePublisher = rabbit.publisher
        warnLegacyRabbit()
    }

    /** {@code static rabbitQueue} (plugin rabbitmq antigo) não consome no rabbitmq-native nem no Grox. */
    private void warnLegacyRabbit() {
        runtime.application.getArtefacts('Service').findAll { ClassUtils.staticProperty(it, 'rabbitQueue') != null }.each {
            log.warn("${it.simpleName}: static rabbitQueue é do plugin rabbitmq antigo; ignorado (como no rabbitmq-native)")
        }
    }

    @Override
    void release(Shutdown shutdown) {
        // também na recarga: cada geração cria os seus no contributeBeans (antes, os da anterior ficavam abertos)
        if (shutdown.generation) rabbit?.close()
    }
}
