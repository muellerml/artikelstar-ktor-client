package de.muellerml.artikelstar

import de.muellerml.artikelstar.article.ArticleBuilder
import de.muellerml.artikelstar.article.ArticleDto
import de.muellerml.artikelstar.article.ArtikelSender
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json


@ArtikelstarDsl
class Artikelstar internal constructor(
    credentials: Credentials,
    private val category: String?
) {

    constructor(username: String, password: String, category: String?) : this(Credentials(username, password), category)

    private val decoder = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        followRedirects = true
        expectSuccess = true
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(ContentNegotiation) {
            this.json(decoder)
            register(ContentType("text", "plain"), ArtikelstarTextPlainConverter(KotlinxSerializationConverter(decoder)))
        }
    }

    private val articleSender = ArtikelSender(client, credentials)

    @ArtikelstarDsl
    fun newArticle(title: String, text: String, author: String, articleLambda: ArticleBuilder.() -> Unit = {}): ArticleDsl {

        return ArticleDsl(
            ArticleBuilder(category, title, text, author).apply { articleLambda(this) }.build(),
            articleSender
        )
    }

    suspend fun delete(article: ArticleDto) {
        return articleSender.delete(article)
    }

}


@ArtikelstarDsl
data class Username internal constructor(val username: String, internal val builder: ArtikelstarBuilder) {
    @ArtikelstarDsl
    infix fun withPassword(password: String): ArtikelstarBuilder =
        builder.also { it.login = Credentials(this.username, password) }
}


data class Credentials(val username: String, val password: String, val url: String = "https://artikelstar.nb-data.io", val cityId: String = "91f42d33-97ff-48a0-8b54-9bddc1ebcd76", val category: String = "b568f407-801e-4da6-9a21-a5267716d669")

class ArtikelstarTextPlainConverter(private val parent: KotlinxSerializationConverter) : ContentConverter {
    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val myContent = String(content.toInputStream().readBytes())
        return parent.deserialize(charset, typeInfo, ByteReadChannel(myContent.toByteArray()))
    }
}





