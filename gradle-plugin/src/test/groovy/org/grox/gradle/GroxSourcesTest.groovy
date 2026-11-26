package org.grox.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Raízes externas ({@code grox { source }}), overrides do projeto e patches: o build paralelo de uma app Grails. */
class GroxSourcesTest {

    @TempDir
    File dir

    /** A "app Grails" original, fora do projeto; o build paralelo fica em {@code grox/}. */
    private void app() {
        file('app/grails-app/services/demo/AService.groovy', "package demo\nclass AService { static String origem() { 'original' } }")
        file('app/grails-app/services/demo/BService.groovy', "package demo\nclass BService {\n    static String origem() { 'original' }\n}\n")
        file('app/src/main/groovy/demo/Util.groovy', "package demo\nclass Util { static String origem() { 'original' } }")
        file('app/src/main/groovy/demo/so/NoGrails.groovy', "package demo.so\nclass NoGrails { NaoCompila x }")
        file('app/grails-app/conf/application.yml', 'app.nome: original')
        file('app/grails-app/conf/logback-spring.xml', '<configuration/>')
        file('app/grails-app/views/livro/index.gsp', 'original')
        file('app/grails-app/views/livro/show.gsp', 'show')
        file('grox/settings.gradle', "rootProject.name = 'paralelo'")
    }

    private void build(String patches = '') {
        file('grox/build.gradle', """
plugins { id 'org.grox.lib' }
dependencies { implementation localGroovy() }
grox {
    source('../app') {
        exclude 'grails-app/conf/logback-spring.xml', 'src/main/groovy/demo/so/**'
    }
    ${patches}
}
""")
    }

    @Test
    void 'compila as raizes externas com overrides do projeto e patches, sem mudar o original'() {
        app()
        build("patches.from('patches')")
        // override: o projeto substitui o arquivo de mesmo caminho da raiz
        file('grox/src/main/groovy/demo/Util.groovy', "package demo\nclass Util { static String origem() { 'projeto' } }")
        file('grox/grails-app/conf/application-grox.yml', 'app.grox: true')
        file('grox/grails-app/views/livro/index.gsp', 'projeto')
        file('grox/patches/BService.patch', '''--- a/grails-app/services/demo/BService.groovy
+++ b/grails-app/services/demo/BService.groovy
@@ -1,4 +1,4 @@
 package demo
 class BService {
-    static String origem() { 'original' }
+    static String origem() { 'patch' }
 }
''')
        def result = runner('classes').build()
        assert result.output.contains('BUILD SUCCESSFUL')

        def classes = new URLClassLoader([new File(dir, 'grox/build/classes/groovy/main').toURI().toURL()] as URL[], getClass().classLoader)
        assert classes.loadClass('demo.AService').origem() == 'original'
        assert classes.loadClass('demo.BService').origem() == 'patch'
        assert classes.loadClass('demo.Util').origem() == 'projeto'
        assert !new File(dir, 'grox/build/classes/groovy/main/demo/so').exists()      // excluído da raiz

        File resources = new File(dir, 'grox/build/resources/main')
        assert new File(resources, 'application.yml').text == 'app.nome: original'
        assert new File(resources, 'application-grox.yml').file
        assert !new File(resources, 'logback-spring.xml').exists()
        assert new File(resources, 'views/livro/index.gsp').text == 'projeto'
        assert new File(resources, 'views/livro/show.gsp').text == 'show'
        assert new File(dir, 'grox/build/grox-index/META-INF/grox/packages').text.contains('demo')
        assert new File(dir, 'app/grails-app/services/demo/BService.groovy').text.contains("'original'")
    }

    @Test
    void 'raiz posterior so substitui o que os seus padroes aceitam'() {
        app()
        // como o app C-taglibs no app-c-web: tem um application.yml próprio, mas só entram as TagLibs
        file('taglibs/grails-app/conf/application.yml', 'app.nome: taglibs')
        file('taglibs/grails-app/taglib/demo/DemoTagLib.groovy', 'package demo\nclass DemoTagLib {}')
        file('grox/build.gradle', """
plugins { id 'org.grox.lib' }
dependencies { implementation localGroovy() }
grox {
    source('../app') { exclude 'src/main/groovy/demo/so/**' }
    source('../taglibs') { include 'grails-app/taglib/**' }
}
""")
        assert runner('classes').build().output.contains('BUILD SUCCESSFUL')
        assert new File(dir, 'grox/build/resources/main/application.yml').text == 'app.nome: original'
        assert new File(dir, 'grox/build/classes/groovy/main/demo/DemoTagLib.class').file
    }

    @Test
    void 'patch que nao se aplica mais falha o build'() {
        app()
        build("patches.from('patches')")
        file('grox/patches/BService.patch', '''--- a/grails-app/services/demo/BService.groovy
+++ b/grails-app/services/demo/BService.groovy
@@ -1,4 +1,4 @@
 package demo
 class BService {
-    static String origem() { 'mudou no original' }
+    static String origem() { 'patch' }
 }
''')
        def result = runner('classes').buildAndFail()
        assert result.output.contains('o patch BService.patch não se aplica mais')
    }

    @Test
    void 'sem raizes externas o layout continua o do projeto'() {
        file('lib/settings.gradle', "rootProject.name = 'lib'")
        file('lib/build.gradle', "plugins { id 'org.grox.lib' }\ndependencies { implementation localGroovy() }")
        file('lib/grails-app/services/demo/CService.groovy', 'package demo\nclass CService {}')
        def result = GradleRunner.create().withProjectDir(new File(dir, 'lib')).withPluginClasspath()
                .withArguments('classes', 'tasks', '--all', '--stacktrace').build()
        assert new File(dir, 'lib/build/classes/groovy/main/demo/CService.class').file
        assert !result.output.contains('groxPatch')
    }

    private GradleRunner runner(String... tasks) {
        GradleRunner.create().withProjectDir(new File(dir, 'grox')).withPluginClasspath()
                .withArguments((tasks as List) + ['--stacktrace'])
    }

    private File file(String path, String text) {
        File f = new File(dir, path)
        f.parentFile.mkdirs()
        f.text = text
        f
    }
}
