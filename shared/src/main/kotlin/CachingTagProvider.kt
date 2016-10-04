package kotlinx.html.consumers

import kotlinx.html.*
import java.util.*

class CachingTagProvider : TagProvider {
    private val tagsCache: MutableMap<String, Tag> = HashMap()

    override fun <T : Tag> instance(tag: String, provider: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return tagsCache.getOrPut(tag) { provider() } as T
    }
}

