package org.grox.data.ast

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grox.data.GormEntity

/**
 * Chamada direta ({@code invokestatic}) aos {@code $init$} dos traits do Grox no construtor das domains.
 * <p>O Groovy insere {@code GormEntity$Trait$Helper.$init$(this)} em cada construtor; numa classe dinâmica, é um
 * call site {@code invokedynamic} guardado pelo tipo de {@code this}. O Hibernate instancia a domain e o proxy dela
 * (uma subclasse) pelo mesmo construtor, e o call site religava a cada troca (1,2% da {@code /usuario/index}).
 * Roda depois da composição dos traits ({@code CANONICALIZATION}) e antes da geração do bytecode.
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
class TraitInitTransformation implements ASTTransformation {

    private static final ClassNode GORM_ENTITY = ClassHelper.make(GormEntity)

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        for (ClassNode cn : source.AST.classes) {
            if (!cn.isInterface() && cn.implementsInterface(GORM_ENTITY)) cn.objectInitializerStatements.each { Statement s -> bind(s) }
        }
    }

    private static void bind(Statement s) {
        if (s instanceof BlockStatement) {
            ((BlockStatement) s).statements.each { Statement c -> bind(c) }
            return
        }
        if (!(s instanceof ExpressionStatement)) return
        Expression e = ((ExpressionStatement) s).expression
        if (!(e instanceof MethodCallExpression)) return
        MethodCallExpression call = (MethodCallExpression) e
        if (call.methodAsString != '$init$' || !(call.objectExpression instanceof ClassExpression)) return
        ClassNode helper = call.objectExpression.type
        if (!helper.name.startsWith('org.grox.')) return
        MethodNode init = helper.getMethods('$init$').find { MethodNode m -> m.static && m.parameters.length == 1 }
        if (init != null) call.methodTarget = init
    }
}
