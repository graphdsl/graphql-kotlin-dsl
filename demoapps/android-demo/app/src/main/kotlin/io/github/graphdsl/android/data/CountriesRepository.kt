package io.github.graphdsl.android.data

import io.github.graphdsl.android.graphql.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val COUNTRIES_API = "https://countries.trevorblades.com/"

data class Country(
    val code: String,
    val name: String,
    val capital: String?,
    val emoji: String,
    val currency: String?,
    val continent: String,
)

class CountriesRepository {

    private val client = OkHttpClient()



    suspend fun fetchCountries(): List<Country> = withContext(Dispatchers.IO) {
        val generatedQuery: String = query {
            countries {
                code
                name
                capital
                emoji
                currency
                continent {
                    name
                }
            }
        }
        val body = JSONObject()
            .put("query", generatedQuery)
            .toString()

        val request = Request.Builder()
            .url(COUNTRIES_API)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}: ${response.message}" }
            parseCountries(response.body?.string() ?: error("Empty response body"))
        }
    }

    private fun parseCountries(json: String): List<Country> {
        val countries = JSONObject(json)
            .getJSONObject("data")
            .getJSONArray("countries")

        return buildList {
            for (i in 0 until countries.length()) {
                val c = countries.getJSONObject(i)
                add(
                    Country(
                        code = c.getString("code"),
                        name = c.getString("name"),
                        capital = c.optString("capital").takeIf { it.isNotBlank() },
                        emoji = c.getString("emoji"),
                        currency = c.optString("currency").takeIf { it.isNotBlank() },
                        continent = c.getJSONObject("continent").getString("name"),
                    )
                )
            }
        }.sortedBy { it.name }
    }
}
