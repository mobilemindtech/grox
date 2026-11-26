package org.grox.security

/** Regrava a senha recodificada depois do login, quando {@link PasswordCodec#upgradeEncoding} pede. */
interface PasswordUpdater {
    void updatePassword(GroxUser user, String encoded)
}
