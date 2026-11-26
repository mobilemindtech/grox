package org.springframework.web.multipart

import java.nio.file.Path

/** Interface com o nome da do Spring (usada por apps Grails em command objects e actions). */
interface MultipartFile {
    String getName()

    String getOriginalFilename()

    String getContentType()

    boolean isEmpty()

    long getSize()

    byte[] getBytes()

    InputStream getInputStream()

    void transferTo(File dest)

    void transferTo(Path dest)
}
