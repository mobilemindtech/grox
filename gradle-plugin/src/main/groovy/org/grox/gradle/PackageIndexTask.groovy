package org.grox.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Grava {@code META-INF/grox/packages}: os pacotes declarados nos fontes de {@code grails-app/} e, para fontes sem
 * {@code package} (pacote padrão, aceito pelo Grails), uma linha {@code class:Nome} por arquivo.
 * A aplicação procura artefatos no pacote da sua classe {@code Application} e nos pacotes de todos
 * esses índices do classpath (os seus e os das bibliotecas {@code org.grox.lib}).
 */
abstract class PackageIndexTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getSources()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @TaskAction
    void write() {
        Set<String> packages = new TreeSet<>()
        sources.files.each { File f ->
            String line = f.withReader('UTF-8') { r -> r.readLines().find { it.trim().startsWith('package ') } }
            if (line) packages << line.trim().substring(8).replace(';', '').trim()
            else packages << 'class:' + f.name.replaceAll(/\.(groovy|java)$/, '')
        }
        File out = new File(outputDir.get().asFile, 'META-INF/grox/packages')
        out.parentFile.mkdirs()
        out.setText(packages.join('\n') + (packages ? '\n' : ''), 'UTF-8')
    }
}
