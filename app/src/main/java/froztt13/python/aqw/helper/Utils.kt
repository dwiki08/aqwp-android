package froztt13.python.aqw.helper

object Utils {
    fun normalize(name: String?): String {
        if (name == null) return ""
        return name.trim()
            .replace("`", "'")
            .replace("’", "'")
            .replace("❜", "'")
            .lowercase()
    }
}
