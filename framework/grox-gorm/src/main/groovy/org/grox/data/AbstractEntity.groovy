package org.grox.data

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Marca (posta pela AST) da classe abstrata de {@code grails-app/domain} ou com {@code @Entity}: é entidade, a raiz
 * ou um nível intermediário de uma hierarquia, como no GORM. Uma classe base abstrata fora dali (em
 * {@code src/main/groovy}) continua sendo só uma superclasse.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface AbstractEntity {
}
