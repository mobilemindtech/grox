package org.grox.data.ast

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/** {@code @grails.gorm.annotation.Entity}: domain class fora de {@code grails-app/domain} ({@link DomainClassSupport}). */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class EntityTransformation implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length > 1 && nodes[1] instanceof ClassNode) DomainClassSupport.apply((ClassNode) nodes[1])
    }
}
