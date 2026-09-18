package froztt13.python.aqw.data.util

import froztt13.python.aqw.data.model.DoomAccount
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility for exporting and parsing DoomAccount collections to/from JSON.
 */
object DoomAccountJsonParser {

    fun exportAccountsJson(accounts: List<DoomAccount>): String {
        val arr = JSONArray()
        for (acc in accounts) {
            if (acc.username.isNotBlank()) {
                val obj = JSONObject()
                obj.put("username", acc.username)
                obj.put("password", acc.password)
                obj.put("enabled", acc.enabled)
                arr.put(obj)
            }
        }
        return arr.toString(2)
    }

    fun parseAccountsJson(jsonStr: String): Pair<List<DoomAccount>?, String?> {
        return try {
            val trimmed = jsonStr.trim()
            val importedList = mutableListOf<DoomAccount>()

            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val user = obj.optString("username", "").trim()
                    val pass = obj.optString("password", "").trim()
                    val enabled = obj.optBoolean("enabled", true)
                    if (user.isNotEmpty()) {
                        importedList.add(
                            DoomAccount(
                                username = user,
                                password = pass,
                                enabled = enabled
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("accounts")) {
                    val arr = root.optJSONArray("accounts")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val user = obj.optString("username", "").trim()
                            val pass = obj.optString("password", "").trim()
                            val enabled = obj.optBoolean("enabled", true)
                            if (user.isNotEmpty()) {
                                importedList.add(
                                    DoomAccount(
                                        username = user,
                                        password = pass,
                                        enabled = enabled
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Key-value pairs: "username": "password"
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val user = keys.next().trim()
                        val pass = root.optString(user, "").trim()
                        if (user.isNotEmpty()) {
                            importedList.add(
                                DoomAccount(
                                    username = user,
                                    password = pass,
                                    enabled = true
                                )
                            )
                        }
                    }
                }
            } else {
                return Pair(null, "Invalid file format. Ensure the file is a JSON array or object.")
            }

            if (importedList.isEmpty()) {
                return Pair(null, "No valid accounts found in the JSON file.")
            }

            Pair(importedList, null)
        } catch (e: Exception) {
            Pair(null, "Failed to parse JSON: ${e.message}")
        }
    }
}
