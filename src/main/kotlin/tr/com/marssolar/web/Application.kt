package tr.com.marssolar.web

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.sessions.clear
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

data class AdminSession(val email: String)

data class MemberSession(val uid: String, val email: String, val name: String)

fun main() {
    val config = AppConfig.load()
    val store = ShopStore(config)
    val auth = AdminAuth(config)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(Compression)
        install(CallLogging)
        install(Sessions) {
            cookie<AdminSession>("mars_admin") {
                cookie.path = "/"
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "Lax"
                serializer = object : SessionSerializer<AdminSession> {
                    override fun serialize(session: AdminSession) = session.email
                    override fun deserialize(text: String) = AdminSession(text)
                }
                transform(SessionTransportTransformerMessageAuthentication(config.sessionSecret.encodeToByteArray()))
            }
            cookie<MemberSession>("mars_uye") {
                cookie.path = "/"
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "Lax"
                serializer = object : SessionSerializer<MemberSession> {
                    override fun serialize(session: MemberSession) =
                        listOf(session.uid, session.email, session.name.replace("\n", " ")).joinToString("\n")

                    override fun deserialize(text: String): MemberSession {
                        val parts = text.split("\n", limit = 3)
                        return MemberSession(
                            parts.getOrElse(0) { "" },
                            parts.getOrElse(1) { "" },
                            parts.getOrElse(2) { "" }
                        )
                    }
                }
                transform(SessionTransportTransformerMessageAuthentication(config.sessionSecret.encodeToByteArray()))
            }
        }
        install(StatusPages) {
            status(HttpStatusCode.NotFound) { call, _ ->
                call.respondHtml(HttpStatusCode.NotFound) { missingPage(call.siteView(store)) }
            }
        }
        routing {
            staticResources("/assets", "static")
            get("/robots.txt") {
                call.respondText(robotsTxt(call.publicOrigin()), ContentType.Text.Plain)
            }
            get("/sitemap.xml") {
                call.respondText(
                    sitemapXml(call.publicOrigin(), store.categories(), store.products(activeOnly = false)),
                    ContentType.Application.Xml
                )
            }
            get("/") {
                call.respondHtml { homePage(call.siteView(store), store.featured()) }
            }
            get("/kategori/{slug}") {
                val category = store.category(call.parameters["slug"].orEmpty())
                if (category == null) {
                    call.respondHtml(HttpStatusCode.NotFound) { missingPage(call.siteView(store)) }
                } else {
                    val view = call.siteView(store, category.slug)
                    val params = call.request.queryParameters
                    val query = CatalogQuery(
                        stockOnly = params["stok"] == "1",
                        featuredOnly = params["one"] == "1",
                        brands = params.getAll("marka").orEmpty().map { it.brandKey() }.toSet(),
                        sort = params["sirala"].orEmpty(),
                        columns = params["kolon"]?.toIntOrNull()?.coerceIn(2, 4) ?: 4
                    )
                    call.respondHtml { categoryPage(view, category, store.products(), query) }
                }
            }
            get("/urun/{slug}") {
                val product = store.bySlug(call.parameters["slug"].orEmpty())
                if (product == null || !product.active) {
                    call.respondHtml(HttpStatusCode.NotFound) { missingPage(call.siteView(store)) }
                } else {
                    val added = call.request.queryParameters["sepet"] == "1"
                    call.respondHtml { productPage(call.siteView(store), product, added) }
                }
            }
            get("/ara") {
                val query = call.request.queryParameters["q"].orEmpty()
                call.respondHtml { searchPage(call.siteView(store), query, store.search(query)) }
            }
            get("/ara/oneri") {
                val (categories, products) = store.suggest(call.request.queryParameters["q"].orEmpty())
                call.respondText(suggestJson(categories, products), ContentType.Application.Json)
            }
            get("/sepet") {
                call.respondHtml { cartPage(call.siteView(store), call.cartLines(store)) }
            }
            post("/sepet") {
                val fields = call.receiveParameters()
                val id = fields["id"].orEmpty()
                val qty = fields["qty"].orEmpty().toIntOrNull() ?: 1
                call.replaceCart(id, qty)
                val back = fields["back"].orEmpty().takeIf { it.startsWith("/") && !it.startsWith("//") } ?: "/sepet"
                call.respondRedirect(back)
            }
            get("/medya/kategori/{id}") {
                val bytes = store.categoryImage(call.parameters["id"].orEmpty())
                if (bytes == null) call.respond(HttpStatusCode.NotFound)
                else {
                    call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=604800")
                    call.respondBytes(bytes, ContentType.Image.JPEG)
                }
            }
            get("/sss") {
                call.respondHtml { faqPage(call.siteView(store)) }
            }
            get("/medya/urun/{id}") {
                call.respondImage(store, call.parameters["id"].orEmpty(), 0)
            }
            get("/medya/urun/{id}/{index}") {
                call.respondImage(store, call.parameters["id"].orEmpty(), call.parameters["index"]?.toIntOrNull() ?: 0)
            }
            get("/giris") {
                if (call.member() != null) call.respondRedirect("/hesap")
                else call.respondHtml { memberLoginPage(call.siteView(store), null) }
            }
            post("/giris") {
                val fields = call.receiveParameters()
                val account = auth.signIn(fields["email"].orEmpty(), fields["password"].orEmpty())
                if (account == null) {
                    call.respondHtml(HttpStatusCode.Unauthorized) {
                        memberLoginPage(call.siteView(store), "E-posta veya şifre kabul edilmedi.")
                    }
                } else {
                    val profile = store.customer(account.uid)
                    call.sessions.set(MemberSession(account.uid, account.email, profile?.name ?: account.email.substringBefore("@")))
                    call.respondRedirect("/hesap")
                }
            }
            get("/uye-ol") {
                if (call.member() != null) call.respondRedirect("/hesap")
                else call.respondHtml { registerPage(call.siteView(store), null) }
            }
            post("/uye-ol") {
                val fields = call.receiveParameters()
                try {
                    val firstName = fields["firstName"].orEmpty().trim()
                    val lastName = fields["lastName"].orEmpty().trim()
                    if (firstName.length !in 2..40) throw IllegalArgumentException("Ad 2 ile 40 karakter arasında olmalı.")
                    if (lastName.length !in 2..40) throw IllegalArgumentException("Soyad 2 ile 40 karakter arasında olmalı.")
                    val gender = fields["gender"].orEmpty().takeIf { it in setOf("erkek", "kadin", "belirtmem") }.orEmpty()
                    if (fields["sozlesme"] == null) throw IllegalArgumentException("Üyelik sözleşmesini kabul edin.")
                    if (fields["aydinlatma"] == null) throw IllegalArgumentException("Aydınlatma metnini okuduğunuzu işaretleyin.")
                    val phone = normalizePhone(fields["phone"].orEmpty())
                        ?: throw IllegalArgumentException("Telefonu 05xx xxx xx xx biçiminde yazın.")
                    val account = auth.signUp(fields["email"].orEmpty(), fields["password"].orEmpty())
                    val name = "$firstName $lastName"
                    store.saveCustomer(
                        ShopCustomer(
                            id = account.uid,
                            name = name,
                            email = account.email,
                            phone = phone,
                            firstName = firstName,
                            lastName = lastName,
                            gender = gender,
                            marketing = fields["ileti"] != null
                        )
                    )
                    call.sessions.set(MemberSession(account.uid, account.email, name))
                    call.respondRedirect("/hesap")
                } catch (error: IllegalArgumentException) {
                    call.respondHtml(HttpStatusCode.BadRequest) {
                        registerPage(call.siteView(store), error.message)
                    }
                }
            }
            get("/uyelik-sozlesmesi") {
                call.respondHtml { legalPage(call.siteView(store), "Üyelik sözleşmesi", MEMBERSHIP_TEXT) }
            }
            get("/aydinlatma-metni") {
                call.respondHtml { legalPage(call.siteView(store), "Aydınlatma metni", PRIVACY_TEXT) }
            }
            post("/cikis") {
                call.sessions.clear<MemberSession>()
                call.respondRedirect("/")
            }
            get("/hesap") {
                val member = call.member()
                if (member == null) {
                    call.respondRedirect("/giris")
                } else {
                    call.respondHtml { accountPage(call.siteView(store), store.customer(member.uid)) }
                }
            }
            get("/yonetim/giris") {
                if (call.admin() != null) call.respondRedirect("/yonetim")
                else call.respondHtml { loginPage(null) }
            }
            post("/yonetim/giris") {
                val fields = call.receiveParameters()
                val email = fields["email"].orEmpty()
                val password = fields["password"].orEmpty()
                if (auth.accepts(email, password)) {
                    call.sessions.set(AdminSession(email.trim()))
                    call.respondRedirect("/yonetim")
                } else {
                    call.respondHtml(HttpStatusCode.Unauthorized) {
                        loginPage("E-posta veya şifre kabul edilmedi. Süper yönetici hesabını kullanın.")
                    }
                }
            }
            post("/yonetim/cikis") {
                call.sessions.clear<AdminSession>()
                call.respondRedirect("/")
            }
            get("/yonetim") {
                if (!call.requireAdmin()) return@get
                call.respondHtml {
                    adminHome(
                        call.siteView(store),
                        store.products(activeOnly = false),
                        store.categories(includeHidden = true)
                    )
                }
            }
            post("/yonetim/fiyat") {
                if (!call.requireAdmin()) return@post
                store.setShowPrices(call.receiveParameters()["show"] == "1")
                val referer = call.request.headers["Referer"].orEmpty()
                val path = runCatching { java.net.URI(referer).path }.getOrDefault("/yonetim")
                call.respondRedirect(if (path.startsWith("/yonetim")) path else "/yonetim")
            }
            get("/yonetim/urunler") {
                if (!call.requireAdmin()) return@get
                call.respondHtml {
                    adminProducts(
                        call.siteView(store),
                        store.products(activeOnly = false),
                        store.categories(includeHidden = true)
                    )
                }
            }
            get("/yonetim/urun/yeni") {
                if (!call.requireAdmin()) return@get
                call.respondHtml { adminProductForm(call.siteView(store), null, null, store.categories(includeHidden = true)) }
            }
            get("/yonetim/urun/{id}") {
                if (!call.requireAdmin()) return@get
                val product = store.byId(call.parameters["id"].orEmpty())
                if (product == null) call.respondHtml(HttpStatusCode.NotFound) { missingPage(call.siteView(store)) }
                else call.respondHtml {
                    adminProductForm(call.siteView(store), product, null, store.categories(includeHidden = true))
                }
            }
            post("/yonetim/urun") {
                if (!call.requireAdmin()) return@post
                val form = call.formParts()
                val existing = form.fields["id"]?.takeIf { it.isNotBlank() }?.let(store::byId)
                try {
                    val id = store.save(form.toDraft(), store.categories(includeHidden = true))
                    call.respondRedirect("/yonetim/urun/$id")
                } catch (error: IllegalArgumentException) {
                    call.respondHtml(HttpStatusCode.BadRequest) {
                        adminProductForm(call.siteView(store), existing, error.message, store.categories(includeHidden = true))
                    }
                }
            }
            post("/yonetim/urun/yayin") {
                if (!call.requireAdmin()) return@post
                val fields = call.receiveParameters()
                val id = fields["id"].orEmpty()
                if (id.isNotBlank()) store.setActive(id, fields["active"] == "1")
                call.respondRedirect("/yonetim/urunler")
            }
            post("/yonetim/urun/sil") {
                if (!call.requireAdmin()) return@post
                val id = call.receiveParameters()["id"].orEmpty()
                if (id.isNotBlank()) store.delete(id)
                call.respondRedirect("/yonetim/urunler")
            }
            get("/yonetim/kategoriler") {
                if (!call.requireAdmin()) return@get
                call.respondHtml {
                    adminCategories(call.siteView(store), store.categories(includeHidden = true), store.products(activeOnly = false), null)
                }
            }
            post("/yonetim/kategori") {
                if (!call.requireAdmin()) return@post
                val form = call.formParts()
                try {
                    store.saveCategory(
                        form.fields["id"].orEmpty(),
                        form.fields["name"].orEmpty(),
                        form.fields["blurb"].orEmpty(),
                        form.fields["hidden"] != null,
                        form.fields["parentId"].orEmpty(),
                        form.files.firstOrNull(),
                        form.repeated["removeImage"].orEmpty().isNotEmpty()
                    )
                    call.respondRedirect("/yonetim/kategoriler")
                } catch (error: IllegalArgumentException) {
                    call.respondHtml(HttpStatusCode.BadRequest) {
                        adminCategories(call.siteView(store), store.categories(includeHidden = true), store.products(activeOnly = false), error.message)
                    }
                }
            }
            post("/yonetim/kategori/gizle") {
                if (!call.requireAdmin()) return@post
                val fields = call.receiveParameters()
                val id = fields["id"].orEmpty()
                if (id.isNotBlank()) store.setCategoryHidden(id, fields["hidden"] == "1")
                call.respondRedirect("/yonetim/kategoriler")
            }
            post("/yonetim/kategori/sil") {
                if (!call.requireAdmin()) return@post
                val id = call.receiveParameters()["id"].orEmpty()
                try {
                    if (id.isNotBlank()) store.deleteCategory(id)
                    call.respondRedirect("/yonetim/kategoriler")
                } catch (error: IllegalArgumentException) {
                    call.respondHtml(HttpStatusCode.BadRequest) {
                        adminCategories(call.siteView(store), store.categories(includeHidden = true), store.products(activeOnly = false), error.message)
                    }
                }
            }
        }
    }.start(wait = true)
}

