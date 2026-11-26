package org.grox.dsl

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** Método interceptor de um {@link Handler}: roda com a exceção de uma rota da classe (parâmetro {@code Throwable}); se responder, a resposta é a dele (ver {@link Interceptor}). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface OnError {
    /** Padrão Ant do caminho ({@code /api/livros/**}); vazio: todas as rotas da classe. */
    String value() default ''
}
