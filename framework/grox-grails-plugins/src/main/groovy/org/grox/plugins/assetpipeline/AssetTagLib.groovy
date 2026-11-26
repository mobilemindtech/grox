package org.grox.plugins.assetpipeline

import org.grox.view.taglib.TagLibApi
import org.grox.assets.AssetServer
import org.grox.http.Html

/**
 * Tags {@code asset:} do asset-pipeline do Grails: {@code javascript}, {@code stylesheet},
 * {@code image}, {@code link}, {@code script}, {@code deferredScripts}, {@code assetPath}, {@code assetPathExists}.
 */
class AssetTagLib implements TagLibApi {

    static namespace = 'asset'
    static returnObjectForTags = ['assetPath', 'assetPathExists']

    /** {@code <asset:javascript src="application.js"/>} */
    def javascript = { Map attrs ->
        String src = withExtension(required(attrs, 'src', 'javascript'), 'js')
        out << '<script src="' << Html.escape(url(src)) << '"' << Html.attributes(attrs) << '></script>'
    }

    /** {@code <asset:stylesheet href="application.css"/>} (aceita também src) */
    def stylesheet = { Map attrs ->
        String href = withExtension((attrs.remove('href') ?: required(attrs, 'src', 'stylesheet')).toString(), 'css')
        out << '<link rel="stylesheet" href="' << Html.escape(url(href)) << '"' << Html.attributes(attrs) << '/>'
    }

    static final String DEFERRED = 'grox.asset.deferredScripts'

    /** {@code <asset:script type="text/javascript">...</asset:script>}: guarda o script para {@code deferredScripts}. */
    def script = { Map attrs, Closure body ->
        List<String> scripts = deferred(true)
        scripts << '<script type="' + Html.escape(attrs.type ?: 'text/javascript') + '">' + body() + '</script>'
    }

    /** {@code <asset:deferredScripts/>}: escreve (uma vez) os scripts guardados por {@code asset:script}, normalmente no fim do layout. */
    def deferredScripts = { Map attrs ->
        List<String> scripts = deferred(false)
        if (!scripts) return
        scripts.each { out << it << '\n' }
        scripts.clear()
    }

    private List<String> deferred(boolean create) {
        def req = request
        List<String> scripts = (List<String>) (req != null ? req.getAttribute(DEFERRED) : pageScope.variables[DEFERRED])
        if (scripts == null && create) {
            scripts = []
            if (req != null) req.setAttribute(DEFERRED, scripts) else pageScope.setVariable(DEFERRED, scripts)
        }
        scripts
    }

    /** {@code <asset:image src="logo.png" alt="Logo"/>} */
    def image = { Map attrs ->
        String src = required(attrs, 'src', 'image')
        out << '<img src="' << Html.escape(url(src)) << '"' << Html.attributes(attrs) << '/>'
    }

    /** {@code <asset:link rel="icon" href="favicon.ico" type="image/x-icon"/>} */
    def link = { Map attrs ->
        String href = required(attrs, 'href', 'link')
        out << '<link href="' << Html.escape(url(href)) << '"' << Html.attributes(attrs) << '/>'
    }

    /** {@code ${assetPath(src: 'logo.png')}} */
    def assetPath = { Map attrs ->
        url(required(attrs, 'src', 'assetPath'))
    }

    def assetPathExists = { Map attrs ->
        assets()?.exists(required(attrs, 'src', 'assetPathExists')) ?: false
    }

    /** O servidor de assets da geração corrente, definido pelo {@link AssetPipelineModule} (vale também nas rotas do grox-dsl). */
    static volatile AssetServer server

    private AssetServer assets() {
        server
    }

    private String url(String logical) {
        assets()?.url(logical) ?: '/assets/' + logical.replaceAll(/^\//, '')
    }

    private static String withExtension(String name, String ext) {
        name.endsWith('.' + ext) ? name : name + '.' + ext
    }

    private static String required(Map attrs, String attr, String tag) {
        Object v = attrs.remove(attr)
        if (v == null) throw new IllegalArgumentException("<asset:${tag}> exige o atributo ${attr}")
        v.toString()
    }
}
