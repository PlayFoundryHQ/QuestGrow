package hq.playfoundry.questgrow.ui

/** Persian (Farsi) presentation helpers — [[DECISION-020]]. */

private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Latin digits in a string → Persian digits. Leaves everything else alone. */
fun String.faDigits(): String = buildString(length) {
    for (c in this@faDigits) append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
}

fun Int.fa(): String = toString().faDigits()

/** "۲ از ۴" */
fun faFraction(done: Int, total: Int): String = "${done.fa()} از ${total.fa()}"

/** "۳ روز" / "۱ روز" — Persian has no plural inflection here, but keep the helper. */
fun faDays(n: Int): String = "${n.fa()} روز"
