package grails.events.annotation.gorm

import java.lang.annotation.*

/**
 * Marca um método de bean como listener de eventos de persistência
 * ({@code PreInsertEvent}, {@code PreUpdateEvent}...), opcionalmente restrito às classes indicadas.
 * O tipo do evento vem do parâmetro do método.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Listener {
    Class[] value() default []
}
