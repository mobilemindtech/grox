package org.grox.core

/**
 * A aplicação: configuração, ambiente e artefatos descobertos por convenção no pacote da classe de
 * aplicação, agrupados por tipo ({@link ArtefactType}, registrados pelos módulos). O bean
 * {@code grailsApplication} é uma fachada sobre ela, do módulo Grails.
 */
class GroxApplication {

    /** O único tipo de artefato do core: os services, que o runtime cria. */
    static final ArtefactType SERVICE = ArtefactType.suffix('Service')

    final Class<?> applicationClass
    final ClassLoader classLoader
    final String basePackage
    /** Nome do ambiente ({@link Env}), fixo durante a vida da aplicação. */
    final String environmentName
    final Config config
    private final Map<String, List<Class<?>>> artefacts = [:]

    GroxApplication(Class<?> applicationClass) {
        this.applicationClass = applicationClass
        this.classLoader = applicationClass.classLoader
        this.basePackage = applicationClass.packageName
        if (!basePackage) throw new IllegalArgumentException('A classe de aplicação precisa estar em um pacote')
        this.environmentName = Env.currentName
        this.config = Config.load(classLoader, environmentName)
    }

    /** Agrupa as classes da aplicação pelos tipos dados (cada classe no primeiro tipo que a aceitar). */
    @groovy.transform.CompileStatic
    void scan(List<ArtefactType> types) {
        List<ArtefactType> ordered = types.toSorted { ArtefactType a, ArtefactType b -> a.precedence <=> b.precedence }
        artefacts.clear()
        Set<String> names = new LinkedHashSet<>()
        for (String pkg : scanPackages()) names.addAll(ClassScanner.scan(classLoader, pkg))
        names.addAll(defaultPackageClasses())
        // laço simples: um closure por classe e por tipo pesava na subida (perfil JFR)
        for (String name : names) {
            Class<?> c = Class.forName(name, false, classLoader)
            boolean concrete = ArtefactType.concrete(c)
            for (ArtefactType t : ordered) {
                if (t.accepts(c, concrete)) {
                    List<Class<?>> list = artefacts.get(t.name)
                    if (list == null) artefacts.put(t.name, list = new ArrayList<Class<?>>())
                    list.add(c)
                    break
                }
            }
        }
    }

    /** Arquivo gerado pelos plugins Gradle com os pacotes dos fontes de {@code grails-app/}. */
    static final String PACKAGE_INDEX = 'META-INF/grox/packages'

    /** Classes de {@code grails-app/} sem {@code package} (linhas {@code class:Nome} dos índices), que existem. */
    @groovy.transform.CompileStatic
    List<String> defaultPackageClasses() {
        Set<String> result = new LinkedHashSet<>()
        for (URL url : Collections.list(classLoader.getResources(PACKAGE_INDEX))) {
            url.getText('UTF-8').readLines().each { String l ->
                String n = l.trim()
                if (n.startsWith('class:') && classLoader.getResource(n.substring(6) + '.class') != null) result << n.substring(6)
            }
        }
        result.toList()
    }

    /**
     * O pacote da classe de aplicação e os dos índices {@link #PACKAGE_INDEX} do classpath (da app e das
     * bibliotecas {@code org.grox.lib}), sem os já cobertos por um pacote pai da lista.
     */
    @groovy.transform.CompileStatic
    List<String> scanPackages() {
        Set<String> all = new TreeSet<>([basePackage])
        for (URL url : Collections.list(classLoader.getResources(PACKAGE_INDEX))) {
            url.getText('UTF-8').readLines().each { String l -> if (l.trim() && !l.trim().startsWith('class:')) all << l.trim() }
        }
        all.findAll { String p -> !all.any { String o -> o != p && p.startsWith(o + '.') } }.toList()
    }

    List<Class<?>> getArtefacts(String type) {
        artefacts[type] ?: []
    }

    /** Todas as classes reconhecidas como artefato, de todos os tipos. */
    List<Class<?>> getAllArtefacts() {
        artefacts.values().flatten() as List<Class<?>>
    }

    boolean isDevelopment() {
        environmentName == Env.DEVELOPMENT
    }
}
