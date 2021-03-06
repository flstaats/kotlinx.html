package kotlinx.html.dom

import kotlinx.html.*
import kotlinx.html.consumers.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.xml.sax.InputSource
import java.io.*
import java.util.*
import javax.xml.parsers.*
import javax.xml.transform.*
import javax.xml.transform.dom.*
import javax.xml.transform.stream.*

class HTMLDOMBuilder(val document : Document) : TagConsumer<Element> {
    private val path = arrayListOf<Element>()
    private var lastLeaved : Element? = null
    private val documentBuilder by lazy { DocumentBuilderFactory.newInstance().newDocumentBuilder() }

    override fun onTagStart(tag: Tag) {
        val element = when {
            tag.namespace != null -> document.createElementNS(tag.namespace!!, tag.tagName)
            else -> document.createElement(tag.tagName)
        }

        tag.attributesEntries.forEach {
            element.setAttribute(it.key, it.value)
        }

        if (path.isNotEmpty()) {
            path.last().appendChild(element)
        }

        path.add(element)
    }

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
        if (path.isEmpty()) {
            throw IllegalStateException("No current tag")
        }

        path.last().let { node ->
            if (value == null) {
                node.removeAttribute(attribute)
            } else {
                node.setAttribute(attribute, value)
            }
        }
    }

    override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
        throw UnsupportedOperationException("You can't assign lambda event handler on JVM")
    }

    override fun onTagEnd(tag: Tag) {
        if (path.isEmpty() || path.last().tagName.toLowerCase() != tag.tagName.toLowerCase()) {
            throw IllegalStateException("We haven't entered tag ${tag.tagName} but trying to leave")
        }

        val element = path.removeAt(path.lastIndex)
        element.setIdAttributeName()
        lastLeaved = element
    }

    override fun onTagContent(content: CharSequence) {
        if (path.isEmpty()) {
            throw IllegalStateException("No current DOM node")
        }

        path.last().appendChild(document.createTextNode(content.toString()))
    }

    override fun onTagContentEntity(entity: Entities) {
        if (path.isEmpty()) {
            throw IllegalStateException("No current DOM node")
        }

        path.last().appendChild(document.createEntityReference(entity.name))
    }

    override fun finalize() = lastLeaved ?: throw IllegalStateException("No tags were emitted")

    override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {
        UnsafeImpl.block()
    }

    val UnsafeImpl = object : Unsafe {
        override operator fun String.unaryPlus() {
            val element = documentBuilder
                .parse(InputSource(StringReader(this)))
                .documentElement
            val importNode = document.importNode(element, true)
            path.last().appendChild(importNode)
        }
    }

    private fun Element.setIdAttributeName() {
        if (hasAttribute("id")) {
            setIdAttribute("id", true)
        }
    }
}

public fun Document.createHTMLTree() : TagConsumer<Element> = HTMLDOMBuilder(this)
public val Document.create : TagConsumer<Element>
    get() = HTMLDOMBuilder(this)

public fun Node.append(block : TagConsumer<Element>.() -> Unit) : List<Element> = ArrayList<Element>().let { result ->
    ownerDocumentExt.createHTMLTree().onFinalize { it, partial -> if (!partial) {appendChild(it); result.add(it)} }.block()

    result
}

public val Node.append: TagConsumer<Element>
    get() = ownerDocumentExt.createHTMLTree().onFinalize { it, partial -> if (!partial) { appendChild(it) } }

private val Node.ownerDocumentExt: Document
    get() = when {
        this is Document -> this
        else -> ownerDocument ?: throw IllegalArgumentException("node has no ownerDocument")
    }

public fun createHTMLDocument() : TagConsumer<Document> = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().let {
    document -> HTMLDOMBuilder(document).onFinalizeMap { it, partial -> if (!partial) {document.appendChild(it)}; document }
}

public inline fun document(block : Document.() -> Unit) : Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().let { document ->
    document.block()
    document
}

public fun Writer.write(document : Document, prettyPrint : Boolean = true) : Writer {
    write("<!DOCTYPE html>\n")
    write(document.documentElement, prettyPrint)
    return this
}

public fun Writer.write(element: Element, prettyPrint : Boolean = true) : Writer {
    val transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    transformer.setOutputProperty(OutputKeys.METHOD, "html");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

    if (prettyPrint) {
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    }

    transformer.transform(DOMSource(element), StreamResult(this))
    return this
}

public fun Element.serialize(prettyPrint : Boolean = true) : String = StringWriter().let { it.write(this, prettyPrint).toString() }
public fun Document.serialize(prettyPrint : Boolean = true) : String = StringWriter().let { it.write(this, prettyPrint).toString() }