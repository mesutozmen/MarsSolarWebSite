package tr.com.marssolar.web

import java.time.Instant

data class Crumb(val name: String, val path: String)

data class PageHead(
    val path: String,
    val description: String,
    val imagePath: String? = null,
    val index: Boolean = true,
    val crumbs: List<Crumb> = emptyList(),
    val product: ShopProduct? = null,
    val showPrices: Boolean = true
)

fun homeHead(): PageHead = PageHead(
    path = "/",
    description = Site.DESCRIPTION,
    imagePath = "/assets/logo-wide.png",
    crumbs = listOf(Crumb("Mars Solar Enerji", "/"))
)

fun categoryHead(category: ShopCategory, parent: ShopCategory?, index: Boolean): PageHead {
    val about = category.blurb.ifBlank { "Fiyat ve stok Mars Solar Enerji vitrininde açık." }
    return PageHead(
        path = "/kategori/${category.slug}",
        description = clip("${category.name}. $about Nusaybin, ${Site.PHONE_DISPLAY}."),
        imagePath = if (category.hasImage) "/medya/kategori/${category.id}?v=${category.updatedAt}" else null,
        index = index,
        crumbs = buildList {
            add(Crumb("Anasayfa", "/"))
            if (parent != null) add(Crumb(parent.name, "/kategori/${parent.slug}"))
            add(Crumb(category.name, "/kategori/${category.slug}"))
        }
    )
}

fun productHead(
    product: ShopProduct,
    category: ShopCategory?,
    parent: ShopCategory?,
    index: Boolean,
    showPrices: Boolean
): PageHead {
    val path = "/urun/${product.slug}"
    val lead = product.summary.ifBlank {
        listOf(product.brand, product.categoryName, product.name).filter { it.isNotBlank() }.joinToString(", ")
    }
    val price = if (showPrices) " ${product.priceLabel}." else ""
    return PageHead(
        path = path,
        description = clip("$lead. Mars Solar Enerji, Nusaybin.$price"),
        showPrices = showPrices,
        imagePath = if (product.photoCount > 0) "/medya/urun/${product.id}/0?v=${product.updatedAt}" else null,
        index = index,
        crumbs = buildList {
            add(Crumb("Anasayfa", "/"))
            if (parent != null) add(Crumb(parent.name, "/kategori/${parent.slug}"))
            if (category != null) add(Crumb(category.name, "/kategori/${category.slug}"))
            add(Crumb(product.name, path))
        },
        product = product
    )
}

fun pageGraph(origin: String, head: PageHead): List<String> {
    if (origin.isBlank()) return emptyList()
    val graph = mutableListOf<String>()
    if (head.crumbs.size > 1) graph += breadcrumbJson(origin, head.crumbs)
    val product = head.product
    if (product != null) graph += productJson(origin, head)
    if (head.path == "/") {
        graph += organizationJson(origin)
        graph += websiteJson(origin)
    }
    if (head.path == "/sss") graph += faqJson(origin)
    return graph
}

fun sitemapXml(origin: String, categories: List<ShopCategory>, products: List<ShopProduct>): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8"?>""")
    append("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
    fun url(path: String, updatedAt: Long = 0) {
        append("<url><loc>${"$origin$path".xml()}</loc>")
        if (updatedAt > 0) append("<lastmod>${Instant.ofEpochMilli(updatedAt)}</lastmod>")
        append("</url>")
    }
    url("/")
    url("/sss")
    url("/uyelik-sozlesmesi")
    url("/aydinlatma-metni")
    categories.forEach { url("/kategori/${it.slug}", it.updatedAt) }
    products.filter { it.active }.forEach { url("/urun/${it.slug}", it.updatedAt) }
    append("</urlset>")
}

fun robotsTxt(origin: String): String = """
    User-agent: *
    Allow: /
    Disallow: /yonetim
    Disallow: /giris
    Disallow: /uye-ol
    Disallow: /hesap
    Disallow: /sepet
    Disallow: /ara
    Sitemap: $origin/sitemap.xml
""".trimIndent() + "\n"

private fun organizationJson(origin: String): String = """
    {"@context":"https://schema.org","@type":"LocalBusiness","name":"Mars Solar Enerji","url":${origin.json()},"telephone":"${Site.PHONE_TEL}","image":${"$origin/assets/logo-wide.png".json()},"address":{"@type":"PostalAddress","streetAddress":"Devrim, Midyat Yolu Cd. Kaçmaz Apt No: 224/A","addressLocality":"Nusaybin","addressRegion":"Mardin","addressCountry":"TR"},"sameAs":[${Site.INSTAGRAM.json()},${Site.FACEBOOK.json()}]}
""".trim()

private fun websiteJson(origin: String): String = """
    {"@context":"https://schema.org","@type":"WebSite","name":"Mars Solar Enerji","url":${origin.json()},"potentialAction":{"@type":"SearchAction","target":${"$origin/ara?q={search_term_string}".json()},"query-input":"required name=search_term_string"}}
""".trim()

private fun breadcrumbJson(origin: String, crumbs: List<Crumb>): String = buildString {
    append("""{"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[""")
    crumbs.forEachIndexed { index, crumb ->
        if (index > 0) append(',')
        append("""{"@type":"ListItem","position":${index + 1},"name":${crumb.name.json()},"item":${"$origin${crumb.path}".json()}}""")
    }
    append("]}")
}

private fun productJson(origin: String, head: PageHead): String {
    val product = head.product ?: return ""
    val priceLira = product.priceKurus / 100
    val cents = (product.priceKurus % 100).toString().padStart(2, '0')
    val availability = if (product.inStock) "https://schema.org/InStock" else "https://schema.org/OutOfStock"
    val brand = if (product.brand.isBlank()) "" else ""","brand":{"@type":"Brand","name":${product.brand.json()}}"""
    val image = head.imagePath?.let { ""","image":[${"$origin$it".json()}]""" }.orEmpty()
    val offer = if (!head.showPrices) "" else ""","offers":{"@type":"Offer","url":${"$origin${head.path}".json()},"priceCurrency":"TRY","price":"$priceLira.$cents","availability":"$availability"}"""
    return """{"@context":"https://schema.org","@type":"Product","name":${product.name.json()},"description":${head.description.json()},"sku":${product.id.json()}$image$brand$offer}"""
}

private fun faqJson(origin: String): String = buildString {
    append("""{"@context":"https://schema.org","@type":"FAQPage","url":${"$origin/sss".json()},"mainEntity":[""")
    QUESTIONS.forEachIndexed { index, (question, answer) ->
        if (index > 0) append(',')
        append("""{"@type":"Question","name":${question.json()},"acceptedAnswer":{"@type":"Answer","text":${answer.json()}}}""")
    }
    append("]}")
}

private fun clip(text: String): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.length <= 160) return clean
    val cut = clean.take(157)
    val word = cut.substringBeforeLast(' ')
    return (if (word.length > 80) word else cut).trimEnd() + "…"
}

private fun String.json(): String = buildString {
    append('"')
    for (char in this@json) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n', '\r' -> append(' ')
            '<' -> append("\\u003c")
            else -> append(char)
        }
    }
    append('"')
}

private fun String.xml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
