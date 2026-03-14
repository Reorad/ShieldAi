package com.example.floatingdot

import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
class SafeBrowsingService {

    private val apiKey = "AIzaSyCvncNC7XXVN073wydw49Xj-6rUjzcQw6Y"
    private val client = OkHttpClient()

    fun checkUrl(url: String, callback: (Boolean) -> Unit) {

        val json = """
        {
          "client": {
            "clientId": "sms-scanner",
            "clientVersion": "1.0"
          },
          "threatInfo": {
            "threatTypes": ["MALWARE","SOCIAL_ENGINEERING"],
            "platformTypes": ["ANY_PLATFORM"],
            "threatEntryTypes": ["URL"],
            "threatEntries": [
              {"url": "$url"}
            ]
          }
        }
        """.trimIndent()

        val requestBody = json.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url("https://safebrowsing.googleapis.com/v4/threatMatches:find?key=$apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {

                val body = response.body?.string()

                if (body != null) {

                    val json = JSONObject(body)

                    val threatDetected = json.has("matches")

                    callback(threatDetected)

                } else {
                    callback(false)
                }
            }
        })
    }
}