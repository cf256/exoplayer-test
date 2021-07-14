package no.rikstv.exoplayertest

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class PlayLinks(
    val manifestUrl: String,
    val licenseUrl: String
)

interface PlayApi {
    @GET("/play/1/play/rikstv/{assetId}/format/Dash")
    suspend fun getPlayLinks(@Path("assetId") assetId: Int): PlayLinks
}
