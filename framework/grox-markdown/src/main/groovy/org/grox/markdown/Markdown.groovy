package org.grox.markdown

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Markdown → HTML (commonmark, com tabelas e tachado do GFM, links automáticos, âncoras nos títulos e front
 * matter YAML). Links {@code javascript:} e afins são neutralizados. Em {@code safe}, o HTML cru do texto é
 * escapado: use para conteúdo vindo do usuário ou do banco.
 */
class Markdown {

    static final List<Extension> EXTENSIONS = [TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create(),
                                               HeadingAnchorExtension.create(), YamlFrontMatterExtension.create()].asImmutable()

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build()
    private static final HtmlRenderer TRUSTED = HtmlRenderer.builder().extensions(EXTENSIONS).sanitizeUrls(true).build()
    private static final HtmlRenderer SAFE = HtmlRenderer.builder().extensions(EXTENSIONS).sanitizeUrls(true).escapeHtml(true).build()

    /** HTML e front matter ({@code chave → valor}; uma chave com vários valores vira lista). */
    static class Result {
        String html
        Map<String, Object> frontMatter
    }

    static Result parse(String text, boolean safe = false) {
        Node doc = PARSER.parse(text ?: '')
        def visitor = new YamlFrontMatterVisitor()
        doc.accept(visitor)
        Map<String, Object> front = visitor.data.collectEntries { String k, List<String> v -> [k, v.size() == 1 ? v[0] : v] }
        new Result(html: (safe ? SAFE : TRUSTED).render(doc), frontMatter: front)
    }

    /** Só o HTML; {@code safe} (padrão) escapa o HTML cru do texto. */
    static String toHtml(String text, boolean safe = true) {
        parse(text, safe).html
    }
}
