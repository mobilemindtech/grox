package sample.auth

import org.grox.security.Passwords

/** Usuário (convenção do s2-quickstart do spring-security-core). */
class User {
    String username
    String password
    /** Empresa do usuário: resolve o tenant das domain classes MultiTenant. */
    String tenantId
    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    static constraints = {
        username blank: false, unique: true
        password blank: false
        tenantId nullable: true
    }

    static mapping = {
        table 'usuario_seguranca'
    }

    Set<Role> getAuthorities() {
        UserRole.findAllByUser(this)*.role as Set
    }

    def beforeInsert() {
        encodePassword()
    }

    def beforeUpdate() {
        if (isDirty('password')) encodePassword()
    }

    /** Codifica sempre que a senha muda (não confia em prefixos como {noop}, que podem vir do usuário). */
    protected void encodePassword() {
        if (password) password = Passwords.encode(password)
    }
}
