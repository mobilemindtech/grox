package org.grox.gradle

import org.gradle.api.provider.Property

abstract class GroxExtension extends GroxLayoutExtension {
    /** Pré-compila as GSPs (erros de GSP falham o build). Padrão: true. */
    abstract Property<Boolean> getPrecompileGsp()

    /** {@code run} com JIT só C1 ({@code -XX:TieredStopAtLevel=1}): sobe bem mais rápido em desenvolvimento. Padrão: true. */
    abstract Property<Boolean> getFastDevStartup()

    /**
     * {@code run} com recarga de classes em development: a app sobe em classloaders próprios e um Gradle
     * contínuo recompila ao salvar. Padrão: true. Ver "Recarga de classes" em docs/guia.md.
     */
    abstract Property<Boolean> getReload()
}
