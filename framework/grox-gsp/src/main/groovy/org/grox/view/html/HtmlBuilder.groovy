package org.grox.view.html

import groovy.xml.MarkupBuilder
import org.grox.http.SafeHtml

/**
 * O {@link MarkupBuilder} do Groovy ajustado para HTML5: os elementos vazios do HTML ({@code br}, {@code input},
 * {@code meta}...) saem como {@code <br />}, e os demais, mesmo sem conteúdo, com a tag de fechamento
 * ({@code <script src="x"></script>}, {@code <div></div>}), que o navegador exige. Texto e atributos são
 * escapados; um valor {@link SafeHtml} (a saída das tags {@code g:}, {@code raw(...)}) entra sem escape.
 * Atributos null são omitidos.
 */
class HtmlBuilder extends MarkupBuilder {

    static final Set<String> VOID_ELEMENTS = ['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta',
                                             'source', 'track', 'wbr'] as Set

    HtmlBuilder(Writer out) {
        super(new IndentPrinter(out, '', false))
        doubleQuotes = true
        omitNullAttributes = true
        expandEmptyElements = true
    }

    /**
     * Namespaces de tags ({@code g}, {@code sec}, os das TagLibs da app) durante uma renderização; outros nomes
     * seguem para o dono do closure (variáveis do handler, {@code params}, services).
     */
    Object propertyMissing(String name) {
        def tags = org.grox.view.gsp.GspRenderContext.CURRENT.bound ? org.grox.view.gsp.GspRenderContext.current().tags : null
        if (tags?.hasNamespace(name)) return tags.namespace(name)
        throw new MissingPropertyException(name, HtmlBuilder)
    }

    @Override
    protected void nodeCompleted(Object parent, Object node) {
        boolean empty = VOID_ELEMENTS.contains(node?.toString()?.toLowerCase())
        if (empty) expandEmptyElements = false
        try {
            super.nodeCompleted(parent, node)
        } finally {
            if (empty) expandEmptyElements = true
        }
    }

    @Override
    protected Object createNode(Object name, Object value) {
        if (!(value instanceof SafeHtml)) return super.createNode(name, value)
        Object node = super.createNode(name)
        mkp.yieldUnescaped(value.toString())
        node
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        if (!(value instanceof SafeHtml)) return super.createNode(name, attributes, value)
        Object node = super.createNode(name, attributes)
        mkp.yieldUnescaped(value.toString())
        node
    }
}
