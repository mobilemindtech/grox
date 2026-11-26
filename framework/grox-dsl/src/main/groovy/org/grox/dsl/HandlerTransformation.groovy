package org.grox.dsl

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/** {@link Handler}: a classe implementa o trait {@link HandlerApi} (render, respond, redirect, params...). */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class HandlerTransformation implements ASTTransformation {

    private static final ClassNode HANDLER_API = ClassHelper.make(HandlerApi)

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length < 2 || !(nodes[1] instanceof ClassNode)) return
        ClassNode cn = (ClassNode) nodes[1]
        if (!cn.implementsInterface(HANDLER_API)) cn.addInterface(HANDLER_API)
    }
}
