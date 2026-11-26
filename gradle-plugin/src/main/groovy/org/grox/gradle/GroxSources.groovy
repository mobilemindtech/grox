package org.grox.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternSet

/**
 * Raízes de código externas ({@code grox { source(...) }}) e patches ({@code grox { patches }}): o build paralelo
 * do Grox compila o código de uma app Grails no próprio repositório dela, sem cópia e sem tocar no build do Grails.
 * <ul>
 *   <li>cada raiz entra com {@code <appDir>/{controllers,services,...}}, {@code conf}, {@code i18n}, {@code views}
 *       e {@code src/main/{groovy,java,resources,templates}}, filtrada pelos seus padrões;</li>
 *   <li>a ordem é: as raízes, na ordem declarada, depois o diretório do projeto, depois os fontes com patch; um
 *       arquivo de mesmo caminho relativo numa raiz posterior esconde o das anteriores (overrides);</li>
 *   <li>os patches são aplicados ({@code patch -p1}) a cópias em {@code build/grox-patched} ({@link PatchTask}).</li>
 * </ul>
 * Sem raízes nem patches, nada muda no layout.
 */
class GroxSources {

    static final String PATCHED = 'grox-patched'

    static boolean external(GroxLayoutExtension ext) {
        ext.sources || !ext.patches.files.empty
    }

    static void apply(Project project, GroxLayoutExtension ext, SourceSet main) {
        project.afterEvaluate {
            if (!external(ext)) return
            String appDir = ext.appDir.get()
            List<File> roots = ext.sources.collect { SourceRoot r -> project.file(r.dir) }
            roots.findAll { !it.directory }.each { throw new IllegalArgumentException("grox.source: ${it} não existe") }
            // um diretório vale pelos seus *.patch; um que não existe, por nenhum
            List<File> patchFiles = ext.patches.files.collectMany { File f ->
                f.directory ? (f.listFiles().findAll { it.file && it.name.endsWith('.patch') } as List<File>) : (f.file ? [f] : [])
            }.sort { it.name }
            Map<String, File> originals = originals(patchFiles, roots, ext.sources)
            File patchedDir = project.layout.buildDirectory.dir(PATCHED).get().asFile

            def index = project.tasks.named('groxPackageIndex', PackageIndexTask)
            roots.eachWithIndex { File root, int i ->
                SourceRoot sr = ext.sources[i]
                // só substitui o que a raiz posterior de fato contribui (os padrões dela o aceitam)
                List<Later> later = (i + 1..<roots.size()).collect { int j -> new Later(roots[j], ext.sources[j].patterns) } +
                        [new Later(project.projectDir, new PatternSet())]
                def filter = { String base -> new RootFilter(base, sr.patterns, later, originals.keySet()) }
                addRoot(project, main, index, root, appDir, "root${i}", filter, true)
            }
            if (patchFiles) {
                def patch = project.tasks.register('groxPatch', PatchTask) { PatchTask t ->
                    t.description = 'Aplica os patches do build paralelo a cópias dos fontes (build/grox-patched)'
                    t.patches.from(patchFiles)
                    t.originals.from(originals.values())
                    t.paths.set(originals.keySet().toList())
                    t.sources.set(originals.values()*.absolutePath)
                    t.outputDir.set(patchedDir)
                }
                ['compileJava', 'compileGroovy', 'processResources', 'groxPackageIndex'].each { String n ->
                    project.tasks.named(n).configure { it.dependsOn(patch) }
                }
                addRoot(project, main, index, patchedDir, appDir, 'patched', { String base -> null }, true)
            }
        }
    }

    /**
     * Caminho relativo à raiz → arquivo original, para cada arquivo alterado pelos patches: o da última raiz que o
     * tem e cujos padrões o aceitam.
     */
    static Map<String, File> originals(List<File> patches, List<File> roots, List<SourceRoot> sources) {
        Map<String, File> result = new LinkedHashMap<>()
        patches.each { File p ->
            PatchTask.targets(p).each { String path ->
                Integer i = roots ? (roots.size() - 1..0).find { int r -> new File(roots[r], path).file && accepts(sources[r].patterns, path) } : null
                if (i == null) throw new org.gradle.api.GradleException("patch ${p.name}: ${path} não existe em nenhuma raiz (grox { source })")
                result[path] = new File(roots[i], path)
            }
        }
        result
    }

    private static boolean accepts(PatternSet patterns, String path) {
        patterns.empty || patterns.asSpec.isSatisfiedBy(new PathElement(RelativePath.parse(true, path)))
    }

