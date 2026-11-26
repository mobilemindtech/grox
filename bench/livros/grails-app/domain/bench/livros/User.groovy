package bench.livros

import groovy.transform.EqualsAndHashCode

/** Usuário no formato do s2-quickstart. */
@EqualsAndHashCode(includes = 'username')
class User implements Serializable {
    String username
    String password
    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    Set<Role> getAuthorities() {
        (UserRole.findAllByUser(this) as List<UserRole>)*.role as Set<Role>
    }

    static constraints = {
        username blank: false, unique: true
        password blank: false
    }

    static mapping = {
        table 'usuario'
        password column: '`password`'
    }
}
