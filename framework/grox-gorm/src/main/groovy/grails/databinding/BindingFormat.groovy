package grails.databinding

import java.lang.annotation.*

/** Formato usado pelo data binding para a propriedade (ex.: {@code @BindingFormat('dd/MM/yyyy')} em Date). */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.PARAMETER])
@interface BindingFormat {
    String value() default ''
    String code() default ''
}
