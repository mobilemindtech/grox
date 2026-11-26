package org.grox.dsl

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** Método interceptor de um {@link Handler}: roda depois de cada rota da classe; recebe (se declarar) o valor devolvido pela rota (ver {@link Interceptor}). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface After {
    /** Padrão Ant do caminho ({@code /api/livros/**}); vazio: todas as rotas da classe. */
    String value() default ''
}
