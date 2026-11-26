// Configuração em Groovy, mesclada ao application.yml (numa chave repetida, vale a daqui), como no Grails.
// As regras de acesso por caminho no formato que o s2-quickstart gera.
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
        [pattern: '/', access: ['permitAll']],
        [pattern: '/login/**', access: ['permitAll']],
        [pattern: '/sobre', access: ['permitAll']],
        [pattern: '/catalogo', access: ['permitAll']],
        [pattern: '/ajuda', access: ['permitAll']],
        [pattern: '/livros', httpMethod: 'GET', access: ['permitAll']],
        [pattern: '/livros/**', httpMethod: 'GET', access: ['permitAll']],
        [pattern: '/api/livros/**', httpMethod: 'GET', access: ['permitAll']],
        [pattern: '/api/**', access: ['ROLE_ADMIN']],
        [pattern: '/relatorios/**', access: ['ROLE_ADMIN']],
        [pattern: '/**', access: ['isAuthenticated()']]
]

environments {
    test {
        grox.origemDaConfiguracao = 'application.groovy'
    }
}
