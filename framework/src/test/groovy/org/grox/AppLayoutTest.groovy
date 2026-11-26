package org.grox

import org.grox.core.AppLayout
import org.grox.core.Config
import org.junit.jupiter.api.Test

/** Nome do diretório do layout: grails-app, grox-app (detectado) ou o de grox.appDir. */
class AppLayoutTest {

    @Test
    void 'fontes de grails-app e grox-app reconhecidos pela AST'() {
        assert AppLayout.artefactDir('/proj/grails-app/controllers/a/LivroController.groovy') == 'controllers'
        assert AppLayout.artefactDir('C:\\proj\\grox-app\\domain\\Livro.groovy') == 'domain'
        assert AppLayout.artefactDir('/proj/src/main/groovy/a/Util.groovy') == null
        assert AppLayout.artefactDir(null) == null
    }

    @Test
    void 'outro nome pela propriedade grox appDir'() {
        assert AppLayout.artefactDir('/proj/app/services/X.groovy') == null
        System.setProperty(AppLayout.PROPERTY, 'app')
        try {
            assert AppLayout.artefactDir('/proj/app/services/X.groovy') == 'services'
            assert AppLayout.artefactDir('/proj/grails-app/services/X.groovy') == 'services'   // os padrões continuam
            assert AppLayout.current() == 'app'
        } finally {
            System.clearProperty(AppLayout.PROPERTY)
        }
        assert AppLayout.artefactDir('/proj/app/services/X.groovy') == null
    }

    @Test
    void 'diretorio atual: configurado, grox-app se existir, senao grails-app'() {
        assert AppLayout.current(new Config([grox: [appDir: 'meu-app']])) == 'meu-app'
        // os testes rodam no diretório do framework, que não tem grox-app/
        assert AppLayout.current(new Config([:])) == AppLayout.GRAILS
    }
}
