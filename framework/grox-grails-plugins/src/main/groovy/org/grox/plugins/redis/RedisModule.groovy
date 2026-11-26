package org.grox.plugins.redis

import org.grox.core.GroxModule

/** Plugin redis ({@code grails.redis}): beans {@code redisService}..., fechados no fim. */
class RedisModule extends GroxModule {

    Map<String, Object> redisBeans = [:]

    int getOrder() { 610 }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        redisBeans = RedisPlugin.beans(config.getProperty('grails.redis'), runtime.application.classLoader)
        beans.putAll(redisBeans)
    }

    @Override
    void release(Shutdown shutdown) {
        if (!shutdown.reload && shutdown.generation) RedisPlugin.close(redisBeans)
    }
}
