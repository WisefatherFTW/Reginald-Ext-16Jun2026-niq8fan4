package eu.kanade.tachiyomi.animeextension.cs.anikoto

import eu.kanade.tachiyomi.animesource.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import org.jsoup.Jsoup

class Anikoto : AnimeHttpSource() {

    override val name = "Anikoto"
    override val baseUrl = "https://anikoto.cz"
    override val lang = "cs"
    override val supportsLatest = true

    // Unique 19‑digit ID (change if you like)
    override val id: Long = 8392017465218936745

    // ----------------------------------------------------
    //  Popular / homepage
    // ----------------------------------------------------
    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/home/page/$page", headers)
    }

    override fun popularAnimeParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body!!.string())
        val entries = document.select(".post").map { element ->
            SManga.create().apply {
                title = element.select(".entry-title a").text().trim()
                url = element.select("a").first()?.attr("href") ?: ""
                thumbnail_url = element.select("img").first()?.attr("src") ?: ""
            }
        }
        val hasNextPage = document.select(".pagination .next").isNotEmpty()
        return MangasPage(entries, hasNextPage)
    }

    // ----------------------------------------------------
    //  Anime details
    // ----------------------------------------------------
    override fun animeDetailsParse(response: Response): SAnime {
        val document = Jsoup.parse(response.body!!.string())
        return SAnime.create().apply {
            title = document.select("h1.entry-title").text().trim()
            description = document.select(".entry-content p").text()
            genre = document.select(".cat-links a").joinToString(", ") { it.text() }
            status = parseStatus(document.select(".status").text())
            thumbnail_url = document.select(".wp-post-image").attr("src")
        }
    }

    // ----------------------------------------------------
    //  Episodes
    // ----------------------------------------------------
    override fun episodeListRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = Jsoup.parse(response.body!!.string())
        return document.select(".episode-list a").mapIndexed { i, element ->
            SEpisode.create().apply {
                name = element.text().trim()
                episode_number = (i + 1).toFloat()
                url = element.attr("href")
            }
        }.reversed()
    }

    // ----------------------------------------------------
    //  Video extraction
    // ----------------------------------------------------
    override fun videoListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = Jsoup.parse(response.body!!.string())
        // Try iframe first
        val iframeSrc = document.select("iframe").attr("src")
        if (iframeSrc.isNotEmpty()) {
            return listOf(
                Video.create().apply {
                    url = iframeSrc
                    quality = "default"
                    videoUrl = iframeSrc
                }
            )
        }
        // Try direct video tag
        val videoTag = document.select("video source").first()
        if (videoTag != null) {
            return listOf(
                Video.create().apply {
                    url = videoTag.attr("src")
                    quality = videoTag.attr("label").ifEmpty { "default" }
                    videoUrl = url
                }
            )
        }
        return emptyList()
    }

    // ----------------------------------------------------
    //  Latest & search (reuse popular parsing)
    // ----------------------------------------------------
    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response): MangasPage = popularAnimeParse(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl?s=$query&page=$page", headers)
    }

    override fun searchAnimeParse(response: Response): MangasPage = popularAnimeParse(response)

    // ----------------------------------------------------
    //  Helper
    // ----------------------------------------------------
    private fun parseStatus(text: String): Int {
        return when {
            text.contains("Vysílá", true) || text.contains("Probíhá", true) -> SAnime.ONGOING
            text.contains("Dokončeno", true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }
}
