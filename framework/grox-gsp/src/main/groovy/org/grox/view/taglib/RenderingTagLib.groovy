package org.grox.view.taglib

import org.grox.http.Html

/**
 * Tags {@code rendering:} do plugin rendering: imagens embutidas no PDF como data URI (o Flying Saucer as lê).
 * {@code <rendering:inlinePng bytes="${bytes}" height="66px"/>}; os demais atributos vão para o {@code <img>}.
 */
class RenderingTagLib implements TagLibApi {

    static namespace = 'rendering'

    def inlinePng = { attrs -> inline('image/png', attrs) }

    def inlineJpeg = { attrs -> inline('image/jpeg', attrs) }

    def inlineGif = { attrs -> inline('image/gif', attrs) }

    private void inline(String mime, Map attrs) {
        Object bytes = attrs.remove('bytes')
        if (bytes == null) throw new IllegalArgumentException("rendering: o atributo 'bytes' é obrigatório")
        out << '<img src="data:' << mime << ';base64,' << Base64.encoder.encodeToString((byte[]) bytes) << '"'
        attrs.each { k, v -> out << ' ' << k << '="' << Html.escape(v?.toString() ?: '') << '"' }
        out << '/>'
    }
}
