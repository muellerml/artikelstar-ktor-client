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
import java.time.LocalDateTime

@Serializable
data class LoginResponse(
    val data: LoginData,
) {
    @Serializable
    data class LoginData(
        val token: Tokens,
    )

    @Serializable
    data class Tokens(
        val print: PrintToken,
    )

    @Serializable
    data class PrintToken(
        val token: String,
    )
}

class ArtikelSender internal constructor(private val httpClient: HttpClient, private val credentials: Credentials) {

    private val token: String by lazy { login(credentials) }


    private fun login(credentials: Credentials): String = runBlocking {
        val result = httpClient.post(urlString = "${credentials.authUrl}/admin-proxy/login") {
            setBody(mapOf(
                "username" to credentials.username,
                "password" to credentials.password,
            ))
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
        result.body<LoginResponse>().data.token.print.token
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
            val id: String,
            val newsId: String,
            val filename: String,
            val name: String,
            val originalFileName: String,
            val fileSize: Int,
            val authorAlias: String,
            val authorTitle: String,
            val caption: String,
            val captionTitle: String,
            val alternative: String,
            val layoutSize: Int,
            val displayOrder: Int,
            val mediatype: Int,
            val createdOn: String,
            val modifiedOn: String,
            val width: Int,
            val height: Int,
            val url: String,
            val downloadUrl: String,
            val fileSize2: Int = fileSize,
            val _isNew: Boolean = true,
        ) {
            constructor(uploadedImage: UploadedImage, authorAlias: String, caption: String, alternative: String = caption) : this(
                id = uploadedImage.id,
                newsId = uploadedImage.newsId,
                filename = uploadedImage.filename,
                name = uploadedImage.filename,
                originalFileName = uploadedImage.originalFileName,
                fileSize = uploadedImage.fileSize,
                authorAlias = authorAlias,
                authorTitle = authorAlias,
                caption = caption,
                captionTitle = caption,
                alternative = alternative,
                layoutSize = uploadedImage.layoutSize,
                displayOrder = uploadedImage.displayOrder,
                mediatype = uploadedImage.mediatype,
                createdOn = uploadedImage.createdOn,
                modifiedOn = uploadedImage.modifiedOn,
                width = uploadedImage.width,
                height = uploadedImage.height,
                url = uploadedImage.url,
                downloadUrl = uploadedImage.downloadUrl,
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
            val issues: List<Issue>
        )

        @Serializable
        data class Issue(
            val year: Int,
            val weekNumber: Int,
            val id: String,
            val name: String,
            val authorDeadline: String,
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
        val originalFileName: String,
        val fileSize: Int,
        val layoutSize: Int,
        val displayOrder: Int,
        val mediatype: Int,
        val createdOn: String,
        val modifiedOn: String,
        val width: Int,
        val height: Int,
        val url: String,
        val downloadUrl: String,
    )


    suspend fun send(article: Article): ArticleDto = runCatching {
        val issueId = retrieveNextIssue()

        println(article.pictures)

        val uploadedPictures = article.pictures.map { picture -> uploadPicture(article, picture) }

        val response = saveArticle(article, issueId, uploadedPictures)

        ArticleDto(response.body<SaveArticleResponse>().data.first().newsId)
    }
        .onFailure { delete(ArticleDto(article.id)) }
        .getOrThrow()

    private suspend fun retrieveNextIssue(): String {
        val issuesResponse = httpClient.get("${credentials.url}/article/getIssuesForCity?cityId=${credentials.cityId}") {
            header("Authorization", "Bearer $token")
        }
        val now = LocalDateTime.now()
        return issuesResponse.body<IssuesResponse>().data.issues
            .filter { LocalDateTime.parse(it.authorDeadline) > now }
            .minByOrNull { it.authorDeadline }
            ?.id
            ?: error("No issue with open author deadline found")
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
                text = article.text.replace("[\\t\\n\\r]+".toRegex(),"</p><p>"),
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

        println(requestBody)

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
                append(HttpHeaders.ContentDisposition, "filename=\"1.JPG\"")
            })
            append("imageLayoutSize", 2)
        }
        println(picture)
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
