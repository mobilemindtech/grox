package sample

import org.grox.dsl.Routes

/**
 * As mesmas respostas de três actions dos controllers (HomeController.ola, UsuarioController.index e a API
 * /api/usuarios/{id}) como rotas do grox-dsl, para comparar os dois caminhos na mesma aplicação
 * (docs/benchmark.md): /dsl/... em virtual threads e /dsl-ator/... num pool de atores do mesmo tamanho do dos
 * controllers.
 *
 * Os services vêm pelo nome, sem campos tipados: esta classe fica em src/main/groovy (a camada estável da
 * recarga), e um campo com o tipo de um service o prenderia nessa camada.
 */
class BenchRoutes implements Routes {

    Closure routes = {
        [['/dsl', 'virtual'], ['/dsl-ator', 'actor']].each { String prefixo, String execucao ->
            group(prefixo, execution: execucao, poolSize: 16) {
                get('/ola/{nome}', name: "ola_${execucao}", access: 'permitAll') {
                    render text: saudacaoService.saudar(params.nome ?: 'mundo'), contentType: 'text/plain'
                }
                // o modelo do UsuarioController.index, com o que o UsuarioInterceptor acrescenta
                get('/usuarios', name: "usuarios_${execucao}", access: 'permitAll') {
                    render view: 'usuario/index', model: [usuarioList: usuarioService.listar(params), total: usuarioService.contar(params.q),
                                                          interceptadoEm: new Date()]
                }
                get('/api/usuarios/{id}', name: "api_${execucao}", access: 'ROLE_ADMIN') {
                    respond Usuario.get(params.long('id'))   // null: 404, como o show do controller
                }
            }
        }
    }
}
