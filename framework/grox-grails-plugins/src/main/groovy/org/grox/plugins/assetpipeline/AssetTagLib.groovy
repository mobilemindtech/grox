package org.grox.plugins.assetpipeline

import groovy.transform.CompileStatic
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
    @CompileStatic
    void javascript(Map attrs) {
        String src = withExtension(required(attrs, 'src', 'javascript'), 'js')
        out.write('<script src="' + Html.escape(url(src)) + '"' + Html.attributes(attrs) + '></script>')
    }

    /** {@code <asset:stylesheet href="application.css"/>} (aceita também src) */
    @CompileStatic
    void stylesheet(Map attrs) {
        Object h = attrs.remove('href')
        String href = withExtension(h != null && h.toString() ? h.toString() : required(attrs, 'src', 'stylesheet'), 'css')
        out.write('<link rel="stylesheet" href="' + Html.escape(url(href)) + '"' + Html.attributes(attrs) + '/>')
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
    @CompileStatic
    void image(Map attrs) {
        String src = required(attrs, 'src', 'image')
        out.write('<img src="' + Html.escape(url(src)) + '"' + Html.attributes(attrs) + '/>')
    }

    /** {@code <asset:link rel="icon" href="favicon.ico" type="image/x-icon"/>} */
    @CompileStatic
    void link(Map attrs) {
        String href = required(attrs, 'href', 'link')
        out.write('<link href="' + Html.escape(url(href)) + '"' + Html.attributes(attrs) + '/>')
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

    @CompileStatic
    private AssetServer assets() {
        server
    }

    @CompileStatic
    private String url(String logical) {
        assets()?.url(logical) ?: '/assets/' + (logical.startsWith('/') ? logical.substring(1) : logical)
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
