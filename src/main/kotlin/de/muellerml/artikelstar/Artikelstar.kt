package de.muellerml.artikelstar

import de.muellerml.artikelstar.article.ArticleBuilder
import de.muellerml.artikelstar.article.ArtikelSender
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging


@ArtikelstarDsl
class Artikelstar(credentials: Credentials,
                  private val category: String?) {

    private val client = HttpClient(CIO) {
        followRedirects = true
        expectSuccess = false
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
    }

    private val articleSender = ArtikelSender(client, credentials)

    @ArtikelstarDsl
    fun newArticle(articleLambda: ArticleBuilder.() -> Unit): ArticleDsl {

        return ArticleDsl(ArticleBuilder(category).apply { articleLambda(this) }.build(), articleSender)
    }

}


@ArtikelstarDsl
data class Username internal constructor(val username: String, internal val builder: ArtikelstarBuilder) {
    @ArtikelstarDsl
    infix fun withPassword(password: String): ArtikelstarBuilder
            = builder.also { it.login = Credentials(this.username, password) }
}



data class Credentials(val username: String, val password: String)





