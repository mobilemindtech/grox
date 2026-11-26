package org.grox.pug

import de.neuland.pug4j.filter.Filter

/** {@code :markdown} do Pug com o commonmark (o pug4j traz o flexmark, que o Grox exclui). HTML cru é escapado. */
class MarkdownFilter implements Filter {
    @Override
    String convert(String source, Map<String, Object> options, Map<String, Object> model) {
        def parser = org.commonmark.parser.Parser.builder().build()
        def renderer = org.commonmark.renderer.html.HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build()
        renderer.render(parser.parse(source))
    }
}