private fun ApplicationCall.siteView(store: ShopStore, slug: String? = null): SiteView {
    val visible = store.categories()
    val lines = cartLines(store)
    return SiteView(
        categories = visible.filter { it.parentId.isBlank() },
        branches = visible,
        cartCount = lines.sumOf { it.quantity },
        cartTotal = lines.sumOf { it.lineTotal },
        connected = store.ready,
        currentSlug = slug,
        memberName = member()?.name?.takeIf { it.isNotBlank() },
        origin = publicOrigin(),
        showPrices = store.showPrices()
    )
}

private fun ApplicationCall.publicOrigin(): String {
    val scheme = request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim()?.ifBlank { null }
        ?: request.local.scheme
    val host = request.headers["X-Forwarded-Host"]?.substringBefore(',')?.trim()?.ifBlank { null }
        ?: request.headers[HttpHeaders.Host]?.substringBefore(',')?.trim()?.ifBlank { null }
        ?: request.local.serverHost
    return "$scheme://$host"
}

private fun ApplicationCall.member(): MemberSession? = sessions.get()

private suspend fun ApplicationCall.respondImage(store: ShopStore, id: String, index: Int) {
    val bytes = store.image(id, index)
    if (bytes == null) respond(HttpStatusCode.NotFound)
    else {
        response.headers.append(HttpHeaders.CacheControl, "public, max-age=604800")
        respondBytes(bytes, ContentType.Image.JPEG)
    }
}

