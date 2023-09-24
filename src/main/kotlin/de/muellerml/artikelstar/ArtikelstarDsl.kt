package de.muellerml.artikelstar

import de.muellerml.artikelstar.article.Article
import de.muellerml.artikelstar.article.ArticleDto
import de.muellerml.artikelstar.article.ArtikelSender
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths

@DslMarker
annotation class ArtikelstarDsl

@ArtikelstarDsl
fun artikelstar(lambda: ArtikelstarBuilder.() -> Unit): Artikelstar {
    return ArtikelstarBuilder().apply(lambda).build()
}

@ArtikelstarDsl
data class ArtikelstarBuilder internal constructor(internal var login: Credentials? = null,
                                                   internal var category: String? = null) {
    @ArtikelstarDsl
    infix fun loginAs(username: String) : Username = Username(username, this)
    @ArtikelstarDsl
    fun category(category: String): ArtikelstarBuilder = this.apply { this.category = category }

    internal fun build() : Artikelstar {
        val (login, category) = this
        if(login != null) {
            return Artikelstar(login, category)
        } else {
            throw IllegalStateException("Invalid object $this")
        }
    }
}

@ArtikelstarDsl
data class ArticleDsl(private val article: Article, private val artikelSender: ArtikelSender) {
    suspend fun andSend(): ArticleDto = artikelSender.send(article)
}
