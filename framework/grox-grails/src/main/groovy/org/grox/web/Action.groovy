package org.grox.web

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/** Marca uma action de controller; adicionada pela transformação AST com os nomes dos parâmetros. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Action {
    String[] parameterNames() default []
}