private fun normalizePhone(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.length == 11 && digits.startsWith("0") -> digits
        digits.length == 10 && digits.startsWith("5") -> "0$digits"
        else -> null
    }
}

private fun ApplicationCall.admin(): AdminSession? = sessions.get()

private suspend fun ApplicationCall.requireAdmin(): Boolean {
    if (admin() != null) return true
    respondRedirect("/yonetim/giris")
    return false
}

private fun suggestJson(categories: List<ShopCategory>, products: List<ShopProduct>): String = buildString {
    append("{\"categories\":[")
    categories.forEachIndexed { index, category ->
        if (index > 0) append(',')
        append("{\"name\":${category.name.json()},\"href\":${"/kategori/${category.slug}".json()}}")
    }
    append("],\"products\":[")
    products.forEachIndexed { index, product ->
        if (index > 0) append(',')
        val image = if (product.photoCount > 0) "/medya/urun/${product.id}/0?v=${product.updatedAt}" else ""
        append(
            "{\"name\":${product.name.json()},\"href\":${"/urun/${product.slug}".json()}," +
                "\"image\":${image.json()},\"category\":${product.categoryName.json()}}"
        )
    }
    append("]}")
}

private fun String.json(): String = buildString {
    append('"')
    for (char in this@json) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n', '\r' -> append(' ')
            else -> append(char)
        }
    }
    append('"')
}

