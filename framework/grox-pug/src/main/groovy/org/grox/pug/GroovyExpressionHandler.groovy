package org.grox.pug

import de.neuland.pug4j.exceptions.ExpressionException
import de.neuland.pug4j.expression.AbstractExpressionHandler
import de.neuland.pug4j.model.PugModel
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.runtime.InvokerHelper
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation

import java.util.concurrent.ConcurrentHashMap

/**
 * Expressões do Pug em Groovy, no lugar do JEXL do pug4j: {@code #{livro.titulo}}, {@code each l in livros},
 * {@code if livros.size() > 1}, {@code - var total = livros*.preco.sum()}, closures, {@code ?.} e GStrings. O modelo,
 * os objetos implícitos da GSP e os namespaces de tags ({@code g.createLink(...)}) ficam visíveis
 * ({@link PugBinding}); tags do {@code g} também como método ({@code message(code: 'x')}). Variável ausente é null.
 * Cada expressão é compilada uma vez para uma classe {@link PugExpression}.
 */
class GroovyExpressionHandler extends AbstractExpressionHandler {

    private final GroovyClassLoader loader
    private final Map<String, Class<? extends Script>> scripts = new ConcurrentHashMap<>()
    private volatile boolean cache = true
    /** Valor já avaliado de uma expressão, entregue uma vez ao pug4j (o {@link TagLibCompiler} avalia antes dele). */
    private final ThreadLocal<Object[]> preset = new ThreadLocal<>()

    void preset(String expression, Object value) {
        preset.set([expression, value] as Object[])
    }

    void clearPreset() {
        preset.remove()
    }

    GroovyExpressionHandler(ClassLoader parent) {
        def config = new CompilerConfiguration()
        config.scriptBaseClass = PugExpression.name
        loader = new GroovyClassLoader(parent, config)
    }

    @Override
    Object evaluateExpression(String expression, PugModel model) throws ExpressionException {
        Object[] p = preset.get()
        if (p != null && p[0] == expression) {
            preset.remove()
            return p[1]
        }
        saveLocalVariableName(expression, model)
        try {
            Script script = InvokerHelper.createScript(compile(expression), new PugBinding(model))
            script.run()
        } catch (ExpressionException e) {
            throw e
        } catch (Throwable e) {
            throw new ExpressionException(expression, e instanceof Exception ? (Exception) e : new RuntimeException(e))
        }
    }

    @Override
    Boolean evaluateBooleanExpression(String expression, PugModel model) throws ExpressionException {
        DefaultTypeTransformation.castToBoolean(evaluateExpression(expression, model))   // verdade do Groovy
    }

    @Override
    String evaluateStringExpression(String expression, PugModel model) throws ExpressionException {
        Object result = evaluateExpression(expression, model)
        result == null ? '' : result.toString()
    }

    /** Chamado pelo lexer do pug4j: erro de sintaxe aparece na carga do template, com a linha. */
    @Override
    void assertExpression(String expression) throws ExpressionException {
        try {
            compile(expression)
        } catch (Exception e) {
            throw new ExpressionException(expression, e)
        }
    }

    @Override
    void setCache(boolean cache) {
        this.cache = cache
        if (!cache) scripts.clear()
    }

    @Override
    void clearCache() {
        scripts.clear()
    }

    private Class<? extends Script> compile(String expression) {
        String source = withoutDeclaration(expression)
        if (!cache) return parse(source)
        scripts.computeIfAbsent(source) { String s -> parse(s) }
    }

    private Class<? extends Script> parse(String source) {
        (Class<? extends Script>) loader.parseClass(new GroovyCodeSource(source, 'PugExpression' + Integer.toHexString(source.hashCode()) + '.groovy', '/groovy/pug'), false)
    }

    /**
     * {@code var x = 1} / {@code let} / {@code const} do Pug: o nome já foi registrado como local do escopo
     * ({@code saveLocalVariableName}); a atribuição vai ao modelo pelo binding.
     */
    static String withoutDeclaration(String expression) {
        String e = expression.trim()
        for (String kw : ['var ', 'let ', 'const ']) {
            if (e.startsWith(kw)) return e.substring(kw.length())
        }
        e
    }
}
