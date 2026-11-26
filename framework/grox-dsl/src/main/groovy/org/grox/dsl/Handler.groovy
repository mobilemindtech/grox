package org.grox.dsl

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Classe de rotas por anotação: o caminho base e, nos métodos, {@link Get}, {@link Post}, {@link Put},
 * {@link Patch} e {@link Delete}. A classe ganha a API de {@link HandlerApi} ({@code render}, {@code respond},
 * {@code redirect}, {@code params}...) e recebe injeção de services pelo nome, como um controller.
 * <pre>
 * &#64;Handler('/api/autores')
 * class Autores {
 *     AutorService autorService
 *     &#64;Get('/{id}') def show(Long id) { respond autorService.get(id) }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@GroovyASTTransformationClass('org.grox.dsl.HandlerTransformation')
@interface Handler {
    /** Caminho base das rotas da classe. */
    String value() default ''

    /** {@code virtual} ou {@code actor}; vazio: o padrão {@code grox.routes.execution}. */
    String execution() default ''

    /** Atores no pool com {@code execution = 'actor'}; 0: o padrão {@code grox.routes.poolSize}. */
    int poolSize() default 0

    /** Regras de acesso das rotas da classe (como um {@code @Secured}); vazio: as regras de URL. */
    String[] access() default []
}
