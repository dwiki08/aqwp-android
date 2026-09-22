package froztt13.python.aqw.data.network

import android.util.Log
import froztt13.python.aqw.domain.model.AqwItem
import froztt13.python.aqw.domain.model.AqwLoginResult
import froztt13.python.aqw.domain.model.AqwServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object AqwHttpApi {

    private const val LOGIN_ENDPOINT = "https://game.aq.com/game/api/login/now?"
    private const val TAG = "AqwHttpApi"

    suspend fun login(username: String, password: String): AqwLoginResult =
        withContext(Dispatchers.IO) {
            val url = URL(LOGIN_ENDPOINT)
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            }

            val requestPayload = JSONObject().apply {
                put("user", username)
                put("option", 1)
                put("pass", password)
            }

            try {
                conn.outputStream.use { os ->
                    os.write(requestPayload.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText =
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"

                val json = JSONObject(responseText)
                if (json.has("login") && !json.isNull("login")) {
                    val loginObj = json.getJSONObject("login")
                    val serversArr = json.optJSONArray("servers") ?: JSONArray()
                    val serversList = mutableListOf<AqwServer>()

                    for (i in 0 until serversArr.length()) {
                        val sObj = serversArr.optJSONObject(i) ?: continue
                        serversList.add(
                            AqwServer(
                                name = sObj.optString("sName", ""),
                                ip = sObj.optString("sIP", ""),
                                port = sObj.optInt("iPort", 5588),
                                maxPlayers = sObj.optInt("iMax", 1000),
                                currentCount = sObj.optInt("iCount", 0),
                                isMemberOnly = sObj.optInt("iUpg", 0) == 1,
                                isOnline = sObj.optInt("bOnline", 1) == 1
                            )
                        )
                    }

                    AqwLoginResult(
                        success = true,
                        userId = loginObj.optInt("userid", 0),
                        token = loginObj.optString("sToken", ""),
                        isMember = loginObj.optInt("iUpg", 0) == 1,
                        servers = serversList
                    )
                } else {
                    val errorMsg = json.optString("sMsg", "Invalid username or password")
                    AqwLoginResult(success = false, errorMessage = errorMsg)
                }
            } catch (e: Exception) {
                AqwLoginResult(success = false, errorMessage = "Connection error: ${e.message}")
            } finally {
                try {
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }
        }

    suspend fun loadBank(charId: Int, token: String): List<AqwItem> = withContext(Dispatchers.IO) {
        var conn: HttpsURLConnection? = null
        try {
            val randomV = "0.${(1000000000000000L..9999999999999999L).random()}"
            val url = URL("https://game.aq.com/game/api/char/bank?v=$randomV")
            conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("ccid", charId.toString())
                setRequestProperty("token", token)
                setRequestProperty("artixmode", "launcher")
                setRequestProperty("X-Requested-With", "ShockwaveFlash/32.0.0.371")
            }
            val postData = "layout%5Bcat%5D=all"
            conn.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val stream = conn.inputStream
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            val list = mutableListOf<AqwItem>()
            for (i in 0 until arr.length()) {
                val itObj = arr.optJSONObject(i) ?: continue
                list.add(
                    AqwItem(
                        itemId = itObj.optInt("ItemID", 0),
                        charItemId = itObj.optInt("CharItemID", 0),
                        name = itObj.optString("sName", ""),
                        qty = itObj.optInt("iQty", 1),
                        maxQty = itObj.optInt("iStk", 1),
                        isCoins = itObj.optInt("bCoins", 0) == 1,
                        isTemp = itObj.optInt("bTemp", 0) == 1
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.d(TAG, "loadBank: $e")
            emptyList()
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}

