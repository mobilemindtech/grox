package grails.gorm.annotation

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Domain class fora de {@code grails-app/domain}, como no GORM: a classe ganha a API do GORM, {@code id},
 * {@code version}, as associações declaradas e o construtor com Map. Ela precisa estar no pacote da
 * aplicação (ou num pacote varrido) para o datastore mapeá-la.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@GroovyASTTransformationClass('org.grox.data.ast.EntityTransformation')
@interface Entity {
}
