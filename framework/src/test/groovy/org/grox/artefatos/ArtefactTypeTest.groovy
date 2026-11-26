package org.grox.artefatos

import org.grox.core.ArtefactType
import org.grox.core.GroxApplication
import org.grox.web.GroxGrailsApplication
import org.junit.jupiter.api.Test

/** Varredura dos artefatos pelos tipos que os módulos registram ({@link ArtefactType}). */
class ArtefactTypeTest {

    static final ArtefactType MARCADO = ArtefactType.assignableTo('Marcado', ArtefactType.MARKER, Marcado)

    @Test
    void 'cada classe fica no primeiro tipo que a aceita, por precedência'() {
        def app = new GroxApplication(ArtefatosApp)
        app.scan([ArtefactType.suffix('Controller'), GroxApplication.SERVICE, ArtefactType.suffix('UrlMappings', true), MARCADO])
        assert app.getArtefacts('Service') == [LivroService]        // abstrato e enum ficam de fora
        assert app.getArtefacts('Marcado') == [MarcadoService]       // marcador antes do sufixo
        assert app.getArtefacts('Controller') == [LivroController]   // 'Controller' sozinho não é artefato
        assert app.getArtefacts('UrlMappings') == [UrlMappings]      // exato
        assert app.getArtefacts('Job') == []
    }

    @Test
    void 'só o core: sem os tipos dos módulos, só os services'() {
        def app = new GroxApplication(ArtefatosApp)
        app.scan([GroxApplication.SERVICE])
        assert app.getArtefacts('Service') as Set == [LivroService, MarcadoService] as Set
        assert app.getArtefacts('Controller') == []
    }

    @Test
    void 'grailsApplication: fachada com GrailsClass e xxxClasses'() {
        def core = new GroxApplication(ArtefatosApp)
        core.scan([GroxApplication.SERVICE, ArtefactType.suffix('Controller')])
        def app = new GroxGrailsApplication(core)
        assert app.controllerClasses*.logicalPropertyName == ['livro']
        assert app.getArtefact('Service', 'livro').clazz == LivroService
        assert app.getArtefacts('Service')*.clazz.contains(LivroService) && app.getArtefacts('Service') instanceof grails.core.GrailsClass[]
        assert app.config.is(core.config) && app.environmentName == core.environmentName
    }
}

class ArtefatosApp {}

interface Marcado {}

class LivroService {}

class MarcadoService implements Marcado {}

abstract class BaseService {}

enum StatusService { A }

class LivroController {}

class Controller {}

class UrlMappings {}
