package de.muellerml.artikelstar.article

import de.muellerml.artikelstar.ArtikelstarDsl
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.util.*
data class ArticleDto(
    val id: String
)


@ArtikelstarDsl
class Picture(val bytes: ByteArray, val contentType: ContentType, val filename: String, val caption: String = "")

@ArtikelstarDsl
data class ArticleBuilder internal constructor(
        var category: String?,
        val title: String,
        val text: String,
        val author: String,
        private val pictures: MutableList<Picture> = mutableListOf()
) {
    @ArtikelstarDsl
    fun picture(bytes: ByteArray, fileName: String, contentType: ContentType, caption: String? = null) {
        pictures += Picture(bytes, contentType, fileName, caption ?: "")
    }

    internal fun build() : Article {
        return Article(
            title = title,
            category = category,
            pictures = pictures,
            text = text,
            author = author
        )
    }
}

