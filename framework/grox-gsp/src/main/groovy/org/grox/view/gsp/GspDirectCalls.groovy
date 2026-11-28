package org.grox.view.gsp

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer

/**
 * Liga as chamadas geradas pelo {@link GspCompiler} ({@code GspPage.__text(this, 1)}, {@code __expr},
 * {@code __tag}, {@code __set}, {@code __capture}) direto ao método estático do {@link GspPage}: o Groovy
 * dinâmico respeita o {@code methodTarget} de uma chamada estática e emite um {@code invokestatic}, sem o call site
 * {@code invokedynamic} e os guardas dele. As expressões do usuário continuam dinâmicas.
 */
@CompileStatic
class GspDirectCalls extends CompilationCustomizer {

    private static final ClassNode PAGE = ClassHelper.make(GspPage)
    private static final Set<String> NOMES = ['__text', '__expr', '__tag', '__set', '__capture'] as Set

    GspDirectCalls() {
        super(CompilePhase.INSTRUCTION_SELECTION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        Map<String, MethodNode> alvos = [:]
        for (String nome : NOMES) {
            MethodNode m = PAGE.getMethods(nome).find { MethodNode mn -> mn.static }
            if (m != null) alvos.put(nome, m)
        }
        new ClassCodeVisitorSupport() {
            @Override
            protected SourceUnit getSourceUnit() { source }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                super.visitMethodCallExpression(call)
                if (!(call.objectExpression instanceof ClassExpression) || call.objectExpression.type.name != PAGE.name) return
                MethodNode alvo = alvos.get(call.methodAsString)
                if (alvo == null || !(call.arguments instanceof ArgumentListExpression)) return
                if (((ArgumentListExpression) call.arguments).expressions.size() != alvo.parameters.length) return
                call.methodTarget = alvo
            }
        }.visitClass(classNode)
    }
}
