// Configuração do compilador Groovy dos módulos do framework (framework/build.gradle): as ASTs globais
// declaradas pelos módulos valem para o código das aplicações, não para o próprio framework.
configuration.disabledGlobalASTTransformations = ['org.grox.ast.ArtefactTransformation', 'org.grox.data.ast.WhereQueryTransformation'] as Set
