package org.grox.gradle

import org.gradle.api.provider.Property

/** Configuração do layout, comum aos plugins {@code org.grox.app} e {@code org.grox.lib}. */
abstract class GroxLayoutExtension {
    /**
     * Diretório do layout Grails. Padrão: {@code grox-app} se existir, senão {@code grails-app}. Com outro nome, o
     * plugin o passa ({@code -Dgrox.appDir}) à compilação, ao {@code run} e aos testes.
     */
    abstract Property<String> getAppDir()
}
