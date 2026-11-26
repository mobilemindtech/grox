package grails.core

import groovy.transform.CompileStatic

/** Visão mínima dos beans do Grox no estilo Spring ({@code getBean(nome)}, {@code getBean(Tipo)}). */
@CompileStatic
class ApplicationContext {
    private final Map<String, Object> beans

    ApplicationContext(Map<String, Object> beans) {
        this.beans = beans
    }

    Object getBean(String name) {
        Object b = beans[name]
        if (b == null) throw new IllegalArgumentException("Bean não encontrado: $name")
        b
    }

    public <T> T getBean(Class<T> type) {
        Object b = beans.values().find { type.isInstance(it) }
        // o bean sessionFactory do Grox delega ao SessionFactory do Hibernate (currentSession = sessão corrente)
        if (b == null && type.name == 'org.hibernate.SessionFactory') b = beans.sessionFactory
        if (b == null) throw new IllegalArgumentException("Bean não encontrado do tipo ${type.name}")
        (T) b
    }

    public <T> T getBean(String name, Class<T> type) {
        (T) getBean(name)
    }

    boolean containsBean(String name) { beans.containsKey(name) }

    /** Entrega o evento aos beans {@code ApplicationListener} do tipo dele (síncrono, como no Spring). */
    void publishEvent(org.springframework.context.ApplicationEvent event) {
        org.grox.core.ApplicationEvents.publish(beans.values(), event)
    }

    Set<String> getBeanDefinitionNames() { beans.keySet() }

    public <T> Map<String, T> getBeansOfType(Class<T> type) {
        (Map<String, T>) beans.findAll { k, v -> type.isInstance(v) }
    }
}
