package org.grox.view

/**
 * Módulo ({@code GroxModule}) que contribui com a GSP: TagLibs prontas e imports padrão das páginas.
 * O {@link GspModule} registra as TagLibs depois das nativas e antes das {@code *TagLib} da aplicação
 * (que podem sobrescrever as tags), e o {@link org.grox.view.gsp.GspPrecompiler} usa os namespaces e os
 * imports. Nada aqui pode depender do runtime: o pré-compilador instancia o módulo sem subir a aplicação.
 */
trait GspContributor {
    List<Class<?>> getTagLibClasses() { [] }

    /** Classes importadas em toda GSP (o módulo Grails importa o {@code JSON} dos converters, para {@code x as JSON}). */
    List<String> getGspImports() { [] }
}
