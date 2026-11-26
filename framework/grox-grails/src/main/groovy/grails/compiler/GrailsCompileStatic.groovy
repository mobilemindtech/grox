package grails.compiler

import groovy.transform.AnnotationCollector
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

/**
 * Compatibilidade com {@code @GrailsCompileStatic} do Grails. No Grox a classe é compilada de forma
 * dinâmica: o Grails depende de extensões de type checking do GORM (dynamic finders, propriedades
 * das domain classes) que o Grox não tem. O comportamento é o mesmo; só não há verificação de tipos.
 */
@CompileStatic(TypeCheckingMode.SKIP)
@AnnotationCollector
@interface GrailsCompileStatic {
}
