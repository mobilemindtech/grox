package bench.livros

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includes = ['user', 'role'])
class UserRole implements Serializable {
    User user
    Role role

    static mapping = {
        id composite: ['user', 'role']
        version false
    }
}