private fun ApplicationCall.cartQuantities(): Map<String, Int> =
    request.cookies["mars_cart"].orEmpty().split(',')
        .mapNotNull { piece ->
            val parts = piece.split(':')
            if (parts.size != 2) return@mapNotNull null
            val qty = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (qty <= 0) null else parts[0] to qty.coerceAtMost(99)
        }
        .toMap()

private fun ApplicationCall.cartLines(store: ShopStore): List<CartLine> =
    cartQuantities().mapNotNull { (id, qty) ->
        store.byId(id)?.takeIf { it.active }?.let { CartLine(it, qty) }
    }

private suspend fun ApplicationCall.replaceCart(id: String, qty: Int) {
    if (id.isBlank()) return
    val next = cartQuantities().toMutableMap()
    if (qty <= 0) next.remove(id) else next[id] = qty.coerceAtMost(99)
    response.cookies.append(
        Cookie(
            name = "mars_cart",
            value = next.entries.joinToString(",") { "${it.key}:${it.value}" },
            path = "/",
            maxAge = 60 * 60 * 24 * 30,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax")
        )
    )
}

private data class FormBundle(
    val fields: Map<String, String>,
    val repeated: Map<String, List<String>>,
    val files: List<ByteArray>
)

private suspend fun ApplicationCall.formParts(): FormBundle {
    val values = linkedMapOf<String, String>()
    val repeated = linkedMapOf<String, MutableList<String>>()
    val files = mutableListOf<ByteArray>()
    val multipart = receiveMultipart(formFieldLimit = 8L * 1024 * 1024)
    while (true) {
        val part = multipart.readPart() ?: break
        when (part) {
            is PartData.FormItem -> part.name?.let { name ->
                values[name] = part.value
                repeated.getOrPut(name) { mutableListOf() }.add(part.value)
            }
            is PartData.FileItem -> {
                val bytes = part.provider().readRemaining(8L * 1024 * 1024).readByteArray()
                if (bytes.isNotEmpty()) files.add(bytes)
            }
            else -> Unit
        }
        part.dispose()
    }
    return FormBundle(values, repeated, files)
}

