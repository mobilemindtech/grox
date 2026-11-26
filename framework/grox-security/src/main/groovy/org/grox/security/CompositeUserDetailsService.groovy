package org.grox.security

class CompositeUserDetailsService implements UserDetailsService {

    final List<UserDetailsService> delegates

    CompositeUserDetailsService(List<UserDetailsService> delegates) {
        this.delegates = delegates
    }

    @Override
    GroxUser loadUserByUsername(String username) {
        for (UserDetailsService s : delegates) {
            GroxUser u = s.loadUserByUsername(username)
            if (u != null) return u
        }
        null
    }
}
