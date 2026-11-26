package org.grox

import org.junit.jupiter.api.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Fronteiras dos módulos (docs/modularizacao.md). Cada pacote pertence a um módulo (subprojeto
 * {@code framework/grox-*}), e cada módulo só pode referenciar os módulos de que depende. O compilador já
 * barra a referência direta a uma classe de outro módulo; o teste pega também os nomes qualificados em
 * código dinâmico e em strings, e confere que cada fonte está no subprojeto do seu pacote (nenhum pacote
 * dividido entre dois jars). Violações conhecidas ficam em src/test/resources/arquitetura-violacoes.txt
 * (a lista só encolhe; hoje vazia).
 *
 * Para regravar a lista: ./gradlew :framework:test --tests org.grox.ArquiteturaTest -Darquitetura.atualizar=true
 */
class ArquiteturaTest {

    /** Módulo → módulos de que ele pode depender (além dele mesmo). */
    static final Map<String, Set<String>> PERMITIDO = [
            'core'    : [] as Set,
            'gorm'    : ['core'] as Set,
            'gsp'     : ['core'] as Set,
            'assets'  : ['core'] as Set,
            'security': ['core'] as Set,
            'security-gorm': ['core', 'gorm', 'security'] as Set,
            'grails'  : ['core', 'gorm', 'gsp', 'assets', 'security', 'security-gorm'] as Set,
            'plugins' : ['core', 'gorm', 'gsp', 'assets', 'security', 'security-gorm', 'grails'] as Set,
            'dsl'     : ['core', 'gorm', 'gsp', 'security'] as Set,
            'pug'     : ['core', 'gsp'] as Set,
            'markdown': ['core', 'gsp'] as Set,
    ]

    /** Módulo → subprojeto. */
    static final Map<String, String> SUBPROJETOS = [
            'core': 'grox-core', 'gorm': 'grox-gorm', 'gsp': 'grox-gsp', 'assets': 'grox-assets', 'security': 'grox-security',
            'security-gorm': 'grox-security-gorm', 'grails': 'grox-grails', 'plugins': 'grox-grails-plugins', 'dsl': 'grox-dsl',
            'pug': 'grox-pug', 'markdown': 'grox-markdown',
    ]

    /** Prefixo de pacote → módulo; vale o prefixo mais longo. Só pacotes: nenhum pacote fica em dois módulos. */
    static final Map<String, String> PACOTES = [
            'org.grox'                          : 'core',
            'org.grox.core'                     : 'core',
            'org.grox.http'                     : 'core',
            'org.grox.session'                  : 'core',
            'org.grox.routing'                  : 'core',
            'org.grox.i18n'                     : 'core',
            'org.grox.management'               : 'core',
            'org.grox.async'                    : 'core',
            'org.grox.service'                  : 'core',
            // API servlet do jar oficial (jakarta.servlet-api), implementada pelo http do core; ver docs/modularizacao.md
            'jakarta.servlet'                  : 'core',
            // marcadores que o http do core implementa ou estende, para assinaturas tipadas das apps
            'org.springframework.web.multipart': 'core',
            'grails.web.servlet.mvc'           : 'core',
            // contratos do Spring sem lógica (ApplicationEvent, ApplicationListener, MessageSource) que o core
            // implementa e entrega; os subpacotes continuam com o Grails
            'org.springframework.context'      : 'core',
            'org.springframework.context.annotation': 'grails',
            'org.springframework.context.i18n' : 'grails',
            // API de tarefas assíncronas do Grails (Promise, Promises), implementada pelo core
            'grails.async'                     : 'core',
            'org.grox.data'                     : 'gorm',
            'grails.gorm'                      : 'gorm',
            'grails.validation'                : 'gorm',
            'grails.databinding'               : 'gorm',
            'org.grails.datastore'             : 'gorm',
            // resolvers de tenant pela requisição (no GORM, do grails-datastore-web)
            'org.grails.datastore.mapping.multitenancy.web': 'grails',
            'org.grails.orm'                   : 'gorm',
            'org.springframework.transaction'  : 'gorm',
            'org.springframework.dao'          : 'gorm',
            'org.springframework.validation'   : 'gorm',
            // anotação @Listener dos eventos do GORM
            'grails.events.annotation.gorm'    : 'gorm',
            'org.grox.view'                     : 'gsp',
            'org.grox.assets'                   : 'assets',
            'org.grox.security'                 : 'security',
            // contratos mínimos do spring-security que a autenticação do Grox implementa (Authentication,
            // GrantedAuthority, AuthenticationException); os subpacotes são do plugin
            'org.springframework.security.core': 'security',
            'org.grox.security.gorm'            : 'security-gorm',
            'org.grox.web'                      : 'grails',
            'org.grox.ast'                      : 'grails',
            'grails'                           : 'grails',
            'org.grails'                       : 'grails',
            'org.springframework'              : 'grails',
            // descritores *GrailsPlugin (Plugin, GrailsPluginManager...): API do Grails; os subpacotes são de plugins
            'grails.plugins'                   : 'grails',
            'grails.plugins.orm'               : 'plugins',
            'grails.plugins.quartz'            : 'plugins',
            'grails.plugins.redis'             : 'plugins',
            'grails.plugins.rendering'         : 'plugins',
            'org.grox.dsl'                      : 'dsl',
            'org.grox.pug'                      : 'pug',
            'org.grox.markdown'                 : 'markdown',
            'org.grox.plugins'                  : 'plugins',
            'grails.plugin'                    : 'plugins',
            'org.grails.plugins'               : 'plugins',
            'org.springframework.security'     : 'plugins',
            'org.springframework.security.core.authority': 'plugins',
            'org.springframework.security.core.context': 'plugins',
            'org.springframework.security.core.session': 'plugins',
            'org.springframework.security.core.userdetails': 'plugins',
            'asset.pipeline'                   : 'plugins',
            'com.budjb'                        : 'plugins',
            'pl.touk'                          : 'plugins',
            'groovyx.gpars'                    : 'plugins',
    ]

