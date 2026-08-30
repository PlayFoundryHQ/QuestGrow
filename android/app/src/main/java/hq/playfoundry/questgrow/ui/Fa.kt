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

/** Today's weekday name in Persian — e.g. "شنبه" — for a friendly greeting. */
fun faWeekdayToday(): String {
    // java.time DayOfWeek: MONDAY=1 … SUNDAY=7
    val names = mapOf(
        1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنجشنبه",
        5 to "جمعه", 6 to "شنبه", 7 to "یکشنبه",
    )
    return names.getValue(java.time.LocalDate.now().dayOfWeek.value)
}
