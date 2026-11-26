package org.grox.session

import java.security.SecureRandom

class SessionIds {

    private static final SecureRandom RANDOM = new SecureRandom()

    static String next() {
        byte[] bytes = new byte[24]
        RANDOM.nextBytes(bytes)
        Base64.urlEncoder.withoutPadding().encodeToString(bytes)
    }
}
