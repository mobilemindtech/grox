package org.grox.view

import groovy.util.logging.Slf4j
import org.grox.core.ArtefactType
import org.grox.core.GroxModule
import org.grox.view.gsp.GspTemplateEngine
import org.grox.view.gsp.ViewLocator
import org.grox.view.taglib.ApplicationTagLib
import org.grox.view.taglib.FormTagLib
import org.grox.view.taglib.RenderTagLib
import org.grox.view.taglib.RenderingTagLib
import org.grox.view.GspContributor
import org.grox.view.taglib.TagLibRegistry

/**
 * Motor GSP, layouts SiteMesh e TagLibs: as nativas, as dos módulos ({@link GspContributor}: validação,
 * {@code sec:}, {@code asset:}) e as {@code *TagLib} da aplicação, nessa ordem.
 */
@Slf4j
class GspModule extends GroxModule {

    TagLibRegistry tagLibs
    GspViews views

    int getOrder() { 300 }

    @Override
    List<ArtefactType> getArtefactTypes() { [ArtefactType.suffix('TagLib')] }

    /** TagLibs nativas da GSP (namespace {@code g}). */
    static final List<Class<?>> BUILT_IN = [ApplicationTagLib, FormTagLib, RenderTagLib, org.grox.view.taglib.ValidationTagLib, RenderingTagLib]

    /** As TagLibs dos módulos {@link GspContributor}, na ordem dos módulos. */
    static List<Class<?>> providedTagLibs(Collection<GroxModule> modules) {
        modules.findAll { it instanceof GspContributor }.collectMany { ((GspContributor) it).tagLibClasses }
    }

    /** Os imports padrão das GSPs pedidos pelos módulos. */
    static List<String> providedImports(Collection<GroxModule> modules) {
        modules.findAll { it instanceof GspContributor }.collectMany { ((GspContributor) it).gspImports }.unique()
    }

    @Override
    void configureWeb() {
        tagLibs = new TagLibRegistry()
        (BUILT_IN + providedTagLibs(runtime.modules)).each { Class<?> c ->
            tagLibs.register(runtime.autowirer.autowire(c.getDeclaredConstructor().newInstance()))
        }
        runtime.application.getArtefacts('TagLib').each { Class<?> c ->
            tagLibs.register(runtime.autowirer.autowire(c.getDeclaredConstructor().newInstance()))
        }
        GspTemplateEngine.imports = providedImports(runtime.modules)
        boolean reload = config.getProperty('grox.gsp.reload', Boolean, runtime.development)
        def locator = new ViewLocator(runtime.devDirectory('grox.gsp.viewsDir', org.grox.core.AppLayout.current(runtime.config) + '/views'), runtime.application.classLoader)
        views = new GspViews(new GspTemplateEngine(locator, reload, tagLibs, runtime.application.classLoader), tagLibs,
                config.getProperty('grails.sitemesh.default.layout', String))
        log.debug("Views: ${locator.directory ?: 'classpath:views/'} (reload: ${reload}); namespaces de tags: ${tagLibs.namespaceNames}")
    }
}
