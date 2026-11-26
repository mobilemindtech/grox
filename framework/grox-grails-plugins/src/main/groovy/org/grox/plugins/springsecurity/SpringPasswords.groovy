package org.grox.plugins.springsecurity

import groovy.transform.CompileStatic
import org.grox.security.GroxUser
import org.grox.security.PasswordCodec
import org.grox.security.PasswordUpdater
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.crypto.password.PasswordEncoder

/** Adapta o {@code passwordEncoder} e o {@code userDetailsPasswordService} do Spring Security às interfaces do Grox. */
@CompileStatic
class SpringPasswords {

    static class Codec implements PasswordCodec {
        final PasswordEncoder target

        Codec(PasswordEncoder target) {
            this.target = target
        }

        String encode(String raw) { target.encode(raw) }

        boolean matches(String raw, String encoded) { target.matches(raw, encoded) }

        boolean upgradeEncoding(String encoded) { target.upgradeEncoding(encoded) }
    }

    static class Updater implements PasswordUpdater {
        final UserDetailsPasswordService target

        Updater(UserDetailsPasswordService target) {
            this.target = target
        }

        void updatePassword(GroxUser user, String encoded) {
            target.updatePassword(new org.springframework.security.core.userdetails.User(user.username, user.password, user.roles), encoded)
        }
    }

    /** O {@code passwordEncoder} da aplicação, ou o que usa o algoritmo configurado. */
    static PasswordEncoder encoder(PasswordCodec custom) {
        custom instanceof Codec ? ((Codec) custom).target : BcryptPasswordEncoder.INSTANCE
    }
}