    static final Pattern REF = ~/\b((?:org\.grox|grails|org\.grails|org\.springframework|jakarta\.servlet|groovyx\.gpars|asset\.pipeline|com\.budjb|pl\.touk)(?:\.[a-z][a-zA-Z0-9_]*)*)\.([A-Z][A-Za-z0-9_]*)/
    static final Pattern PACKAGE = ~/(?m)^\s*package\s+([\w.]+)/

    static File lista = new File('src/test/resources/arquitetura-violacoes.txt')

    /** Os fontes de cada subprojeto (subprojeto → diretório); antes da divisão, o src/main/groovy do framework. */
    static Map<String, File> fontes() {
        Map<String, File> dirs = SUBPROJETOS.values().collectEntries { [it, new File(it, 'src/main/groovy')] }.findAll { k, v -> v.directory }
        dirs ?: (new File('src/main/groovy').directory ? ['': new File('src/main/groovy')] : [:])
    }

    static String modulo(String pacote, String classe) {
        String melhor = null
        PACOTES.each { String prefixo, String m ->
            if ((pacote == prefixo || pacote.startsWith(prefixo + '.')) && (melhor == null || prefixo.length() > melhor.length())) melhor = prefixo
        }
        melhor ? PACOTES[melhor] : null
    }

    /** "origem -> destino: pacote.Classe -> pacote.Classe", ordenadas e sem repetição. */
    static SortedSet<String> violacoes() {
        SortedSet<String> out = new TreeSet<>()
        fontes().each { String subprojeto, File dir -> dir.eachFileRecurse { File f ->
            if (!(f.name.endsWith('.groovy') || f.name.endsWith('.java'))) return
            String src = f.text
            Matcher pm = PACKAGE.matcher(src)
            String pacote = pm.find() ? pm.group(1) : ''
            String classe = f.name.replaceAll(/\.(groovy|java)$/, '')
            String origem = modulo(pacote, classe)
            assert origem != null: "pacote sem módulo em ArquiteturaTest.PACOTES: ${pacote} (${f})"
            if (subprojeto) assert SUBPROJETOS[origem] == subprojeto: "${f} é do módulo ${origem} (${SUBPROJETOS[origem]}), não de ${subprojeto}"
            Matcher m = REF.matcher(src)
            while (m.find()) {
                String destino = modulo(m.group(1), m.group(2))
                if (destino == null || destino == origem || destino in PERMITIDO[origem]) continue
                out << "${origem} -> ${destino}: ${pacote}.${classe} -> ${m.group(1)}.${m.group(2)}".toString()
            }
        } }
        out
    }

    @Test
    void 'dependencias entre modulos so encolhem'() {
        assert fontes(): "rode a partir do diretório do framework (${new File('.').absolutePath})"
        SortedSet<String> atuais = violacoes()
        if (Boolean.getBoolean('arquitetura.atualizar')) {
            lista.text = '# Gerado por ArquiteturaTest (-Darquitetura.atualizar=true). Ver docs/modularizacao.md\n' + atuais.join('\n') + '\n'
            return
        }
        Set<String> conhecidas = lista.readLines().findAll { it && !it.startsWith('#') } as Set
        Collection<String> novas = atuais.findAll { !(it in conhecidas) }
        Collection<String> resolvidas = conhecidas.findAll { !(it in atuais) }
        assert !novas: "dependências novas entre módulos (ver docs/modularizacao.md):\n" + novas.join('\n')
        assert !resolvidas: "violações resolvidas: tire-as da lista (ou regrave com -Darquitetura.atualizar=true):\n" + resolvidas.join('\n')
    }

    @Test
    void 'todo pacote do framework tem modulo'() {
        assert modulo('org.grox.data.query', 'X') == 'gorm'
        assert modulo('grails.plugin.springsecurity', 'X') == 'plugins'
        assert modulo('grails.gorm.transactions', 'X') == 'gorm'
        assert modulo('org.grox.data.ast', 'WhereQueryTransformation') == 'gorm'
        assert modulo('org.grox.ast', 'ArtefactTransformation') == 'grails'
        assert modulo('grails.plugins', 'Plugin') == 'grails' && modulo('grails.plugins.redis', 'X') == 'plugins'
        assert modulo('org.springframework.security.core', 'Authentication') == 'security'
        assert modulo('org.springframework.security.core.userdetails', 'X') == 'plugins'
    }
}
