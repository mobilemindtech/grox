package org.grails.datastore.mapping.engine.event

import org.grails.datastore.mapping.core.Datastore
import org.springframework.context.ApplicationEvent

/**
 * Compatibilidade com o listener de persistência do GORM (ex.: o {@code UserPasswordEncoderListener}
 * gerado pelo s2-quickstart). Beans do {@code resources.groovy} e services que o estendem são
 * registrados no {@code org.grox.data.GormEvents} e recebem os eventos que {@link #supportsEventType} aceitar.
 */
abstract class AbstractPersistenceEventListener {

    final Datastore datastore

    protected AbstractPersistenceEventListener(Datastore datastore) {
        this.datastore = datastore
    }

    protected abstract void onPersistenceEvent(AbstractPersistenceEvent event)

    abstract boolean supportsEventType(Class<? extends ApplicationEvent> eventType)

    /** Chamado pelo Grox (a Spring chama {@code onApplicationEvent}). */
    void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof AbstractPersistenceEvent) onPersistenceEvent((AbstractPersistenceEvent) event)
    }
}
