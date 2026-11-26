package org.springframework.context

/**
 * Compatibilidade com o {@code ApplicationListener} do Spring: beans (services, resources.groovy) que o
 * implementam recebem os eventos publicados por {@code applicationContext.publishEvent} e pelo Grox
 * (por exemplo {@code RestTokenCreationEvent}), filtrados pelo tipo genérico.
 */
interface ApplicationListener<E extends ApplicationEvent> {
    void onApplicationEvent(E event)
}
