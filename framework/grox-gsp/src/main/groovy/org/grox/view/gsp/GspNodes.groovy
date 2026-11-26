package org.grox.view.gsp

/** Nós produzidos pelo {@link GspParser}. */
abstract class GspNode {
    int line
}

class TextNode extends GspNode {
    String text
}

/** {@code ${expr}} ou {@code <%= expr %>}. */
class ExprNode extends GspNode {
    String code
}

/** {@code <% code %>}. */
class ScriptletNode extends GspNode {
    String code
}

/** {@code <%@ page import="..." contentType="..." %>}. */
class DirectiveNode extends GspNode {
    String name
    Map<String, String> attrs = [:]
}

/** Valor de atributo: sequência de partes literais (String) e expressões ({@link ExprNode}). */
class AttrValue {
    List<Object> parts = []

    boolean isSingleExpression() {
        parts.size() == 1 && parts[0] instanceof ExprNode
    }

    boolean isAllLiteral() {
        parts.every { it instanceof String }
    }

    String getText() {
        parts.join('')
    }
}

class TagStartNode extends GspNode {
    String namespace
    String name
    Map<String, AttrValue> attrs = [:]
    boolean selfClosing

    String getQualifiedName() {
        "${namespace}:${name}"
    }
}

class TagEndNode extends GspNode {
    String namespace
    String name

    String getQualifiedName() {
        "${namespace}:${name}"
    }
}
