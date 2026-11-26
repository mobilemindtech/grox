package grails.gorm.services

import java.lang.annotation.*

/**
 * GORM Data Service: interface em {@code grails-app/services} cujos métodos
 * ({@code get}, {@code list}, {@code count}, {@code save}, {@code delete}, {@code findBy*}...)
 * são implementados pelo Grox sobre a domain class indicada. Ver {@link org.grox.data.DataServices}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Service {
    Class value() default Object
    String name() default ''
}
