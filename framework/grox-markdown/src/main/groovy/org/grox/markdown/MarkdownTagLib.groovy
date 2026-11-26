package org.grox.markdown

/**
 * {@code <md:render text="${post.corpo}"/>} (ou com o Markdown no corpo da tag): Markdown → HTML em qualquer view
 * (GSP, Pug, Markdown). O HTML cru do texto é escapado; {@code trusted="true"} o mantém (só para texto confiável).
 */
class MarkdownTagLib implements org.grox.view.taglib.TagLibApi {
    static namespace = 'md'

    def render = { attrs, body ->
        String text = attrs.containsKey('text') ? attrs.text?.toString() : body()?.toString()
        boolean trusted = attrs.trusted?.toString() == 'true'
        out << Markdown.toHtml(text ?: '', !trusted)
    }
}
