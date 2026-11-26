package org.grox.gradle

import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

/**
 * Raiz de código fora do projeto ({@code grox { source('../app-c-web') }}): um diretório com o layout do Grails
 * ({@code grails-app/} e {@code src/main/}), filtrado por padrões relativos à raiz
 * ({@code exclude 'grails-app/conf/application.yml'}). Ver {@link GroxSources}.
 */
class SourceRoot {
    final Object dir
    @Delegate
    final PatternSet patterns = new PatternSet()

    SourceRoot(Object dir) { this.dir = dir }

    PatternFilterable getFilter() { patterns }
}
