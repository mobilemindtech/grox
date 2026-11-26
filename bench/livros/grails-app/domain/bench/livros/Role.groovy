package bench.livros

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includes = 'authority')
class Role implements Serializable {
    String authority

    static constraints = {
        authority blank: false, unique: true
    }
}
