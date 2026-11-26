package org.springframework.core.io

/** Compatibilidade com o {@code Resource} do Spring (recurso com {@code inputStream}). */
interface Resource {
    InputStream getInputStream()

    boolean exists()

    String getFilename()

    long contentLength()
}
