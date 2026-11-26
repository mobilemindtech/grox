package org.grox.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

/**
 * Bibliotecas {@code org.grox.lib} do mesmo build de que a aplicação depende (direta ou transitivamente, pelo
 * {@code runtimeClasspath}), como o app-c-core do app-c. Os diretórios de classes e resources delas entram na
 * recarga ({@code grox.reload.classes}/{@code resources}) junto com os da aplicação, e a saída completa entra no
 * classpath do {@code run} no lugar dos jars. Dependências de outros builds (o framework por composite build) e
 * projetos sem o plugin (o próprio framework no build do Grox) ficam de fora: continuam no classloader do sistema.
 * As listas são preenchidas depois que todos os projetos foram avaliados (a biblioteca pode ser avaliada depois da app).
 */
class ReloadLibs {

    final ConfigurableFileCollection classes
    final ConfigurableFileCollection resources
    final ConfigurableFileCollection outputs
    /** Diretórios de fontes (inclusive raízes externas de {@code grox { source }}), para o modo sob demanda. */
    final ConfigurableFileCollection sources
    /** {@code buildTreePath} das bibliotecas encontradas. */
    final Set<String> paths = new HashSet<>()

    private ReloadLibs(Project project) {
        classes = project.objects.fileCollection()
        resources = project.objects.fileCollection()
        outputs = project.objects.fileCollection()
        sources = project.objects.fileCollection()
    }

    static ReloadLibs register(Project app) {
        def libs = new ReloadLibs(app)
        app.gradle.projectsEvaluated {
            find(app).each { Project lib ->
                SourceSet main = lib.extensions.getByType(SourceSetContainer).getByName('main')
                libs.classes.from(main.output.classesDirs)
                libs.resources.from(main.output.resourcesDir)
                libs.outputs.from(main.output)
                libs.sources.from(main.allSource.sourceDirectories)
                libs.paths << lib.buildTreePath
            }
        }
        libs
    }

    /**
     * O {@code runtimeClasspath} sem os jars das bibliotecas (as dependências delas continuam). O {@code compileGroovy}
     * da app consome esses jars, e o Gradle contínuo os reescreve enquanto a JVM do {@code run} os teria abertos
     * ({@code ZipException: invalid LOC header} na recarga seguinte); a app usa os diretórios de saída.
     */
    FileCollection runtimeWithoutLibs(Project app) {
        app.configurations.getByName('runtimeClasspath').incoming.artifactView { view ->
            view.componentFilter(new NotLib(paths))
        }.files
    }

    static class NotLib implements Spec<ComponentIdentifier>, Serializable {
        private final Set<String> paths

        NotLib(Set<String> paths) { this.paths = paths }

        @Override
        boolean isSatisfiedBy(ComponentIdentifier id) {
            !(id instanceof ProjectComponentIdentifier && paths.contains(((ProjectComponentIdentifier) id).buildTreePath))
        }
    }

    /** Projetos com {@code org.grox.lib} alcançáveis pelas dependências de projeto do {@code runtimeClasspath}. */
    static List<Project> find(Project app) {
        Set<String> seen = new HashSet<>()
        List<Project> libs = []
        Deque<Project> pending = new ArrayDeque<>([app])
        while (!pending.empty) {
            Configuration runtime = pending.pop().configurations.findByName('runtimeClasspath')
            if (runtime == null) continue
            runtime.allDependencies.withType(ProjectDependency).each { ProjectDependency dep ->
                if (!seen.add(dep.path)) return
                Project target = app.project(dep.path)
                if (target.plugins.hasPlugin('org.grox.lib')) libs << target
                pending << target
            }
        }
        libs
    }
}
