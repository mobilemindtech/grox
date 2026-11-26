package org.grox.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin {@code org.grox.lib}: biblioteca de artefatos (domains, services, taglibs, jobs) no layout
 * {@code grails-app/}, sem classe {@code Application}, usada por aplicações {@code org.grox.app}
 * (como um plugin do Grails). Aplica groovy + java-library e o {@link GroxLayout}; os artefatos são
 * encontrados pela aplicação pelo índice {@code META-INF/grox/packages}.
 * A dependência do framework continua no build da biblioteca.
 */
class GroxLibPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply('groovy')
        project.pluginManager.apply('java-library')
        GroxLayoutExtension ext = project.extensions.create('grox', GroxLayoutExtension)
        GroxLayout.apply(project, ext)
    }
}
