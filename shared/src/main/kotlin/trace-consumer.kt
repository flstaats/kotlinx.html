package kotlinx.html.consumers

import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import java.util.ArrayList
import java.util.Date

class TraceConsumer<out R>(val downstream : TagConsumer<R>) : TagConsumer<R> by downstream {
    private val id = "ID-${Date().getTime() % 16384}"
    private val path = ArrayList<String>(1024)

    override fun <T : Tag> instance(tag: String, provider: () -> T) = downstream.instance(tag, provider)

    override fun onTagStart(tag: Tag) {
        downstream.onTagStart(tag)
        path.add(tag.tagName)

        println("[$id]  open ${tag.tagName} path: ${path.joinToString(" > ")}")
    }

    override fun onTagEnd(tag: Tag) {
        downstream.onTagEnd(tag)
        path.removeAt(path.lastIndex)

        println("[$id] close ${tag.tagName} path: ${path.joinToString(" > ")}")
    }

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
        downstream.onTagAttributeChange(tag, attribute, value)

        println("[$id]     ${tag.tagName}.$attribute changed to $value")
    }

    override fun onTagError(tag: Tag, exception: Throwable) {
        println("[$id] exception in ${tag.tagName}: ${exception.message}")

        downstream.onTagError(tag, exception)
    }

    override fun finalize(): R {
        val v = downstream.finalize()

        println("[$id] finalized: ${v.toString()}")

        return v
    }
}

fun <R> TagConsumer<R>.trace() : TagConsumer<R> = TraceConsumer(this)