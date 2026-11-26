package org.springframework.beans.factory.annotation

import java.lang.annotation.*

/** Compatibilidade: no Grox a injeção é sempre por nome de propriedade; a anotação é só marcador. */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER])
@interface Autowired {
    boolean required() default true
}
