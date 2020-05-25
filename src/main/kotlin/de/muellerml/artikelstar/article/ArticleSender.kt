package de.muellerml.artikelstar.article

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

class ArtikelSender internal constructor(private val httpClient: HttpClient, private val credentials: Credentials) {

    suspend fun login(credentials: Credentials) {
        val result = httpClient.submitFormWithBinaryData<HttpResponse>(url = "https://www.artikelstar.net/Login/Login",
            formData = formData {
                append("Username", credentials.username)
                append("Password", credentials.password)
            })
        if(result.headers["Location"] != "/User/EditProfile" && result.headers["Location"] != "/Home/Index") {
            throw IllegalStateException(result.status.toString() + result.headers["Location"])
        }
    }

    suspend fun send(article: Article) {
        login(credentials)
        val createRes = httpClient.get<String>("https://www.artikelstar.net/Article/Edit")
        val parsedBody = Jsoup.parse(createRes)
        val category = parseCategories(parsedBody, article)
        val id = parsedBody.getElementById("articleTable").attr("data-value")
        val issues = parsedBody.getElementById("issueDropdown").children().map { it.attr("value") }
        val issue = if(issues.isNotEmpty()) {
            issues.first()
        } else throw IllegalStateException("No issue to publish to")
        val mapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val pictures = this.sendPictures(pictures = article.pictures, articleId = id, mapper = mapper)
        val imageRes = pictures.mapValues {(_, value) ->
            "<img class=\"editorImage\" id=\"${value.Id}\" src=\"${value.URL}\" />"
        }
        val text = article.text.replace(Regex("\\{\\{(.*)\\}\\}")) {
            imageRes[it.groupValues[1]] ?: throw IllegalStateException(it.groupValues.first())
        }
        val news = News(
            Id = id,
            IssueId = issue,
            Heading = article.title,
            Text = text,
            CategoryId = category
        )
        val res = httpClient.post<String>("https://www.artikelstar.net/Article/ArticleSaveArticle") {
            body = TextContent(mapper.writeValueAsString(news), contentType = ContentType.Application.Json)
        }
        val res2 = httpClient.post<String>("https://www.artikelstar.net/Article/PlaceArticle") {
            body = TextContent(mapper.writeValueAsString(
                PlaceArticle(
                    news = news
                )
            ), contentType = ContentType.Application.Json)
        }
        val result = mapper.readValue<List<Map<String, String>>>(res2.removeSurrounding("\"").replace("\\", ""))
        println(result)
    }

    private fun parseCategories(parsedBody: Document, article: Article): String {
        val categories = parsedBody.getElementById("categoryDropdown")
            .children()
            .map { it.attr("value") }
        return when {
            article.category != null -> categories.find { it == article.category }
                ?: throw IllegalStateException("No matching category for ${article.category}. " +
                        "Known values: $categories")
            categories.size == 1 -> categories.first()
            else -> throw IllegalStateException("Impossible to decide on categories. There are following known values: $categories")
        }
    }


    private suspend fun sendPictures(pictures: List<Picture>, articleId: String, mapper: ObjectMapper): Map<String, ImageResponse> {
        return pictures.map {
            val imageRes = httpClient.submitFormWithBinaryData<String>(url = "https://www.artikelstar.net/article/UploadImage", formData = formData {
                append("file", filename = it.filename, contentType = ContentType.Application.OctetStream, bodyBuilder = { this.writeFully(it.bytes) })
                append("articleId", articleId)
                append("author", it.author)
                append("originalName", it.filename)
                append("caption", "")
                append("order", 0)
                append("isMaster", "true")
                append("fileType", "image")
                append("imageLayoutSize", 2)
            })
            it.identifier to mapper.readValue<ImageResponse>(imageRes.removeSurrounding("\"").replace("\\", ""))
        }.toMap()


    }
}


data class News(
    val AuthorAlias: String = "HSG Homepage",
    val CategoryId: String,
    val Comment: String = "",
    val CorrectionMode: Boolean = false,
    val Heading: String = "Test",
    val Id: String,
    val IssueId: String,
    val NewArticle: Boolean = true,
    val NewsStatus:String = "0",
    val OldText: String = "",
    val PublishingObjectId: String = "91f42d33-97ff-48a0-8b54-9bddc1ebcd76",
    val SerialArticle: Boolean = false,
    val Text: String = "<p>Dies ist ein Test</p>",
    val WasRead: Boolean = false
)

data class PlaceArticle(val ArticleId: String,
                        val authorAlias: String,
                        private val issueId: String,
                        private val categoryId: String,
                        val refs: List<PlaceNews> = listOf(
                            PlaceNews(
                                Article_Id = ArticleId,
                                Issue_Id = issueId,
                                Category_Id = categoryId
                            )
                        )) {
    constructor(news: News) : this(ArticleId = news.Id, authorAlias = news.AuthorAlias, issueId = news.IssueId, categoryId = news.CategoryId)
}
data class PlaceNews(
    val Article_Id: String,
    val Category_Id: String,
    val DisplayOrder: Int = 0,
    val Issue_Id : String,
    val PublishingObject_Id: String = "91f42d33-97ff-48a0-8b54-9bddc1ebcd76")



data class ImageResponse(val Id: String, val URL: String)

