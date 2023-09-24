package de.muellerml.artikelstar.article

import java.util.*

data class Article internal constructor(
    val title: String,
    val category: String?,
    val text: String,
    val pictures: List<Picture>,
    val author: String,
) {
    val id = UUID.randomUUID().toString()
}
