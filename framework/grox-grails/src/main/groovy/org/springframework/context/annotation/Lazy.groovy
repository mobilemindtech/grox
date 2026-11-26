package org.springframework.context.annotation

import java.lang.annotation.*

/** Compatibilidade: sem efeito no Grox (os beans já existem quando a injeção ocorre). */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER, ElementType.CONSTRUCTOR])
@interface Lazy {
    boolean value() default true
}
