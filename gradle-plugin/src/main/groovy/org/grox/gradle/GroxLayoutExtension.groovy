package org.grox.gradle

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

/** Configuração do layout, comum aos plugins {@code org.grox.app} e {@code org.grox.lib}. */
abstract class GroxLayoutExtension {
    /**
     * Diretório do layout Grails. Padrão: {@code grox-app} se existir, senão {@code grails-app}. Com outro nome, o
     * plugin o passa ({@code -Dgrox.appDir}) à compilação, ao {@code run} e aos testes.
     */
    abstract Property<String> getAppDir()

    /** Raízes de código fora do projeto, na ordem em que foram declaradas ({@link #source}). */
    final List<SourceRoot> sources = []

    /**
     * Compila também o código de outro diretório com o layout do Grails ({@code grails-app/} e {@code src/main/}),
     * sem copiá-lo: é o build paralelo do Grox dentro do repositório de uma app Grails. Os padrões
     * ({@code include}/{@code exclude}) são relativos à raiz. Um arquivo de mesmo caminho numa raiz declarada
     * depois, ou no próprio projeto, substitui o da raiz (overrides). Ver {@link GroxSources}.
     */
    SourceRoot source(Object dir, Action<? super SourceRoot> action = null) {
        SourceRoot root = new SourceRoot(dir)
        action?.execute(root)
        sources << root
        root
    }

    /**
     * Patches (diff unificado, {@code a/<caminho na raiz>}) aplicados aos fontes das raízes de {@link #source}
     * numa cópia em {@code build/grox-patched}: o original não muda. Um patch que não se aplica mais falha o build.
     * Um diretório ({@code patches.from('patches')}) vale pelos seus arquivos {@code *.patch}.
     */
    abstract ConfigurableFileCollection getPatches()
}
