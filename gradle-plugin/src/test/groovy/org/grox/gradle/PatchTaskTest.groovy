package org.grox.gradle

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.assertThrows

/** Os arquivos alvo de um patch, e a recusa de caminhos que sairiam de build/grox-patched. */
class PatchTaskTest {

    @TempDir
    File dir

    private File patch(String target) {
        def f = new File(dir, 'x.patch')
        f.text = "--- a/${target}\n+++ b/${target}\t2026-01-01\n@@ -1 +1 @@\n-a\n+b\n"
        f
    }

    @Test
    void 'alvos sem o primeiro componente'() {
        assert PatchTask.targets(patch('grails-app/services/demo/AService.groovy')) == ['grails-app/services/demo/AService.groovy']
    }

    @Test
    void 'caminho com dois pontos ou absoluto e recusado'() {
        ['../../fora.txt', 'grails-app/../../fora.txt', '/etc/passwd'].each { String alvo ->
            def e = assertThrows(GradleException) { PatchTask.targets(patch(alvo)) }
            assert e.message.contains('fora do projeto')
        }
    }
}
