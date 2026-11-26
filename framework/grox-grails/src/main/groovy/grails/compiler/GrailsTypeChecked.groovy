package grails.compiler

import groovy.transform.AnnotationCollector
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

/** Compatibilidade com {@code @GrailsTypeChecked}: como {@link GrailsCompileStatic}, compila de forma dinâmica. */
@CompileStatic(TypeCheckingMode.SKIP)
@AnnotationCollector
@interface GrailsTypeChecked {
}
