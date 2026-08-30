package hq.playfoundry.questgrow.ui

/** Client-side starter routine set — Persian titles + emoji, created directly
 *  (not via the backend's English `seed-starters`). The parent picks a few at
 *  onboarding and can add/remove later. */
data class Starter(val id: String, val title: String, val icon: String, val points: Int)

val STARTERS: List<Starter> = listOf(
    Starter("teeth", "مسواک زدن", "🪥", 10),
    Starter("get-dressed", "لباس پوشیدن", "👕", 10),
    Starter("tidy-up", "جمع کردن اسباب‌بازی‌ها", "🧸", 10),
    Starter("wash-hands", "دست شستن", "🧼", 5),
    Starter("make-bed", "مرتب کردن تخت", "🛏️", 10),
    Starter("read", "کتاب خواندن", "📚", 10),
    Starter("shoes", "کفش پوشیدن", "👟", 5),
    Starter("water-plant", "آب دادن به گل", "🪴", 5),
    Starter("pack-bag", "آماده کردن کیف", "🎒", 10),
    Starter("help-table", "کمک برای چیدن میز", "🍽️", 10),
)
