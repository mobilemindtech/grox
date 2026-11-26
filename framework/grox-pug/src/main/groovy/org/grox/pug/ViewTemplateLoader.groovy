package org.grox.pug

import de.neuland.pug4j.template.TemplateLoader
import org.grox.view.gsp.ViewLocator

/**
 * Os templates {@code .pug} nos mesmos lugares das GSPs ({@link ViewLocator}): o diretório de views em
 * desenvolvimento e {@code views/} no classpath. Nomes absolutos a partir da raiz das views
 * ({@code extends /layouts/base}) ou relativos ao template atual ({@code include ../shared/menu}).
 */
class ViewTemplateLoader implements TemplateLoader {

    final ViewLocator locator

    ViewTemplateLoader(ViewLocator locator) {
        this.locator = locator
    }

    /** {@code livros/index.pug} → {@code /livros/index}. */
    static String viewPath(String name) {
        String p = name.replace('\\', '/')
        if (p.endsWith('.pug')) p = p.substring(0, p.length() - 4)
        p.startsWith('/') ? p : '/' + p
    }

    private ViewLocator.ViewSource source(String name) {
        locator.find(viewPath(name), 'pug')
    }

    @Override
    long getLastModified(String name) throws IOException {
        source(name)?.lastModified ?: -1L
    }

    @Override
    Reader getReader(String name) throws IOException {
        def src = source(name)
        if (src == null) throw new FileNotFoundException("template Pug não encontrado: ${name}")
        new StringReader(src.text)
    }

    @Override
    String getExtension() { 'pug' }

    @Override
    String getBase() { '' }
}
