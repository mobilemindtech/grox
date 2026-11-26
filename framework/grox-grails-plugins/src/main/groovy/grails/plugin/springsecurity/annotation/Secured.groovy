package grails.plugin.springsecurity.annotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** {@code @Secured('ROLE_ADMIN')} / {@code @Secured(['ROLE_A', 'ROLE_B'])} em controllers e actions. */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@interface Secured {
    String[] value()
    String httpMethod() default 'ANY'
}
