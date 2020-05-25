package de.muellerml.artikelstar

import de.muellerml.artikelstar.article.Article
import de.muellerml.artikelstar.article.ArtikelSender
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
    suspend fun andSend() = artikelSender.send(article)
}

fun main() = runBlocking {
    artikelstar {
        loginAs("test") withPassword "test"
        category("b568f407-801e-4da6-9a21-a5267716d669")
    }.newArticle {
        title = "test"
        text = "{{test}}"
        val file = Files.readAllBytes(Paths.get("/home/michael/Downloads/9000.jpeg"))
        picture(file) {
            filename = "test.jpg"
            author = "Michael Müller"
            identifier = "test"

        }
        picture(file) {
            filename = "test.jpg"
            author = "Michael Müller"
        }
    }.andSend()
}
