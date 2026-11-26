package org.grox.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

/**
 * Copia para {@link #getOutputDir} os fontes que os patches alteram (de raízes de {@code grox { source }}) e aplica
 * os patches ({@code patch -p1}) às cópias. Os caminhos dos patches são relativos à raiz
 * ({@code --- a/grails-app/services/X.groovy}); o arquivo vem da última raiz que o tem
 * ({@link GroxSources#originals}). Um patch que não se aplica mais falha o build, com a saída do {@code patch}.
 */
abstract class PatchTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getPatches()

    /** Os arquivos originais (para refazer as cópias quando mudam). */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract ConfigurableFileCollection getOriginals()

    @Input
    abstract ListProperty<String> getPaths()

    /** Os originais (caminho absoluto), na ordem de {@link #getPaths}. */
    @Input
    abstract ListProperty<String> getSources()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Inject
    abstract ExecOperations getExec()

    /**
     * Os arquivos de um patch ({@code +++ b/caminho}), sem o primeiro componente. Caminho absoluto ou com {@code ..}
     * é recusado: a cópia para {@code build/grox-patched} sairia do diretório (antes do próprio {@code patch} recusá-lo).
     */
    static List<String> targets(File patch) {
        patch.readLines('UTF-8').findAll { it.startsWith('+++ ') }.collect { String line ->
            String name = line.substring(4).split('\t')[0].trim()
            String path = name.substring(name.indexOf('/') + 1)
            if (path.startsWith('/') || path ==~ /^[A-Za-z]:.*/ || path.split('[/\\\\]').contains('..')) {
                throw new GradleException("Patch ${patch.name}: caminho fora do projeto: ${name}")
            }
            path
        }.unique()
    }

    @TaskAction
    void apply() {
        File out = outputDir.get().asFile
        out.deleteDir()
        out.mkdirs()
        [paths.get(), sources.get()].transpose().each { String path, String original ->
            File copy = new File(out, path)
            if (!copy.canonicalPath.startsWith(out.canonicalPath + File.separator)) {
                throw new GradleException("Patch: caminho fora de ${out}: ${path}")
            }
            copy.parentFile.mkdirs()
            copy.bytes = new File(original).bytes
        }
        patches.files.sort { it.name }.each { File p ->
            def output = new ByteArrayOutputStream()
            def result = exec.exec {
                it.commandLine('patch', '-p1', '--batch', '--no-backup-if-mismatch', '--reject-file=-', '-d', out.absolutePath,
                        '-i', p.absolutePath)
                it.standardOutput = output
                it.errorOutput = output
                it.ignoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw new GradleException("o patch ${p.name} não se aplica mais (atualize-o):\n${output.toString('UTF-8')}")
            }
        }
    }
}
