package sample.plain

import org.grox.GroxApp

/**
 * Ponto de entrada e DSL de projeto: configuração padrão, segurança, rotas e beans da app. O pacote desta
 * classe é o que o Grox varre (domain classes, services e as classes de rotas).
 */
class Aplicacao {

    static app = {
        // valores padrão: o application.yml/groovy, as variáveis de ambiente e o -D passam por cima
        config {
            grox.routes.poolSize = 3
            grox.i18n.defaultLocale = 'en'          // o application.yml define pt-BR, que vale
        }
        security {
            user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
            user 'leitor', password: '{noop}leitor', roles: ['ROLE_USER']
            useBasicAuth = true
            stateless = ['/api/**']
        }
        routes {
            // access: vale como um @Secured da rota, antes das regras de URL
            get('/app/info', access: 'permitAll') {
                render json: [relogio: relogio.zone.id, livros: livroService.todos().size(), poolSize: config.getProperty('grox.routes.poolSize', Integer)]
            }
            get('/app/admin', access: 'ROLE_ADMIN') { render text: 'só admin' }
            get('/app/falha', access: 'permitAll') { throw new IllegalStateException('falhou de propósito') }
            handlers LivrosApi
            // interceptores na raiz do static app valem para todas as rotas da aplicação (Rotas e LivrosApi inclusive)
            before('/relatorios/**') { if (params.bloquear) render status: 423, text: 'relatórios bloqueados' }
            error('/app/**') { Throwable e -> render status: 503, json: [erro: e.message] }
        }
        beans {
            relogio = java.time.Clock.systemUTC()
        }
        // depois dos services e antes do servidor; destroy no desligamento (também há a classe BootStrap)
        bootstrap {
            init {
                environments { test { livroService.salvar(titulo: 'O Guarani', autor: 'Alencar', ano: 1857) } }
            }
            destroy { log.info 'sample-plain encerrado' }
        }
    }

    static void main(String[] args) {
        GroxApp.run(Aplicacao, args)
    }
}
