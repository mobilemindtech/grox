package org.grox.dsl

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** Método de um {@link Handler} que atende {@code GET} no caminho (relativo ao do handler; {@code {id}} vira parâmetro). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Get {
    String value() default ''

    /** Regras de acesso da rota (como um {@code @Secured}); vazio: as do {@link Handler} ou as de URL. */
    String[] access() default []
}
