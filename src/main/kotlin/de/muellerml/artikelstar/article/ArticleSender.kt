package de.muellerml.artikelstar.article

import de.muellerml.artikelstar.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.*

@Serializable
data class LoginResponse(
    val success: Boolean,
    val token: Token,
) {
    @Serializable
    data class Token(
        val token: String,
    )
}

class ArtikelSender internal constructor(private val httpClient: HttpClient, private val credentials: Credentials) {

    private val token: String by lazy { login(credentials) }


    private fun login(credentials: Credentials): String = runBlocking {
        val result =
            httpClient.get(urlString = "${credentials.url}/login/login?username=${credentials.username}&password=${credentials.password}") {
                accept(ContentType.Any)
            }
        val response = result.body<LoginResponse>()
        if (!response.success) {
            error("Login failed with response: $result")
        } else {
            response.token.token
        }
    }

    @Serializable
    data class SaveArticleRequest(
        val article: SaveArticle,
        val placements: List<Placements>,
    ) {
        @Serializable
        data class SaveArticle(
            val id: String,
            val issueId: String,
            val categoryId: String,
            val publishingObjectId: String,
            val images: List<SaveArticlePicture>,
            val title: String,
            val text: String,
            val isMaster: Boolean = true,
            val useTitle: Boolean = true,
        )

        @Suppress("unused")
        @Serializable
        data class SaveArticlePicture(
            val authorAlias: String,
            val newsId: String,
            val id: String,
            val filename: String,
            val fileSize: Int,
            val caption: String,
        ) {

            val layoutSize: Int = 2
            val name: String = filename
            val createdOn = Instant.now().toString()
            val modifiedOn = Instant.now().toString()
            val displayOrder = 0
            val mediatype = 0
            val originalFileName = filename

            constructor(uploadedImage: UploadedImage, authorAlias: String, caption: String) : this(
                authorAlias = authorAlias,
                newsId = uploadedImage.newsId,
                id = uploadedImage.id,
                filename = uploadedImage.filename,
                fileSize = uploadedImage.fileSize,
                caption = caption,
            )
        }

        @Serializable
        data class Placements(
            val category_id: String,
            val issue_id: String,
            val publishingObject_id: String,
        )
    }

    @Serializable
    data class SaveArticleResponse(
        val data: List<SavedArticle>
    ) {
        @Serializable
        data class SavedArticle(
            val newsId: String
        )
    }

    @Serializable
    data class IssuesResponse(
        val data: IssuesResponseData
    ) {
        @Serializable
        data class IssuesResponseData(
            val weeknumbersForDropdown: List<Issue>
        )

        @Serializable
        data class Issue(
            val year: Int,
            val week: Int,
            val id: String,
            val name: String,
        )
    }

    @Serializable
    data class UploadedImageResponse(
        val data: UploadedImage,
    )

    @Serializable
    data class UploadedImage(
        val id: String,
        val newsId: String,
        val filename: String,
        val fileSize: Int,
    )


    data class IssueDate(private var localDate: LocalDate) {
        private val weekFields: WeekFields = WeekFields.of(Locale.getDefault())
        val weekOfYear
            get() = localDate.get(weekFields.weekOfWeekBasedYear())
        val year
            get() = localDate.year

        fun increment() {
            localDate = localDate.with(TemporalAdjusters.ofDateAdjuster { it.plusDays(7) })
        }
    }

    suspend fun send(article: Article): ArticleDto = runCatching {
        val issueId = retrieveNextIssue(LocalDate.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.next(DayOfWeek.TUESDAY)))

        val uploadedPictures = article.pictures.map { picture -> uploadPicture(article, picture) }

        val response = saveArticle(article, issueId, uploadedPictures)

        ArticleDto(response.body<SaveArticleResponse>().data.first().newsId)
    }
        .onFailure { delete(ArticleDto(article.id)) }
        .getOrThrow()

    private suspend fun retrieveNextIssue(localDate: LocalDate): String {
        val nextIssue = IssueDate(localDate)
        var issueId: String?
        do {
            val issuesResponse =
                httpClient.get("${credentials.url}/article/search?year=${nextIssue.year}") {
                    header("Authorization", "Bearer $token")
                }
            issueId = issuesResponse.body<IssuesResponse>().data.weeknumbersForDropdown.firstOrNull {
                it.year == nextIssue.year && it.week == nextIssue.weekOfYear
            }?.id
            nextIssue.increment()
        } while (issueId == null)
        return issueId
    }

    private suspend fun saveArticle(
        article: Article,
        issueId: String,
        uploadedPictures: List<Pair<UploadedImage, Picture>>
    ): HttpResponse {
        val requestBody = SaveArticleRequest(
            article = SaveArticleRequest.SaveArticle(
                id = article.id,
                issueId = issueId,
                categoryId = credentials.category,
                images = uploadedPictures.map { (uploadedPicture, picture) ->
                    SaveArticleRequest.SaveArticlePicture(
                        uploadedPicture,
                        authorAlias = article.author,
                        caption = picture.caption
                    )
                },
                title = article.title,
                text = article.text,
                publishingObjectId = credentials.cityId
            ),
            placements = listOf(
                SaveArticleRequest.Placements(
                    category_id = credentials.category,
                    issue_id = issueId,
                    publishingObject_id = credentials.cityId
                )
            )
        )

        val response = httpClient.post("${credentials.url}/article/saveAndPlaceArticle") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(requestBody)
            accept(ContentType.Any)
        }
        return response
    }

    private suspend fun uploadPicture(
        article: Article,
        picture: Picture
    ): Pair<UploadedImage, Picture> {
        val parameters = formData {
            append("articleId", article.id)
            append("isMaster", "true")
            append("file", picture.bytes, Headers.build {
                append(HttpHeaders.ContentType, picture.contentType.toString())
                append(HttpHeaders.ContentDisposition, "filename=\"${picture.filename}\"")
            })
            append("imageLayoutSize", 2)
        }
        val result = httpClient.submitFormWithBinaryData(
            "${credentials.url}/article/uploadImage",
            parameters
        ) {
            header("Authorization", "Bearer $token")
            accept(ContentType.Any)
        }

        return result.body<UploadedImageResponse>().data to picture
    }

    suspend fun delete(article: ArticleDto) {
        login(credentials)
        val dto = ArticleDeleteDto(article.id)
        httpClient.post("${credentials.url}/article/newsDelete") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(dto)
        }
        httpClient.post("${credentials.url}/article/DoNewsDelete") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(dto)
        }
    }
}

@Serializable
data class ArticleDeleteDto(
    val id: String
)
