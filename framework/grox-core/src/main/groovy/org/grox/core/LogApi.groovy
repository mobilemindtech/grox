package org.grox.core

import org.slf4j.Logger

/** {@code log} em artefatos sem outra API (ex.: {@code BootStrap} em {@code grails-app/init}), como o Grails injeta. */
trait LogApi {
    Logger getLog() { Logs.of(this.getClass()) }
}
