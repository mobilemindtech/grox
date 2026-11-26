package org.grox.security.gorm

import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grox.data.Persistence
import org.grox.security.GroxUser
import org.grox.security.SecurityConfig
import org.grox.security.UserDetailsService

/**
 * Usuários em domain classes, como o spring-security-core ({@code s2-quickstart}):
 * {@code User} (username, password, enabled, accountExpired, accountLocked,
 * passwordExpired), {@code Role} (authority) e a junção {@code UserRole} (user, role).
 */
class GormUserDetailsService implements UserDetailsService {

    final SecurityConfig cfg
    final Class<?> userClass
    final Class<?> authorityClass
    final Class<?> joinClass

    GormUserDetailsService(SecurityConfig cfg, ClassLoader loader) {
        this.cfg = cfg
        this.userClass = Class.forName(cfg.userDomainClassName, true, loader)
        this.authorityClass = cfg.authorityClassName ? Class.forName(cfg.authorityClassName, true, loader) : null
        this.joinClass = cfg.authorityJoinClassName ? Class.forName(cfg.authorityJoinClassName, true, loader) : null
    }

    @Override
    GroxUser loadUserByUsername(String username) {
        Persistence.withSession {
            Object user = Gorm.api(userClass).findWhere([(cfg.usernamePropertyName): username])
            user == null ? null : toUser(user)
        }
    }

    GroxUser toUser(Object user) {
        def u = new GroxUser(
                username: user[cfg.usernamePropertyName]?.toString(),
                password: user[cfg.passwordPropertyName]?.toString(),
                enabled: flag(user, cfg.enabledPropertyName, true),
                accountExpired: flag(user, cfg.accountExpiredPropertyName, false),
                accountLocked: flag(user, cfg.accountLockedPropertyName, false),
                passwordExpired: flag(user, cfg.passwordExpiredPropertyName, false),
                id: (Serializable) Gorm.domain(user.getClass()).identity.get(user),
                tenantId: cfg.tenantPropertyName && user.hasProperty(cfg.tenantPropertyName) ? (Serializable) user[cfg.tenantPropertyName] : null)
        u.roles = authorities(user)
        u
    }

    private List<String> authorities(Object user) {
        String nameField = cfg.authorityNameField
        if (joinClass != null) {
            DomainClass join = Gorm.domain(joinClass)
            String userRef = reference(join, userClass)
            String roleRef = reference(join, authorityClass)
            if (!userRef || !roleRef) throw new IllegalStateException("${joinClass.name} precisa de propriedades do tipo ${userClass.simpleName} e ${authorityClass?.simpleName}")
            // uma consulta só (sem inicializar o proxy de cada role)
            return Gorm.api(joinClass).executeQuery("select j.${roleRef}.${nameField} from ${joinClass.name} j where j.${userRef} = :u".toString(),
                    [u: user]).collect { it.toString() }
        }
        if (user.hasProperty(cfg.authoritiesPropertyName)) {
            return ((user[cfg.authoritiesPropertyName] ?: []) as Collection).collect { it instanceof CharSequence ? it.toString() : it[nameField].toString() }
        }
        []
    }

    private static String reference(DomainClass join, Class<?> type) {
        join.persistentProperties.values().find { DomainProperty p -> p.kind == DomainProperty.Kind.MANY_TO_ONE && p.referencedType == type }?.name
    }

    private static boolean flag(Object user, String prop, boolean defaultValue) {
        prop && user.hasProperty(prop) && user[prop] != null ? user[prop] as boolean : defaultValue
    }
}
