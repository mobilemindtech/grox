package org.grox.web.scaffolding

import org.grox.core.ClassUtils
import org.grox.view.GspViews
import org.grox.web.WebRequest

import java.util.concurrent.ConcurrentHashMap

/**
 * Views do scaffold dinâmico ({@code static scaffold = Livro}): quando a app não tem a view da action
 * ({@code index}, {@code show}, {@code create}, {@code edit}), ela é gerada do template e compilada uma vez.
 * Os templates usam a sintaxe dos do plugin scaffolding do Grails 3+ ({@code ${propertyName}},
 * {@code ${className}}; {@code \${...}} vira expressão da GSP). A app pode trocá-los pondo os seus no classpath
 * em {@code templates/scaffolding/<action>.gsp} (no layout Grails, {@code src/main/templates/scaffolding/}).
 */
class ScaffoldViews {

    static final List<String> ACTIONS = ['index', 'show', 'create', 'edit']
    static final String APP_TEMPLATES = 'templates/scaffolding/'
    static final String DEFAULT_TEMPLATES = 'org/grox/web/scaffolding/templates/'

    /** Domain do scaffold de cada controller (ou {@link #NONE}). */
    private static final Map<Class<?>, Object> DOMAINS = new ConcurrentHashMap<>()
    private static final Object NONE = new Object()

    /** A domain de {@code static scaffold = X}, ou null. */
    static Class<?> domainOf(Class<?> controller) {
        if (controller == null) return null
        Object d = DOMAINS.computeIfAbsent(controller) { Class<?> c ->
            Object s = ClassUtils.staticProperty(c, 'scaffold')
            s instanceof Class ? s : NONE
        }
        d.is(NONE) ? null : (Class<?>) d
    }

    /**
     * O caminho a renderizar: o pedido, se a view existir ou o controller não tiver scaffold; senão o da view
     * gerada ({@code /_scaffold/<controller>/<action>}).
     */
    static String resolve(WebRequest wr, String path) {
        Class<?> domain = domainOf(wr.controller?.type)
        if (domain == null) return path
        String controller = wr.controllerName
        String action = path.substring(path.lastIndexOf('/') + 1)
        if (!(action in ACTIONS) || path != "/${controller}/${action}".toString()) return path
        GspViews views = (GspViews) wr.ctx.views
        if (views.engine.find(path) != null) return path
        String generated = "/_scaffold/${controller}/${action}"
        views.engine.generated(generated, "scaffold ${action}.gsp (${domain.simpleName})") { -> source(action, domain, wr.ctx.application.classLoader) }
        generated
    }

    /** O template da app ou o padrão, com as variáveis da domain. */
    static String source(String action, Class<?> domain, ClassLoader loader) {
        URL url = loader.getResource(APP_TEMPLATES + action + '.gsp') ?: ScaffoldViews.classLoader.getResource(DEFAULT_TEMPLATES + action + '.gsp')
        expand(url.getText('UTF-8'), [className: domain.simpleName, propertyName: ClassUtils.propertyName(domain),
                                      packageName: domain.package?.name ?: '', resourceName: ClassUtils.propertyName(domain), fullClassName: domain.name])
    }

    /** {@code ${nome}} conhecido vira o valor; {@code \$} vira {@code $} (a expressão fica para a GSP). */
    static String expand(String template, Map<String, String> vars) {
        String s = template.replaceAll(/(?<!\\)\$\{(\w+)\}/) { String all, String name -> vars.containsKey(name) ? vars[name] : all }
        s.replace('\\$', '$')
    }
}