    /** Os diretórios de uma raiz no source set, nos resources ({@code views}, {@code templates}) e no índice. */
    private static void addRoot(Project project, SourceSet main, def index, File root, String appDir, String name,
                                Closure<RootFilter> filter, boolean withSrcMain) {
        GroxLayout.SOURCE_DIRS.each { String d ->
            main.groovy.source(sds(project, "${name}-${d}", new File(root, "${appDir}/${d}"), filter("${appDir}/${d}/")))
            index.configure { PackageIndexTask t ->
                t.sources.from(tree(project, new File(root, "${appDir}/${d}"), filter("${appDir}/${d}/")).matching {
                    it.include('**/*.groovy', '**/*.java')
                })
            }
        }
        ['conf', 'i18n'].each { String d ->
            main.resources.source(sds(project, "${name}-${d}", new File(root, "${appDir}/${d}"), filter("${appDir}/${d}/")))
        }
        if (withSrcMain) {
            main.groovy.source(sds(project, "${name}-groovy", new File(root, 'src/main/groovy'), filter('src/main/groovy/')))
            main.java.source(sds(project, "${name}-java", new File(root, 'src/main/java'), filter('src/main/java/')))
            main.resources.source(sds(project, "${name}-resources", new File(root, 'src/main/resources'), filter('src/main/resources/')))
        }
        project.tasks.named('processResources').configure { task ->
            RootFilter views = filter("${appDir}/views/")
            task.from(new File(root, "${appDir}/views")) { if (views) it.exclude(views); it.into('views') }
            RootFilter templates = filter('src/main/templates/')
            task.from(new File(root, 'src/main/templates')) { if (templates) it.exclude(templates); it.into('templates') }
        }
    }

    private static SourceDirectorySet sds(Project project, String name, File dir, RootFilter filter) {
        SourceDirectorySet s = project.objects.sourceDirectorySet("grox-${name}", "Grox ${name}")
        s.srcDir(dir)
        if (filter) s.exclude(filter)   // padrões do conjunto (o filter não vale quando aninhado em outro)
        s
    }

    private static def tree(Project project, File dir, RootFilter filter) {
        def t = project.fileTree(dir)
        if (filter) t.exclude(filter)
        t
    }

    /**
     * Exclui de uma raiz o que os padrões dela não aceitam e o que uma raiz posterior (ou o projeto, ou um patch)
     * substitui. Os caminhos são relativos à raiz: {@code base} é o diretório da árvore ({@code grails-app/views/}).
     */
    static class RootFilter implements Spec<FileTreeElement>, Serializable {
        private final String base
        private final Set<String> includes
        private final Set<String> excludes
        private final List<Later> later
        private final Set<String> patched
        private transient Spec<FileTreeElement> patterns

        RootFilter(String base, PatternSet patterns, List<Later> later, Set<String> patched) {
            this.base = base
            this.includes = new LinkedHashSet<>(patterns.includes)
            this.excludes = new LinkedHashSet<>(patterns.excludes)
            this.later = later
            this.patched = new HashSet<>(patched)
        }

        @Override
        boolean isSatisfiedBy(FileTreeElement e) {
            String path = base + e.relativePath.pathString
            if ((includes || excludes) && !spec().isSatisfiedBy(new RootElement(e, RelativePath.parse(!e.directory, path)))) return true
            if (e.directory) return false
            patched.contains(path) || later.any { it.replaces(path) }
        }

        private Spec<FileTreeElement> spec() {
            if (patterns == null) patterns = new PatternSet().include(includes).exclude(excludes).asSpec
            patterns
        }
    }

    /**
     * Uma raiz posterior (ou o projeto) e os seus padrões: esconde o arquivo de mesmo caminho das anteriores só se o
     * tem e se os padrões dela o aceitam. Um {@code include} que deixa de fora {@code grails-app/conf/application.yml}
     * não pode tirar o {@code application.yml} da raiz anterior.
     */
    static class Later implements Serializable {
        private final File dir
        private final Set<String> includes
        private final Set<String> excludes
        private transient Spec<FileTreeElement> patterns

        Later(File dir, PatternSet patterns) {
            this.dir = dir
            this.includes = new LinkedHashSet<>(patterns.includes)
            this.excludes = new LinkedHashSet<>(patterns.excludes)
        }

        boolean replaces(String path) {
            if (!new File(dir, path).file) return false
            if (!includes && !excludes) return true
            if (patterns == null) patterns = new PatternSet().include(includes).exclude(excludes).asSpec
            patterns.isSatisfiedBy(new PathElement(RelativePath.parse(true, path)))
        }
    }

    /** Um caminho relativo à raiz, só para os padrões da raiz. */
    static class PathElement implements FileTreeElement {
        private final RelativePath relativePath

        PathElement(RelativePath relativePath) { this.relativePath = relativePath }

        RelativePath getRelativePath() { relativePath }

        String getPath() { relativePath.pathString }

        String getName() { relativePath.lastName }

        boolean isDirectory() { false }

        File getFile() { throw new UnsupportedOperationException() }

        long getLastModified() { 0L }

        long getSize() { 0L }

        InputStream open() { throw new UnsupportedOperationException() }

        void copyTo(OutputStream output) { throw new UnsupportedOperationException() }

        boolean copyTo(File target) { throw new UnsupportedOperationException() }

        int getMode() { 0644 }

        org.gradle.api.file.FilePermissions getPermissions() { throw new UnsupportedOperationException() }
    }

    /** O elemento com o caminho relativo à raiz, para os padrões da raiz. */
    static class RootElement implements FileTreeElement {
        @Delegate
        private final FileTreeElement element
        private final RelativePath path

        RootElement(FileTreeElement element, RelativePath path) {
            this.element = element
            this.path = path
        }

        RelativePath getRelativePath() { path }

        String getPath() { path.pathString }

        String getName() { path.lastName }
    }
}
