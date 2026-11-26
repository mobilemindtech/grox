package org.springframework.context

/** Compatibilidade com o {@code ApplicationEvent} do Spring (base dos eventos de persistência do GORM). */
abstract class ApplicationEvent extends EventObject {

    final long timestamp = System.currentTimeMillis()

    ApplicationEvent(Object source) {
        super(source)
    }
}
