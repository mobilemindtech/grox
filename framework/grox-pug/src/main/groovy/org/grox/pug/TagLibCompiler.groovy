package org.grox.pug

import de.neuland.pug4j.PugEngine
import de.neuland.pug4j.RenderContext
import de.neuland.pug4j.compiler.Compiler
import de.neuland.pug4j.compiler.IndentWriter
import de.neuland.pug4j.exceptions.ExpressionException
import de.neuland.pug4j.exceptions.PugCompilerException
import de.neuland.pug4j.model.PugModel
import de.neuland.pug4j.parser.node.Attr
import de.neuland.pug4j.parser.node.ExpressionNode
import de.neuland.pug4j.parser.node.ExpressionString
import de.neuland.pug4j.parser.node.Node
import de.neuland.pug4j.parser.node.TagNode
import de.neuland.pug4j.template.PugTemplate
import org.grox.http.SafeHtml
import org.grox.view.gsp.GspRenderContext

/**
 * O compilador do pug4j com as TagLibs: um elemento {@code ns:tag} cujo namespace e tag existem no registro
 * ({@code g:link}, {@code g:form}, {@code sec:ifLoggedIn}, as {@code *TagLib} da aplicação) chama a tag em vez de
 * gerar o elemento. Os atributos (e o {@code &attributes}) viram o {@code attrs}, e o bloco aninhado é o
 * {@code body}: a tag decide se e quantas vezes renderizá-lo, e o argumento do {@code body(x)} fica visível no
 * bloco como {@code it} (ou as chaves, se for um Map), como na GSP. A saída da tag não é escapada.
 *
 * <pre>
 * g:form(action='save')
 *   g:textField(name='titulo' value=livro.titulo)
 * sec:ifLoggedIn
 *   g:link(controller='livros' action='show' id=livro.id) #{livro.titulo}
 * </pre>
 */
class TagLibCompiler extends Compiler {

    private final PugEngine engine
    private final GspRenderContext rc

    TagLibCompiler(PugTemplate template, RenderContext context, PugEngine engine, GspRenderContext rc) {
        super(template, context, engine)
        this.engine = engine
        this.rc = rc
    }

    /**
     * {@code #{x}} e {@code p= x} escapam o valor, mas uma tag chamada como método ({@code message(...)},
     * {@code g.link(...)}) já devolve {@link SafeHtml} escapado: vai como está, como na GSP. O valor é avaliado uma
     * vez só; nos outros casos ele é entregue ao pug4j, que segue com o escape dele.
     */
    @Override
    void visit(ExpressionNode node, IndentWriter writer, PugModel model) {
        def handler = engine.expressionHandler
        if (!node.buffer || !node.escape || node.hasBlock() || !(handler instanceof GroovyExpressionHandler)) {
            super.visit(node, writer, model)
            return
        }
        Object value
        try {
            value = handler.evaluateExpression(node.value, model)
        } catch (ExpressionException ignored) {
            super.visit(node, writer, model)   // o pug4j reporta o erro com a linha do template
            return
        }
        if (value instanceof SafeHtml) {
            writer.append(value.toString())
            return
        }
        GroovyExpressionHandler groovy = (GroovyExpressionHandler) handler
        groovy.preset(node.value, value)
        try {
            super.visit(node, writer, model)
        } finally {
            groovy.clearPreset()
        }
    }

    @Override
    void visit(TagNode node, IndentWriter writer, PugModel model) {
        String name = node.name
        int colon = name == null ? -1 : name.indexOf(':')
        if (colon <= 0 || rc == null || !rc.tags.has(name.substring(0, colon), name.substring(colon + 1))) {
            super.visit(node, writer, model)
            return
        }
        String ns = name.substring(0, colon)
        String tag = name.substring(colon + 1)
        Map attrs = attributes(node, model)
        Closure body = node.hasBlock() ? { Object arg = null -> renderBlock(node.block, model, arg, writer) } : null
        Object out
        try {
            out = rc.tags.invokeAsMethod(ns, tag, attrs, body, rc)
        } catch (PugCompilerException e) {
            throw e
        } catch (Exception e) {
            throw new PugCompilerException(node, [], e)
        }
        if (out != null) writer.append(out.toString())
    }

    private Map attributes(TagNode node, PugModel model) {
        Map attrs = [:]
        for (String block : node.attributeBlocks) {
            Object m = evaluate(block, model, node)
            if (m instanceof Map) attrs.putAll((Map) m)
        }
        for (Attr a : node.attributes) {
            Object v = a.value instanceof ExpressionString ? evaluate(((ExpressionString) a.value).value, model, node) : a.value
            // .classe e class=... juntos, como no HTML do Pug
            attrs[a.name] = a.name == 'class' && attrs['class'] != null ? "${attrs['class']} ${v}".toString() : v
        }
        attrs
    }

    private Object evaluate(String expression, PugModel model, Node node) {
        try {
            engine.expressionHandler.evaluateExpression(expression, model)
        } catch (ExpressionException e) {
            throw new PugCompilerException(node, [], e)
        }
    }

    /** O corpo da tag: o bloco aninhado num escopo próprio, com o argumento do {@code body(x)}. */
    private SafeHtml renderBlock(Node block, PugModel model, Object arg, IndentWriter outer) {
        def sw = new StringWriter()
        def inner = new IndentWriter(sw)
        inner.useIndent = outer.pp
        model.pushScope()
        try {
            if (arg instanceof Map) ((Map) arg).each { k, v -> model.putLocal(k.toString(), v) }
            else if (arg != null) model.putLocal('it', arg)
            block.accept(this, inner, model)
        } finally {
            model.popScope()
        }
        new SafeHtml(sw.toString())
    }
}
