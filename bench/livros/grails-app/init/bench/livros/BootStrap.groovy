package bench.livros

/** Os mesmos 20 livros que o bench/run-livros.sh grava no sample-plain pela API. */
class BootStrap {
    def springSecurityService

    def init = { servletContext ->
        Livro.withTransaction {
            (1..20).each { n ->
                new Livro(titulo: String.format('Livro %02d', n), autor: "Autor ${n % 5}", ano: 1990 + n).save(failOnError: true)
            }
            Role admin = new Role(authority: 'ROLE_ADMIN').save(failOnError: true)
            User user = new User(username: 'admin', password: springSecurityService.encodePassword('admin')).save(failOnError: true)
            new UserRole(user: user, role: admin).save(failOnError: true)
        }
    }
    def destroy = {}
}
