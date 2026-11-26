package org.grox.core

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

/** Lista as classes (não internas) de um pacote e subpacotes, em diretórios ou jars. */
@CompileStatic
class ClassScanner {

    static List<String> scan(ClassLoader loader, String basePackage) {
        String path = basePackage.replace('.', '/')
        Set<String> names = new LinkedHashSet<>()
        for (URL url : Collections.list(loader.getResources(path))) {
            if (url.protocol == 'file') {
                scanDirectory(Paths.get(url.toURI()), basePackage, names)
            } else if (url.protocol == 'jar') {
                scanJar(url, path, names)
            }
        }
        names.findAll { !it.contains('$') }.toList()
    }

    private static void scanDirectory(Path base, String pkg, Set<String> names) {
        Files.walk(base).withCloseable { stream ->
            for (Path p : stream.toList()) {
                if (!p.toString().endsWith('.class')) continue
                String rel = base.relativize(p).toString().replace(File.separator, '.')
                names << (pkg + '.' + rel.substring(0, rel.length() - 6))
            }
        }
    }

    private static void scanJar(URL url, String path, Set<String> names) {
        String file = url.path.substring(5, url.path.indexOf('!'))
        new JarFile(URLDecoder.decode(file, 'UTF-8')).withCloseable { JarFile jar ->
            for (java.util.jar.JarEntry entry : Collections.list(jar.entries())) {
                String n = entry.name
                if (n.startsWith(path + '/') && n.endsWith('.class')) {
                    names << n.substring(0, n.length() - 6).replace('/', '.')
                }
            }
        }
    }
}
