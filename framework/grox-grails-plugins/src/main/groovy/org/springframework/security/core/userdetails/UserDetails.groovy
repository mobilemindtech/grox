package org.springframework.security.core.userdetails

/** Compatibilidade: implementada por {@link org.grox.security.GroxUser}. */
interface UserDetails extends Serializable {
    Collection<?> getAuthorities()
    String getPassword()
    String getUsername()
    boolean isAccountNonExpired()
    boolean isAccountNonLocked()
    boolean isCredentialsNonExpired()
    boolean isEnabled()
}
