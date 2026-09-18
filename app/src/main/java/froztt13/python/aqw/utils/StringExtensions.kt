package froztt13.python.aqw.utils

private val ANSI_REGEX =
    Regex("""(?:\u001B|\x1B)\[[0-9;]*[a-zA-Z]|\[\d{1,3}(?:;\d{1,3})*m|(?:\u001B|\x1B)[@-_]""")

/**
 * Strips ANSI color and format escape codes from a string.
 */
fun String.stripAnsi(): String {
    return this.replace(ANSI_REGEX, "").trim()
}