private fun FormBundle.toDraft(): ProductDraft {
    val name = fields["name"].orEmpty().trim()
    if (name.length < 2) throw IllegalArgumentException("Ürün adı en az 2 karakter olmalı.")
    val price = parseLira(fields["price"].orEmpty()) ?: throw IllegalArgumentException("Fiyatı 12500 veya 12500,50 biçiminde yazın.")
    val stock = fields["stock"].orEmpty().toIntOrNull()
    if (stock == null || stock !in 0..100_000) throw IllegalArgumentException("Stok adedi 0 ile 100000 arasında olmalı.")
    if (files.size > ShopStore.MAX_IMAGES) throw IllegalArgumentException("En fazla ${ShopStore.MAX_IMAGES} fotoğraf yükleyebilirsiniz.")
    return ProductDraft(
        id = fields["id"].orEmpty(),
        name = name,
        summary = fields["summary"].orEmpty(),
        description = fields["description"].orEmpty(),
        priceKurus = price,
        categoryId = fields["categoryId"].orEmpty(),
        brand = fields["brand"].orEmpty(),
        stockQty = stock,
        featured = fields.containsKey("featured"),
        active = fields.containsKey("active"),
        images = files,
        removeImageIndexes = repeated["removeImage"].orEmpty().mapNotNull { it.toIntOrNull() }
    )
}
