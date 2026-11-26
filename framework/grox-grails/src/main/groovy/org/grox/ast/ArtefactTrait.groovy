package org.grox.ast

import groovy.transform.CompileStatic

/**
 * Trait que a {@link ArtefactTransformation} aplica às classes de um diretório de {@code grails-app/}
 * cujo nome termina com o sufixo (ex.: {@code jobs/*Job}). Os plugins registram as suas em
 * {@code META-INF/services/org.grox.ast.ArtefactTrait}; as classes desses diretórios também recebem
 * {@code @Reloadable}.
 */
@CompileStatic
interface ArtefactTrait {
    String getDirectory()

    String getSuffix()

    Class<?> getTrait()
}
