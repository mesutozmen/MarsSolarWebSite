package tr.com.marssolar.web

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

data class ShopCategory(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val blurb: String = "",
    val sort: Int = 0,
    val hidden: Boolean = false,
    val parentId: String = "",
    val hasImage: Boolean = false,
    val updatedAt: Long = 0
)

data class ShopProduct(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val summary: String = "",
    val description: String = "",
    val priceKurus: Long = 0,
    val categoryId: String = "",
    val categoryName: String = "",
    val brand: String = "",
    val stockQty: Int = 0,
    val featured: Boolean = false,
    val active: Boolean = true,
    val hasImage: Boolean = false,
    val imageCount: Int = 0,
    val updatedAt: Long = 0
) {
    val photoCount: Int get() = if (imageCount > 0) imageCount else if (hasImage) 1 else 0
    val inStock: Boolean get() = stockQty > 0
    val priceLabel: String get() = priceKurus.toLira()
}

data class ProductDraft(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val priceKurus: Long,
    val categoryId: String,
    val brand: String,
    val stockQty: Int,
    val featured: Boolean,
    val active: Boolean,
    val images: List<ByteArray> = emptyList(),
    val removeImageIndexes: List<Int> = emptyList()
)

data class ShopCustomer(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val firstName: String = "",
    val lastName: String = "",
    val gender: String = "",
    val marketing: Boolean = false
)

object Site {
    const val PHONE_DISPLAY = "0535 324 41 84"
    const val PHONE_TEL = "+905353244184"
    const val PHONE_WA = "905353244184"
    const val TAGLINE = "Mars Solar Enerji — Güneş paneli, invertör ve akü"
    const val DESCRIPTION = "Nusaybin Mars Solar Enerji. Güneş paneli, invertör, akü ve solar sürücü. Fiyatlar açık, stok görünür. 0535 324 41 84."
    const val INSTAGRAM = "https://www.instagram.com/marssolarenergy/"
    const val FACEBOOK = "https://www.facebook.com/profile.php?id=61594742132531"
}

data class CartLine(val product: ShopProduct, val quantity: Int) {
    val lineTotal: Long get() = product.priceKurus * quantity
}

data class CatalogQuery(
    val stockOnly: Boolean = false,
    val featuredOnly: Boolean = false,
    val brands: Set<String> = emptySet(),
    val sort: String = "",
    val columns: Int = 4
) {
    fun apply(products: List<ShopProduct>): List<ShopProduct> {
        val filtered = products.filter { product ->
            (!stockOnly || product.inStock) &&
                (!featuredOnly || product.featured) &&
                (brands.isEmpty() || product.brand.brandKey() in brands)
        }
        return when (sort) {
            "price-asc" -> filtered.sortedBy { it.priceKurus }
            "price-desc" -> filtered.sortedByDescending { it.priceKurus }
            "new" -> filtered.sortedByDescending { it.updatedAt }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    val narrowed: Boolean
        get() = stockOnly || featuredOnly || brands.isNotEmpty() || sort.isNotBlank() || columns != 4
}

fun String.brandKey(): String = trim().lowercase(Locale.forLanguageTag("tr-TR"))

fun String.shopKey(): String = trim().lowercase(Locale.forLanguageTag("tr-TR"))
    .replace('ı', 'i')
    .replace('ö', 'o')
    .replace('ü', 'u')
    .replace('ş', 's')
    .replace('ğ', 'g')
    .replace('ç', 'c')

fun Long.toLira(): String {
    val lira = this / 100
    val cents = (this % 100).toString().padStart(2, '0')
    val grouped = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(lira)
    return "$grouped,$cents TL"
}

fun parseLira(raw: String): Long? {
    var text = raw.trim().lowercase(Locale.forLanguageTag("tr-TR")).removeSuffix("tl").trim()
    text = text.filter { it.isDigit() || it == ',' || it == '.' }
    if (text.isEmpty()) return null
    if (',' in text) text = text.replace(".", "").replace(',', '.')
    val value = text.toBigDecimalOrNull() ?: return null
    if (value < BigDecimal.ZERO || value > BigDecimal("10000000")) return null
    return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

fun slugify(value: String): String {
    val folded = value
        .replace('İ', 'i')
        .replace('I', 'i')
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .map { char ->
            when (char) {
                'ç' -> 'c'
                'ğ' -> 'g'
                'ı' -> 'i'
                'ö' -> 'o'
                'ş' -> 's'
                'ü' -> 'u'
                else -> char
            }
        }
        .joinToString("")
    return folded.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "urun" }
}
