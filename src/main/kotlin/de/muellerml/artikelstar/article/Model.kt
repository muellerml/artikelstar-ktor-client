package de.muellerml.artikelstar.article

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.muellerml.artikelstar.ArtikelstarDsl
import de.muellerml.artikelstar.Credentials
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.utils.io.core.writeFully
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.UUID

data class Article(val title: String,
                   val category: String?,
                   val text: String,
                   val pictures: List<Picture>) {

}


class Picture private constructor(val bytes: ByteArray, val identifier: String, val filename: String, var author: String) {

    @ArtikelstarDsl
    class Builder(private val bytes: ByteArray) {
        var filename: String = UUID.randomUUID().toString()
        var author: String = "unknown"
        var identifier: String = UUID.randomUUID().toString()

        internal fun build(): Picture =
            Picture(
                bytes = bytes,
                identifier = identifier,
                filename = filename,
                author = author
            )
    }
}

@ArtikelstarDsl
data class ArticleBuilder internal constructor(
        var category: String?,
        var title: String? = null,
        var text: String? = null,
        private val pictures: MutableList<Picture> = mutableListOf()
) {
    @ArtikelstarDsl
    fun picture(bytes: ByteArray, lambda: Picture.Builder.() -> Unit) : Unit {
        pictures += Picture.Builder(bytes).apply { lambda(this) }.build()
    }

    internal fun build() : Article {
        val (category, title, text) = this
        if(title != null && text != null) {
            return Article(
                title = title,
                category = category,
                pictures = pictures,
                text = text
            )
        } else {
            throw IllegalStateException("$this")
        }
    }
}

