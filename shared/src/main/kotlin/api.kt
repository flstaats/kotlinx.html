package kotlinx.html

import org.w3c.dom.events.*
import java.util.*

interface TagConsumer<out R> {
    fun onTagStart(tag: Tag)
    fun onTagAttributeChange(tag: Tag, attribute: String, value: String?)
    fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit)
    fun onTagEnd(tag: Tag)
    fun onTagContent(content: CharSequence)
    fun onTagContentEntity(entity: Entities)
    fun onTagContentUnsafe(block: Unsafe.() -> Unit)
    fun finalize(): R
}

interface Tag {
    val tagName: String
    val consumer: TagConsumer<*>
    val namespace: String?

    val attributes: MutableMap<String, String>
    val attributesEntries: Collection<Map.Entry<String, String>>

    val inlineTag: Boolean
    val emptyTag: Boolean

    operator fun Entities.unaryPlus() : Unit {
        consumer.onTagContentEntity(this)
    }
    operator fun String.unaryPlus() : Unit {
        consumer.onTagContent(this)
    }
}

interface Unsafe {
    operator fun String.unaryPlus()
    operator fun Entities.unaryPlus() = +text
}

interface AttributeEnum {
    val realValue: String
}

fun <T : Tag> T.visit(block: T.() -> Unit) {
    consumer.onTagStart(this)
    this.block()
    consumer.onTagEnd(this)
}

fun <T: Tag, R> T.visitAndFinalize(consumer: TagConsumer<R>, block: T.() -> Unit): R {
    require(this.consumer === consumer)
    visit(block)
    return consumer.finalize()
}

fun attributesMapOf() = emptyMap
fun attributesMapOf(key: String, value: String?): Map<String, String> = when (value) {
    null -> emptyMap
    else -> singletonMapOf(key, value)
}
fun attributesMapOf(vararg pairs: String?): Map<String, String> {
    var result: Map<String, String>? = null

    for (i in 0 .. pairs.size - 1 step 2) {
        val k = pairs[i]
        val v = pairs[i + 1]
        if (k != null && v != null) {
            if (result == null) {
                result = SingletonStringMap(k, v)
            } else if (result is LinkedHashMap) {
                result[k] = v
            } else if (result is SingletonStringMap) {
                val newMap = LinkedHashMap<String, String>(pairs.size - i + 1)
                newMap.put(result.key, result.value)
                newMap[k] = v

                result = newMap
            } else {
                val newMap = LinkedHashMap<String, String>(pairs.size - i + result.size)
                newMap.putAll(result)
                newMap[k] = v

                result = newMap
            }
        }
    }

    return result ?: emptyMap
}
fun singletonMapOf(key: String, value: String): Map<String, String> = SingletonStringMap(key, value)

fun HTMLTag.unsafe(block: Unsafe.() -> Unit): Unit = consumer.onTagContentUnsafe(block)

val emptyMap: Map<String, String> = emptyMap()

class DefaultUnsafe : Unsafe {
    private val sb = StringBuilder()

    override fun String.unaryPlus() {
        sb.append(this)
    }

    override fun toString(): String = sb.toString()
}

private data class SingletonStringMap(override val key: String, override val value: String) : Map<String, String>, Map.Entry<String, String> {
    override val entries: Set<Map.Entry<String, String>>
        get() = setOf(this)
    override val keys: Set<String>
        get() = setOf(key)
    override val size: Int
        get() = 1
    override val values: Collection<String>
        get() = listOf(value)

    override fun containsKey(key: String) = key == this.key
    override fun containsValue(value: String) = value == this.value
    override fun get(key: String): String? = if (key == this.key) value else null
    override fun isEmpty() = false
}
