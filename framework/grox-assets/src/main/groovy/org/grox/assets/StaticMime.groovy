package org.grox.assets

import org.grox.http.StaticResources

class StaticMime {
    static String of(String ext) {
        StaticResources.MIME[ext] ?: 'application/octet-stream'
    }
}
