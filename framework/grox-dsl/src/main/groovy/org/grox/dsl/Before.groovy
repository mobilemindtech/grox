package org.grox.dsl

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** Método interceptor de um {@link Handler}: roda antes de cada rota da classe; devolver {@code false} ou responder interrompe a rota (ver {@link Interceptor}). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Before {
    /** Padrão Ant do caminho ({@code /api/livros/**}); vazio: todas as rotas da classe. */
    String value() default ''
}
