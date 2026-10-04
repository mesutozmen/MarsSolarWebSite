package tr.com.marssolar.web

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.aside
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.em
import kotlinx.html.footer
import kotlinx.html.form
import kotlinx.html.HEAD
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe

data class SiteView(
    val categories: List<ShopCategory>,
    val cartCount: Int,
    val cartTotal: Long = 0,
    val connected: Boolean,
    val currentSlug: String? = null,
    val memberName: String? = null,
    val branches: List<ShopCategory> = emptyList(),
    val origin: String = "",
    val showPrices: Boolean = false
)

fun HTML.homePage(view: SiteView, featured: List<ShopProduct>) = page(view, "Mars Solar", head = homeHead()) {
    val lead = view.categories.firstOrNull()
    section(classes = "banner wrap") {
        div {
            p(classes = "eyebrow") { +"Mars Solar Enerji · Nusaybin" }
            h1 { +"Güneş paneli, invertör ve akü." }
            p { +"Eviniz, tarlanız ve iş yeriniz için ürünleri kategorilerden seçin." }
            a(classes = "button", href = lead?.let { "/kategori/${it.slug}" } ?: "#kategoriler") { +"İncele" }
        }
        img(classes = "banner-logo", src = "/assets/logo-wide.png", alt = "Mars Solar, Güneşten Elektrik")
    }
    section(classes = "trust wrap") {
        article(classes = "item") { strong { +"Orijinal ürün" }; span { +"Yetkili satış, net fiyat" } }
        article(classes = "item") { strong { +"Teknik destek" }; span { +"Kurulum öncesi danışın" } }
        article(classes = "item") { strong { +"Hızlı iletişim" }; span { +Site.PHONE_DISPLAY } }
        article(classes = "item") { strong { +"Türkiye geneli" }; span { +"Siparişiniz adrese gider" } }
    }
    section(classes = "shelf wrap") {
        id = "urunler"
        h2 { +"Popüler Ürünler" }
        div(classes = "shelf-body") { productGrid(featured, showPrices = view.showPrices) }
    }
    categoryMosaic(view)
}

fun HTML.categoryPage(view: SiteView, category: ShopCategory, products: List<ShopProduct>, query: CatalogQuery) = page(
    view,
    category.name,
    head = categoryHead(
        category,
        view.branches.firstOrNull { it.id == category.parentId },
        index = !query.narrowed
    )
) {
    val parent = view.branches.firstOrNull { it.id == category.parentId }
    val root = parent ?: category
    val children = view.branches.filter { it.parentId == root.id }
    val treeIds = setOf(root.id) + children.map { it.id }
    val pool = products.filter { it.categoryId in treeIds }
    val scoped = if (category.parentId.isBlank()) pool else pool.filter { it.categoryId == category.id }
    val shown = query.apply(scoped)
    val path = "/kategori/${category.slug}"
    val brandCounts = scoped
        .filter { it.brand.isNotBlank() }
        .groupBy { it.brand.brandKey() }
        .map { (key, items) -> key to (items.first().brand to items.size) }
        .sortedByDescending { it.second.second }
    div(classes = "catalog wrap") {
        aside(classes = "filters") {
            div(classes = "filter-box") {
                div(classes = "filter-title") {
                    span(classes = "chev") { +"»" }
                    if (category.id == root.id) {
                        span { +root.name.uppercase() }
                    } else {
                        a(href = listingHref("/kategori/${root.slug}", query.copy(brands = emptySet()))) { +root.name.uppercase() }
                    }
                }
                nav(classes = "filter-links") {
                    children.forEach { child ->
                        a(classes = if (child.id == category.id) "current" else null, href = listingHref("/kategori/${child.slug}", query.copy(brands = emptySet()))) {
                            span { +"–" }
                            +child.name
                        }
                    }
                }
                a(classes = "filter-back", href = "/#kategoriler") {
                    span { +"«" }
                    +"Tüm Kategoriler"
                }
            }
            form(action = path, method = FormMethod.get, classes = "filter-box") {
                hiddenCatalog(query, includeBrands = false)
                p(classes = "filter-label") { +"Tüm Markalar" }
                if (brandCounts.isEmpty()) {
                    p(classes = "muted filter-empty") { +"Bu kategoride marka yok." }
                } else {
                    brandCounts.forEach { (key, pair) ->
                        label(classes = "check") {
                            checkBoxInput(name = "marka") {
                                value = key
                                checked = key in query.brands
                                attributes["onchange"] = "this.form.submit()"
                            }
                            +"${pair.first} (${pair.second})"
                        }
                    }
                }
            }
            if (scoped.any { it.featured }) {
                form(action = path, method = FormMethod.get, classes = "filter-box") {
                    hiddenCatalog(query, includeBrands = true, includeFeatured = false)
                    p(classes = "filter-label") { +"Filtre Seçenekleri" }
                    label(classes = "check") {
                        checkBoxInput(name = "one") {
                            value = "1"
                            checked = query.featuredOnly
                            attributes["onchange"] = "this.form.submit()"
                        }
                        +"Öne çıkanlar"
                    }
                }
            }
            val popular = pool.filter { it.featured }.ifEmpty { pool }.take(4)
            if (popular.isNotEmpty()) {
                div(classes = "filter-box") {
                    p(classes = "filter-label") { +"En Popüler Olanlar" }
                    popular.forEach { product ->
                        a(classes = "pop-item", href = "/urun/${product.slug}") {
                            productMedia(product)
                            strong { +product.name }
                        }
                    }
                }
            }
        }
        div(classes = "catalog-main") {
            ol(classes = "crumb") {
                li { a(href = "/") { +"Anasayfa" } }
                if (parent != null) {
                    li { a(href = "/kategori/${parent.slug}") { +parent.name } }
                }
                li { span { +category.name.uppercase() } }
            }
            h1 { +category.name }
            if (category.blurb.isNotBlank()) p(classes = "lede") { +category.blurb }
            form(action = path, method = FormMethod.get, classes = "catalog-bar") {
                hiddenCatalog(query, includeStock = false, includeSort = false)
                label(classes = "check") {
                    checkBoxInput(name = "stok") {
                        value = "1"
                        checked = query.stockOnly
                        attributes["onchange"] = "this.form.submit()"
                    }
                    +"Stoktakiler"
                }
                span(classes = "record-count") { +"Toplam ${shown.size} ürün" }
                select {
                    name = "sirala"
                    attributes["onchange"] = "this.form.submit()"
                    attributes["aria-label"] = "Sıralama"
                    option { value = ""; selected = query.sort.isEmpty(); +"Önerilen sıralama" }
                    option { value = "price-asc"; selected = query.sort == "price-asc"; +"En düşük fiyat" }
                    option { value = "price-desc"; selected = query.sort == "price-desc"; +"En yüksek fiyat" }
                    option { value = "new"; selected = query.sort == "new"; +"Yeni eklenenler" }
                }
                span(classes = "views") {
                    listOf(4, 3, 2).forEach { count ->
                        a(
                            classes = if (query.columns == count) "view on" else "view",
                            href = listingHref(path, query.copy(columns = count))
                        ) {
                            attributes["aria-label"] = "$count sütun"
                            attributes["style"] = "grid-template-columns: repeat($count, 1fr)"
                            repeat(count) { span {} }
                        }
                    }
                }
            }
            if (children.isNotEmpty() && category.parentId.isBlank()) {
                div(classes = "chip-block") {
                    h3 { +"İlgili Alt Kategoriler" }
                    div(classes = "chips") {
                        children.forEach { child ->
                            val count = pool.count { it.categoryId == child.id }
                            a(classes = "chip", href = "/kategori/${child.slug}") {
                                +child.name
                                span { +"($count)" }
                            }
                        }
                    }
                }
            }
            if (brandCounts.isNotEmpty()) {
                div(classes = "chip-block") {
                    h3 { +"Markalar" }
                    div(classes = "chips") {
                        brandCounts.forEach { (key, pair) ->
                            val next = if (key in query.brands) query.brands - key else query.brands + key
                            a(classes = if (key in query.brands) "chip on" else "chip", href = listingHref(path, query.copy(brands = next))) {
                                +pair.first.uppercase()
                            }
                        }
                    }
                }
            }
            productGrid(shown, listingHref(path, query), query.columns, view.showPrices)
            shareActions()
        }
    }
}

fun HTML.productPage(view: SiteView, product: ShopProduct, added: Boolean) = page(
    view,
    product.name,
    head = productHead(
        product,
        view.branches.firstOrNull { it.id == product.categoryId },
        view.branches.firstOrNull { it.id == product.categoryId }?.let { item ->
            view.branches.firstOrNull { it.id == item.parentId }
        },
        index = !added,
        showPrices = view.showPrices
    )
) {
    val category = view.branches.firstOrNull { it.id == product.categoryId }
    val parent = category?.let { item -> view.branches.firstOrNull { it.id == item.parentId } }
    article(classes = "product wrap") {
        ol(classes = "crumb") {
            li { a(href = "/") { +"Anasayfa" } }
            if (parent != null) li { a(href = "/kategori/${parent.slug}") { +parent.name } }
            if (category != null) li { a(href = "/kategori/${category.slug}") { +category.name } }
            li { span { +product.name } }
        }
        div(classes = "gallery") {
            productMedia(product, lazy = false)
            if (product.photoCount > 1) {
                div(classes = "thumbs") {
                    for (index in 0 until product.photoCount) {
                        button(type = ButtonType.button, classes = "thumb") {
                            attributes["data-photo"] = "/medya/urun/${product.id}/$index?v=${product.updatedAt}"
                            span(classes = "media-frame") {
                                waitingPhoto("/medya/urun/${product.id}/$index?v=${product.updatedAt}", "")
                            }
                        }
                    }
                }
                script {
                    unsafe {
                        +"""document.querySelectorAll("[data-photo]").forEach(function(button){button.addEventListener("click",function(){var img=document.querySelector(".gallery .card-media img.shot");if(!img)return;img.classList.remove("ready");img.src=button.getAttribute("data-photo");});});"""
                    }
                }
            }
        }
        div(classes = "buy") {
            if (product.brand.isNotBlank()) p(classes = "eyebrow") { +product.brand }
            h1 { +product.name }
            if (view.showPrices) p(classes = "price") { +product.priceLabel }
            p(classes = if (product.inStock) "stock-ok" else "stock-no") {
                +(if (product.inStock) "Stokta ${product.stockQty} adet" else "Şu an tükendi")
            }
            if (product.summary.isNotBlank()) p(classes = "lede") { +product.summary }
            if (added) p(classes = "banner") { +"Sepete eklendi." }
            if (product.inStock) {
                form(action = "/sepet", method = FormMethod.post, classes = "actions-row") {
                    hiddenInput(name = "id") { value = product.id }
                    hiddenInput(name = "back") { value = "/urun/${product.slug}?sepet=1" }
                    hiddenInput(name = "qty") { value = "1" }
                    button(classes = "button", type = ButtonType.submit) { +"Sepete ekle" }
                }
            }
            shareActions("Ürün linkini kopyala")
            table(classes = "specs") {
                tbody {
                    bodyRow("Kategori", product.categoryName.ifBlank { "—" })
                    bodyRow("Marka", product.brand.ifBlank { "—" })
                }
            }
            if (product.description.isNotBlank()) p(classes = "prose") { +product.description }
        }
    }
}

fun HTML.searchPage(view: SiteView, query: String, products: List<ShopProduct>) = page(
    view,
    if (query.isBlank()) "Arama" else "“$query” araması",
    head = PageHead("/ara", "Ürün arama sonuçları.", index = false)
) {
    section(classes = "section wrap") {
        h1 { +"Arama" }
        p(classes = "muted") { +"“$query” için ${products.size} sonuç" }
        productGrid(products, showPrices = view.showPrices)
    }
}

fun HTML.cartPage(view: SiteView, lines: List<CartLine>) = page(
    view,
    "Sepet",
    head = PageHead("/sepet", "Sepetiniz.", index = false)
) {
    section(classes = "section wrap") {
        h1 { +"Sepet" }
        if (lines.isEmpty()) {
            p(classes = "lede") { +"Sepetiniz boş. Bir ürün seçtiğinizde burada toplanır." }
        } else {
            div(classes = "fields") {
                lines.forEach { line ->
                    div(classes = "cart-row") {
                        productMedia(line.product)
                        div {
                            strong { +line.product.name }
                            if (view.showPrices) p(classes = "muted") { +line.lineTotal.toLira() }
                        }
                        div(classes = "cart-actions") {
                            form(action = "/sepet", method = FormMethod.post, classes = "qty") {
                                hiddenInput(name = "id") { value = line.product.id }
                                input(type = InputType.number, name = "qty") {
                                    value = line.quantity.toString()
                                    min = "1"
                                }
                                button(type = ButtonType.submit, classes = "button-ghost") { +"Güncelle" }
                            }
                            form(action = "/sepet", method = FormMethod.post) {
                                hiddenInput(name = "id") { value = line.product.id }
                                hiddenInput(name = "qty") { value = "0" }
                                button(type = ButtonType.submit, classes = "button-ghost cart-remove") { +"Sil" }
                            }
                        }
                    }
                }
            }
            if (view.showPrices) p(classes = "price") { +"Toplam ${lines.sumOf { it.lineTotal }.toLira()}" }
            p(classes = "muted") { +"Siparişi tamamlama bir sonraki adımda açılacak. Sepet bu tarayıcıda durur." }
        }
    }
}

fun HTML.loginPage(error: String?) = page(SiteView(emptyList(), 0, connected = true), "Yönetim girişi", robots = false, console = true) {
    section(classes = "gate") {
        img(classes = "gate-logo", src = "/assets/logo-wide.png", alt = "Mars Solar")
        h1 { +"Yönetim" }
        p(classes = "muted") { +"Süper yönetici hesabıyla giriş yapılır." }
        if (error != null) p(classes = "banner") { +error }
        form(action = "/yonetim/giris", method = FormMethod.post, classes = "fields") {
            label { span { +"E-posta" }; input(type = InputType.email, name = "email") { required = true } }
            label { span { +"Şifre" }; input(type = InputType.password, name = "password") { required = true } }
            button(classes = "button", type = ButtonType.submit) { +"Giriş yap" }
        }
    }
}

fun HTML.adminHome(view: SiteView, products: List<ShopProduct>, categories: List<ShopCategory>) = page(view, "Yönetim", robots = false, console = true) {
    adminFrame(view, "Özet", "home") {
        div(classes = "stats") {
            stat("Ürün", products.size.toString())
            stat("Yayında", products.count { it.active }.toString())
            stat("Kategori", categories.size.toString())
            stat("Sitede fiyat", if (view.showPrices) "Açık" else "Gizli")
        }
        div(classes = "quick") {
            a(classes = "quick-card", href = "/yonetim/urun/yeni") {
                strong { +"Yeni ürün" }
                span { +"Vitrine bir ürün ekle" }
            }
            a(classes = "quick-card", href = "/yonetim/urunler") {
                strong { +"Ürünler" }
                span { +"Yayın, fiyat ve stok" }
            }
            a(classes = "quick-card", href = "/yonetim/kategoriler") {
                strong { +"Kategoriler" }
                span { +"Fotoğraf ve alt kategori" }
            }
        }
    }
}

fun HTML.adminProducts(view: SiteView, products: List<ShopProduct>, categories: List<ShopCategory>) = page(view, "Ürünler", robots = false, console = true) {
    adminFrame(view, "Ürünler", "products") {
        div(classes = "toolbar") {
            input(type = InputType.search, classes = "find") {
                placeholder = "Ürün, marka veya kategori ara"
                attributes["data-admin-find"] = "1"
                attributes["autocomplete"] = "off"
            }
            a(classes = "button", href = "/yonetim/urun/yeni") { +"Yeni ürün" }
        }
        val groups = categories.map { category -> category to products.filter { it.categoryId == category.id } }
        val loose = products.filter { product -> categories.none { it.id == product.categoryId } }
        (groups + (null to loose)).forEach { (category, items) ->
            if (items.isEmpty() && category?.hidden == true) return@forEach
            section(classes = "group") {
                attributes["data-admin-group"] = "1"
                h2(classes = "group-title") {
                    val parent = category?.parentId?.let { id -> categories.firstOrNull { it.id == id } }
                    +(when {
                        category == null -> "Kategorisiz"
                        parent != null -> "${parent.name} / ${category.name}"
                        else -> category.name
                    })
                    if (category?.hidden == true) +" · gizli"
                }
                if (items.isEmpty()) {
                    p(classes = "muted") { +"Bu kategoride ürün yok." }
                } else {
                    div(classes = "stack") {
                        items.forEach { product -> productAdminRow(product, view.showPrices) }
                    }
                }
            }
        }
        script {
            unsafe {
                +"""
                var box = document.querySelector("[data-admin-find]");
                if (box) box.addEventListener("input", function() {
                  var query = box.value.toLocaleLowerCase("tr-TR").trim();
                  document.querySelectorAll("[data-admin-item]").forEach(function(row) {
                    row.hidden = query.length > 0 && (row.getAttribute("data-admin-item") || "").indexOf(query) < 0;
                  });
                  document.querySelectorAll("[data-admin-group]").forEach(function(group) {
                    var rows = group.querySelectorAll("[data-admin-item]");
                    if (!rows.length) return;
                    group.hidden = group.querySelectorAll("[data-admin-item]:not([hidden])").length === 0;
                  });
                });
                """.trimIndent()
            }
        }
    }
}

fun HTML.adminCategories(
    view: SiteView,
    categories: List<ShopCategory>,
    products: List<ShopProduct>,
    error: String?
) = page(view, "Kategoriler", robots = false, console = true) {
    adminFrame(view, "Kategoriler", "categories") {
        if (error != null) p(classes = "banner") { +error }
        div(classes = "cat-page") {
            form(action = "/yonetim/kategori", method = FormMethod.post, encType = FormEncType.multipartFormData, classes = "sheet cat-create") {
                h2 { +"Yeni kategori" }
                p(classes = "muted") { +"Ana kategori olarak ekleyin ya da var olan bir kategorinin altına alın." }
                div(classes = "fields cat-fields") {
                    categoryFields(null, categories)
                    button(classes = "button", type = ButtonType.submit) { +"Kategori ekle" }
                }
            }
            div(classes = "cat-list") {
                h2 { +"Kayıtlı kategoriler" }
                p(classes = "muted") { +"Alt kategoriler ana kategorinin altında durur. Değiştirmek için Düzenle’yi açın." }
                val roots = categories.filter { it.parentId.isBlank() }.sortedBy { it.sort }
                val rootIds = roots.map { it.id }.toSet()
                val attached = categories.filter { it.parentId in rootIds }.map { it.id }.toSet()
                (roots + categories.filter { it.id !in rootIds && it.id !in attached }).forEach { root ->
                    categoryGroup(root, categories, products)
                }
            }
        }
    }
}

fun HTML.memberLoginPage(view: SiteView, error: String?) = page(
    view,
    "Giriş",
    head = PageHead("/giris", "Mars Solar Enerji üye girişi.", index = false)
) {
    section(classes = "panel login") {
        p(classes = "eyebrow") { +"Hesap" }
        h1 { +"Giriş" }
        if (error != null) p(classes = "banner") { +error }
        form(action = "/giris", method = FormMethod.post, classes = "fields") {
            label { span { +"E-posta" }; input(type = InputType.email, name = "email") { required = true } }
            label { span { +"Şifre" }; input(type = InputType.password, name = "password") { required = true } }
            button(classes = "button", type = ButtonType.submit) { +"Giriş yap" }
        }
        p { a(href = "/uye-ol") { +"Hesabınız yok mu? Üye olun" } }
    }
}

fun HTML.registerPage(view: SiteView, error: String?) = page(
    view,
    "Yeni üyelik",
    head = PageHead("/uye-ol", "Mars Solar Enerji üyelik formu.", index = false)
) {
    section(classes = "join wrap") {
        h1 { +"Yeni Üyelik" }
        if (error != null) p(classes = "banner") { +error }
        form(action = "/uye-ol", method = FormMethod.post) {
            joinField("Adı", required = true) {
                input(type = InputType.text, name = "firstName") { required = true; autoComplete = "given-name" }
            }
            joinField("Soyadı", required = true) {
                input(type = InputType.text, name = "lastName") { required = true; autoComplete = "family-name" }
            }
            joinField("Email", required = true) {
                input(type = InputType.email, name = "email") { required = true; autoComplete = "email" }
            }
            joinField("Şifre", required = true) {
                div(classes = "pass-field") {
                    input(type = InputType.password, name = "password") {
                        required = true
                        minLength = "6"
                        autoComplete = "new-password"
                    }
                    button(type = ButtonType.button, classes = "peek") {
                        attributes["data-peek"] = "1"
                        attributes["aria-label"] = "Şifreyi göster"
                        +"göster"
                    }
                }
            }
            div(classes = "join-row") {
                span { +"Cinsiyet" }
                div(classes = "genders") {
                    label { input(type = InputType.radio, name = "gender") { value = "erkek" }; +"Erkek" }
                    label { input(type = InputType.radio, name = "gender") { value = "kadin" }; +"Kadın" }
                    label { input(type = InputType.radio, name = "gender") { value = "belirtmem" }; +"Belirtmek istemiyorum" }
                }
            }
            joinField("Cep Telefonu", required = true) {
                input(type = InputType.tel, name = "phone") {
                    required = true
                    placeholder = "(5XX) XXX XX XX"
                    attributes["inputmode"] = "numeric"
                    autoComplete = "tel"
                }
            }
            label(classes = "agree") {
                checkBoxInput(name = "ileti") {}
                span { +"Aydınlatma metninde belirtilen ilkeler kapsamında elektronik ileti almak istiyorum." }
            }
            label(classes = "agree") {
                checkBoxInput(name = "sozlesme") { required = true }
                span {
                    a(href = "/uyelik-sozlesmesi") { +"Üyelik sözleşmesini" }
                    +" kabul ediyorum."
                }
            }
            label(classes = "agree") {
                checkBoxInput(name = "aydinlatma") { required = true }
                span {
                    +"Kişisel verilerin işlenmesine ilişkin "
                    a(href = "/aydinlatma-metni") { +"Aydınlatma Metnini" }
                    +" okudum."
                }
            }
            div(classes = "join-actions") {
                a(classes = "button-ghost", href = "/giris") { +"İptal" }
                button(classes = "button save", type = ButtonType.submit) { +"Kaydet" }
            }
        }
    }
}

fun HTML.legalPage(view: SiteView, title: String, paragraphs: List<String>) = page(
    view,
    title,
    head = PageHead(
        path = if (title.contains("Aydınlatma")) "/aydinlatma-metni" else "/uyelik-sozlesmesi",
        description = clipPublic(paragraphs.firstOrNull().orEmpty().ifBlank { title })
    )
) {
    section(classes = "section wrap legal-page") {
        h1 { +title }
        paragraphs.forEach { paragraph -> p { +paragraph } }
        p { a(href = "/uye-ol") { +"Üyelik formuna dön" } }
    }
}

val MEMBERSHIP_TEXT = listOf(
    "Mars Solar Enerji üyeliği, bu sitede adınız, iletişim bilgileriniz ve siparişleriniz için bir hesap açar.",
    "Üyelik ücretsizdir. Hesap bilgilerinizin doğruluğu size aittir. Şifrenizi başkasıyla paylaşmayın.",
    "Fiyatlar sitede göründüğü gibidir. Siparişi tamamlama adımı ayrıca açılacaktır. Ürün, stok ve teslimat için ${Site.PHONE_DISPLAY} numarasından bize ulaşabilirsiniz.",
    "Hesabınızı kapatmak istediğinizde aynı numaradan yazmanız yeterlidir. Adres: Devrim, Midyat Yolu Cd. Kaçmaz Apt No: 224/A, Nusaybin / Mardin."
)

val PRIVACY_TEXT = listOf(
    "Üyelik formunda ad, soyad, e-posta, cep telefonu ve seçtiyseniz cinsiyet bilgisini alırız. Bu bilgiler hesabınızı açmak ve sizinle sipariş hakkında konuşmak içindir.",
    "Elektronik ileti kutusunu işaretlerseniz kampanya ve duyuru mesajı gönderebiliriz. İşaretlemezseniz yalnızca hesap ve sipariş için yazarız.",
    "Bilgiler Mars Solar Enerji kaydında durur, satış amacıyla başkasına devredilmez. Görmek veya silinmesini istemek için ${Site.PHONE_DISPLAY} numarasını arayabilirsiniz."
)

private fun FlowContent.joinField(label: String, required: Boolean, control: FlowContent.() -> Unit) {
    div(classes = "join-row") {
        span { +label }
        control()
        if (required) span(classes = "req") { +"*" } else span {}
    }
}

fun HTML.accountPage(view: SiteView, customer: ShopCustomer?) = page(
    view,
    "Hesabım",
    head = PageHead("/hesap", "Üye hesabı.", index = false)
) {
    section(classes = "panel login") {
        p(classes = "eyebrow") { +"Hesap" }
        h1 { +(customer?.name ?: view.memberName ?: "Hesabım") }
        if (customer != null) {
            p { +customer.email }
            if (customer.phone.isNotBlank()) p { +customer.phone }
        }
        form(action = "/cikis", method = FormMethod.post) {
            button(classes = "button-ghost", type = ButtonType.submit) { +"Çıkış yap" }
        }
    }
}

fun HTML.adminProductForm(
    view: SiteView,
    product: ShopProduct?,
    error: String?,
    categories: List<ShopCategory>
) = page(view, if (product == null) "Yeni ürün" else "Ürün", robots = false, console = true) {
    adminFrame(view, if (product == null) "Yeni ürün" else product.name, "product") {
        if (error != null) p(classes = "banner") { +error }
        form(action = "/yonetim/urun", method = FormMethod.post, encType = FormEncType.multipartFormData, classes = "sheet fields") {
            hiddenInput(name = "id") { value = product?.id.orEmpty() }
            div(classes = "form-grid") {
                label { span { +"Ad" }; input(type = InputType.text, name = "name") { required = true; value = product?.name.orEmpty() } }
                label {
                    span { +"Kategori" }
                    select { name = "categoryId"
                        categories.forEach { category ->
                            val parent = categories.firstOrNull { it.id == category.parentId }
                            option {
                                value = category.id
                                selected = category.id == product?.categoryId
                                +(if (parent != null) "${parent.name} / ${category.name}" else category.name)
                                if (category.hidden) +" (gizli)"
                            }
                        }
                    }
                }
                label { span { +"Marka" }; input(type = InputType.text, name = "brand") { value = product?.brand.orEmpty() } }
                label { span { +"Fiyat (TL)" }; input(type = InputType.text, name = "price") { required = true; value = product?.priceKurus?.toPriceField().orEmpty() } }
                label { span { +"Satış stoğu" }; input(type = InputType.number, name = "stock") { value = (product?.stockQty ?: 0).toString(); min = "0" } }
            }
            p(classes = "muted") {
                +(if (view.showPrices) "Bu tutar sitede görünüyor." else "Bu tutar kayıtlı. Ziyaretçi şu an fiyatı görmüyor.")
            }
            label { span { +"Kısa açıklama" }; textArea { name = "summary"; +product?.summary.orEmpty() } }
            label { span { +"Açıklama" }; textArea { name = "description"; +product?.description.orEmpty() } }
            if (product != null && product.photoCount > 0) {
                div(classes = "thumbs") {
                    for (index in 0 until product.photoCount) {
                        label(classes = "thumb") {
                            span(classes = "media-frame") {
                                waitingPhoto("/medya/urun/${product.id}/$index?v=${product.updatedAt}", "")
                            }
                            checkBoxInput(name = "removeImage") { value = index.toString() }
                            span { +"Sil" }
                        }
                    }
                }
            }
            div(classes = "field") {
                span(classes = "field-label") { +"Fotoğraflar" }
                filePick("image", multiple = true)
            }
            p(classes = "muted") { +"Bir ürüne en fazla 8 fotoğraf. İşaretlenenler kayıtta silinir." }
            label(classes = "check") { checkBoxInput(name = "featured") { checked = product?.featured == true }; +"Ana sayfada öne çıkar" }
            label(classes = "check") { checkBoxInput(name = "active") { checked = product?.active != false }; +"Yayında" }
            div(classes = "savebar") {
                button(classes = "button", type = ButtonType.submit) { +"Kaydet" }
            }
        }
        if (product != null) {
            form(action = "/yonetim/urun/sil", method = FormMethod.post, classes = "delete-form") {
                hiddenInput(name = "id") { value = product.id }
                button(classes = "button-ghost danger", type = ButtonType.submit) { +"Ürünü sil" }
            }
        }
    }
}

fun HTML.missingPage(view: SiteView) = page(
    view,
    "Bulunamadı",
    head = PageHead("/", "Sayfa bulunamadı.", index = false)
) {
    section(classes = "section wrap") {
        p(classes = "eyebrow") { +"404" }
        h1 { +"Bu sayfa yok." }
        p(classes = "lede") { +"Aradığınız ürün veya kategori yayında değil." }
        a(classes = "button", href = "/") { +"Ana sayfa" }
    }
}

private fun HTML.page(
    view: SiteView,
    title: String,
    robots: Boolean = true,
    head: PageHead = PageHead(path = "/", description = Site.DESCRIPTION, index = robots),
    console: Boolean = false,
    block: FlowContent.() -> Unit
) {
    lang = "tr"
    val listed = robots && head.index
    val documentTitle = if (title == "Mars Solar") Site.TAGLINE else "$title · Mars Solar Enerji"
    val canonical = if (view.origin.isBlank()) "" else view.origin + head.path
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +documentTitle }
        link(rel = "icon", href = "/assets/favicon.png")
        meta(name = "description", content = head.description)
        meta(name = "theme-color", content = "#f6a000")
        if (canonical.isNotBlank()) link(rel = "canonical", href = canonical)
        meta(name = "robots", content = if (listed) "index,follow" else "noindex,follow")
        openGraph(documentTitle, head.description, canonical, head.imagePath?.let { view.origin + it })
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "anonymous" }
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Manrope:wght@400;600;700;800&display=swap")
        link(rel = "stylesheet", href = "/assets/site.css")
        if (listed) {
            pageGraph(view.origin, head).forEach { json ->
                script(type = "application/ld+json") { unsafe { +json } }
            }
        }
    }
    body {
        if (console) {
            attributes["class"] = "console"
            block()
            pageLoader()
            return@body
        }
        div(classes = "topline wrap") {
            a(classes = "phone", href = "tel:${Site.PHONE_TEL}") {
                unsafe { +PHONE }
                +Site.PHONE_DISPLAY
            }
            a(classes = "wa-link", href = "https://wa.me/${Site.PHONE_WA}") {
                unsafe { +WHATSAPP }
                +"WhatsApp"
            }
            span(classes = "topline-social") { socialLinks() }
        }
        header(classes = "site-header") {
            div(classes = "bar wrap") {
                a(classes = "logo", href = "/") {
                    attributes["title"] = Site.TAGLINE
                    img(src = "/assets/logo-wide.png", alt = "Mars Solar") {
                        attributes["title"] = Site.TAGLINE
                    }
                }
                form(classes = "search", action = "/ara", method = FormMethod.get) {
                    attributes["autocomplete"] = "off"
                    input(type = InputType.search, name = "q") {
                        placeholder = "Hangi ürünü aramıştınız?"
                        attributes["autocomplete"] = "off"
                        attributes["aria-autocomplete"] = "list"
                    }
                    button(type = ButtonType.submit) {
                        attributes["aria-label"] = "Ara"
                        unsafe { +SEARCH }
                    }
                }
                div(classes = "account") {
                    a(classes = "account-icon", href = if (view.memberName == null) "/giris" else "/hesap") {
                        attributes["aria-label"] = view.memberName ?: "Giriş Yap"
                        unsafe { +USER }
                    }
                    span(classes = "account-copy") {
                        a(href = if (view.memberName == null) "/giris" else "/hesap") {
                            strong { +(view.memberName ?: "Giriş Yap") }
                        }
                        a(classes = "account-sub", href = if (view.memberName == null) "/uye-ol" else "/hesap") {
                            +(if (view.memberName == null) "Üye Ol" else "Hesabım")
                        }
                    }
                }
                a(classes = "cart-link", href = "/sepet") {
                    attributes["aria-label"] = "Sepetim ${view.cartCount}"
                    span(classes = "cart-ico") {
                        unsafe { +CART }
                        if (view.cartCount > 0) span(classes = "count") { +view.cartCount.toString() }
                    }
                    span(classes = "cart-copy") {
                        strong { +"Sepetim" }
                        if (view.showPrices) span { +view.cartTotal.toLira() }
                    }
                }
                label(classes = "menu-button") {
                    htmlFor = "nav-toggle"
                    attributes["aria-label"] = "Menü"
                    unsafe { +MENU }
                }
            }
            input(type = InputType.checkBox, classes = "nav-toggle") { id = "nav-toggle" }
            nav(classes = "nav") {
                view.categories.forEach { category ->
                    val children = view.branches.filter { it.parentId == category.id }
                    div(classes = "nav-item") {
                        a(href = "/kategori/${category.slug}") {
                            if (category.slug == view.currentSlug) attributes["aria-current"] = "page"
                            +category.name
                        }
                        if (children.isNotEmpty()) {
                            div(classes = "mega") {
                                div(classes = "mega-subs wrap") {
                                    children.forEach { child ->
                                        a(classes = "sub-card", href = "/kategori/${child.slug}") {
                                            categoryPhoto(child)
                                            span { +child.name }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!view.connected) p(classes = "banner wrap") { +"Veritabanına bağlanılamadı. local.properties içindeki hizmet hesabını kontrol edin." }
        main { block() }
        footer(classes = "site-footer") {
            div(classes = "foot wrap") {
                div {
                    strong { +"Mars Solar Enerji" }
                    p { +"Devrim, Midyat Yolu Cd. Kaçmaz Apt No: 224/A, Nusaybin / Mardin" }
                    p { a(href = "tel:${Site.PHONE_TEL}") { +Site.PHONE_DISPLAY } }
                }
                ul {
                    view.categories.take(4).forEach { li { a(href = "/kategori/${it.slug}") { +it.name } } }
                }
                div {
                    strong { +"Güneşten elektrik" }
                    p { +"Panel, invertör, akü ve sürücü. Fiyatlar açık, ürünler stoklu." }
                    p { a(href = "/sss") { +"Sıkça sorulan sorular" } }
                    div(classes = "social") { socialLinks() }
                }
            }
        }
        div(classes = "floaters") {
            a(classes = "call", href = "tel:${Site.PHONE_TEL}") {
                span { +"Bizi Arayın" }
                span(classes = "bubble") { unsafe { +PHONE } }
            }
            a(classes = "wa", href = "https://wa.me/${Site.PHONE_WA}") {
                span { +"Bize Yazın" }
                span(classes = "bubble") { unsafe { +WHATSAPP } }
            }
        }
        script {
            unsafe {
                +"""
                document.querySelectorAll("[data-peek]").forEach(function(button){
                  button.addEventListener("click", function(){
                    var input = button.parentElement.querySelector("input");
                    if (!input) return;
                    var open = input.type === "password";
                    input.type = open ? "text" : "password";
                    button.textContent = open ? "gizle" : "göster";
                  });
                });
                var searchForm = document.querySelector("form.search");
                if (searchForm) {
                  var searchInput = searchForm.querySelector("input");
                  var suggest = document.createElement("div");
                  suggest.className = "suggest";
                  suggest.hidden = true;
                  searchForm.appendChild(suggest);
                  var suggestTimer;
                  function fold(value) {
                    return value.toLocaleLowerCase("tr-TR").replace(/[öüşğıç]/g, function(char) {
                      return {ö:"o", ü:"u", ş:"s", ğ:"g", ı:"i", ç:"c"}[char];
                    });
                  }
                  function writeMatch(node, text, query) {
                    node.textContent = "";
                    var at = fold(text).indexOf(fold(query));
                    if (at < 0 || !query) { node.textContent = text; return; }
                    node.appendChild(document.createTextNode(text.slice(0, at)));
                    var mark = document.createElement("strong");
                    mark.textContent = text.slice(at, at + query.length);
                    node.appendChild(mark);
                    node.appendChild(document.createTextNode(text.slice(at + query.length)));
                  }
                  function showSuggest(data, query) {
                    suggest.textContent = "";
                    var rows = (data.categories || []).length + (data.products || []).length;
                    if (!rows) { suggest.hidden = true; return; }
                    (data.categories || []).forEach(function(item) {
                      var link = document.createElement("a");
                      link.href = item.href;
                      var name = document.createElement("span");
                      writeMatch(name, item.name, query);
                      var kind = document.createElement("span");
                      kind.className = "kind";
                      kind.textContent = "Kategori";
                      link.appendChild(name);
                      link.appendChild(kind);
                      suggest.appendChild(link);
                    });
                    (data.products || []).forEach(function(item) {
                      var link = document.createElement("a");
                      link.className = "hit";
                      link.href = item.href;
                      var photo = document.createElement(item.image ? "img" : "span");
                      if (item.image) { photo.src = item.image; photo.alt = ""; }
                      link.appendChild(photo);
                      var name = document.createElement("span");
                      writeMatch(name, item.name, query);
                      link.appendChild(name);
                      if (item.category) {
                        var badge = document.createElement("span");
                        badge.className = "hit-cat";
                        badge.textContent = item.category;
                        link.appendChild(badge);
                      }
                      suggest.appendChild(link);
                    });
                    suggest.hidden = false;
                  }
                  searchInput.addEventListener("input", function() {
                    clearTimeout(suggestTimer);
                    var query = searchInput.value.trim();
                    if (query.length < 2) { suggest.hidden = true; suggest.textContent = ""; return; }
                    suggestTimer = setTimeout(function() {
                      fetch("/ara/oneri?q=" + encodeURIComponent(query))
                        .then(function(response) { return response.json(); })
                        .then(function(data) { if (searchInput.value.trim() === query) showSuggest(data, query); })
                        .catch(function() { suggest.hidden = true; });
                    }, 160);
                  });
                  document.addEventListener("click", function(event) {
                    if (!searchForm.contains(event.target)) suggest.hidden = true;
                  });
                  searchInput.addEventListener("keydown", function(event) {
                    if (event.key === "Escape") suggest.hidden = true;
                  });
                }
                document.querySelectorAll("[data-share],[data-copy]").forEach(function(button){
                  button.addEventListener("click", function(){
                    var url = location.href.split("#")[0];
                    var label = button.textContent;
                    function done(text){ button.textContent = text; setTimeout(function(){ button.textContent = label; }, 1400); }
                    if (button.hasAttribute("data-share") && navigator.share) {
                      navigator.share({ title: document.title, url: url }).catch(function(){});
                      return;
                    }
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                      navigator.clipboard.writeText(url).then(function(){ done("Kopyalandı"); }).catch(function(){ done("Kopyalanamadı"); });
                    } else {
                      done("Kopyalanamadı");
                    }
                  });
                });
                """.trimIndent()
            }
        }
        pageLoader()
    }
}

private fun FlowContent.pageLoader() {
    div(classes = "page-loader") {
        attributes["role"] = "status"
        attributes["aria-live"] = "polite"
        attributes["aria-label"] = "Sayfa yükleniyor"
        img(classes = "mark-spin", src = "/assets/logo.svg", alt = "")
    }
    script {
        unsafe {
            +"""
            function showLoader() { document.body.classList.add("is-loading"); }
            function samePage(url) {
              try {
                var next = new URL(url, location.href);
                return next.origin === location.origin && next.pathname === location.pathname && next.search === location.search;
              } catch (e) { return false; }
            }
            document.addEventListener("click", function(event) {
              if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
              var link = event.target.closest && event.target.closest("a");
              if (!link || link.target === "_blank" || link.hasAttribute("download")) return;
              var href = link.getAttribute("href") || "";
              if (!href || href.charAt(0) === "#" || href.indexOf("mailto:") === 0 || href.indexOf("tel:") === 0) return;
              if (samePage(link.href)) return;
              try { if (new URL(link.href).origin !== location.origin) return; } catch (e) { return; }
              showLoader();
            });
            document.addEventListener("submit", function(event) {
              if (event.defaultPrevented) return;
              var form = event.target;
              if (!form || form.target === "_blank") return;
              showLoader();
            });
            window.addEventListener("pageshow", function() {
              document.body.classList.remove("is-loading");
            });
            """.trimIndent()
        }
    }
}

private fun FlowContent.categoryGroup(
    category: ShopCategory,
    categories: List<ShopCategory>,
    products: List<ShopProduct>
) {
    val children = categories.filter { it.parentId == category.id }.sortedBy { it.sort }
    div(classes = "cat-group") {
        categoryCard(category, categories, products, children.size)
        if (children.isNotEmpty()) {
            div(classes = "cat-kids") {
                children.forEach { child -> categoryCard(child, categories, products, 0) }
            }
        }
    }
}

private fun FlowContent.categoryCard(
    category: ShopCategory,
    categories: List<ShopCategory>,
    products: List<ShopProduct>,
    childCount: Int
) {
    val count = products.count { it.categoryId == category.id }
    article(classes = if (category.parentId.isBlank()) "cat-card" else "cat-card sub") {
        div(classes = "cat-head") {
            span(classes = "cat-photo") { categoryPhoto(category) }
            div {
                strong { +category.name }
                p(classes = "muted") {
                    +"$count ürün"
                    if (childCount > 0) +" · $childCount alt kategori"
                }
            }
            if (category.hidden) span(classes = "cat-badge") { +"Gizli" }
        }
        details(classes = "cat-edit") {
            summary { +"Düzenle" }
            form(action = "/yonetim/kategori", method = FormMethod.post, encType = FormEncType.multipartFormData, classes = "fields cat-fields") {
                hiddenInput(name = "id") { value = category.id }
                categoryFields(category, categories)
                button(classes = "button", type = ButtonType.submit) { +"Kaydet" }
            }
            div(classes = "cat-actions") {
                form(action = "/yonetim/kategori/gizle", method = FormMethod.post) {
                    hiddenInput(name = "id") { value = category.id }
                    hiddenInput(name = "hidden") { value = if (category.hidden) "0" else "1" }
                    button(classes = "button-ghost", type = ButtonType.submit) {
                        +(if (category.hidden) "Vitrinde göster" else "Vitrinden gizle")
                    }
                }
                form(action = "/yonetim/kategori/sil", method = FormMethod.post) {
                    hiddenInput(name = "id") { value = category.id }
                    button(classes = "button-ghost danger", type = ButtonType.submit) { +"Sil" }
                }
            }
        }
    }
}

private fun FlowContent.categoryFields(category: ShopCategory?, categories: List<ShopCategory>) {
    val roots = categories.filter { it.parentId.isBlank() && it.id != category?.id }
    val locked = category != null && categories.any { it.parentId == category.id }
    label { span { +"Ad" }; input(type = InputType.text, name = "name") { required = true; value = category?.name.orEmpty() } }
    label { span { +"Kısa açıklama" }; input(type = InputType.text, name = "blurb") { value = category?.blurb.orEmpty() } }
    if (locked) {
        p(classes = "muted") { +"Alt kategorisi var, bu yüzden ana kategoride kalır." }
        hiddenInput(name = "parentId") { value = "" }
    } else {
        label {
            span { +"Üst kategori" }
            select {
                name = "parentId"
                option { value = ""; selected = category == null || category.parentId.isBlank(); +"Ana kategori" }
                roots.forEach { root ->
                    option { value = root.id; selected = root.id == category?.parentId; +root.name }
                }
            }
        }
    }
    div(classes = "field") {
        span(classes = "field-label") { +"Fotoğraf" }
        filePick("image", multiple = false)
    }
    if (category?.hasImage == true) {
        label(classes = "check") { checkBoxInput(name = "removeImage") { value = "1" }; +"Fotoğrafı sil" }
    }
    label(classes = "check") { checkBoxInput(name = "hidden") { checked = category?.hidden == true }; +"Vitrinde gizle" }
}

private fun FlowContent.categoryTile(category: ShopCategory) {
    a(classes = "tile", href = "/kategori/${category.slug}") {
        categoryPhoto(category)
        strong { +category.name }
    }
}

private fun FlowContent.categoryPhoto(category: ShopCategory) {
    span(classes = "media-frame") {
        if (category.hasImage) {
            waitingPhoto("/medya/kategori/${category.id}?v=${category.updatedAt}", category.name)
        } else {
            brandMark(spinning = false)
        }
    }
}

private fun FlowContent.brandMark(spinning: Boolean) {
    img(classes = if (spinning) "mark-spin" else "mark-still", src = "/assets/logo.svg", alt = "") {
        attributes["aria-hidden"] = "true"
    }
}

private fun FlowContent.productMedia(product: ShopProduct, index: Int = 0, lazy: Boolean = true) {
    span(classes = "card-media") {
        if (product.photoCount > 0) {
            waitingPhoto("/medya/urun/${product.id}/$index?v=${product.updatedAt}", product.name, lazy)
        } else {
            brandMark(spinning = false)
        }
    }
}

private fun FlowContent.waitingPhoto(src: String, alt: String, lazy: Boolean = true) {
    brandMark(spinning = true)
    img(classes = "shot", src = src, alt = alt) {
        attributes["loading"] = if (lazy) "lazy" else "eager"
        attributes["decoding"] = "async"
        attributes["onload"] = "this.classList.add('ready')"
        attributes["onerror"] = "this.remove()"
    }
}

private fun HEAD.openGraph(title: String, description: String, url: String, image: String?) {
    meta { attributes["property"] = "og:title"; attributes["content"] = title }
    meta { attributes["property"] = "og:description"; attributes["content"] = description }
    meta { attributes["property"] = "og:locale"; attributes["content"] = "tr_TR" }
    meta { attributes["property"] = "og:site_name"; attributes["content"] = "Mars Solar Enerji" }
    meta { attributes["property"] = "og:type"; attributes["content"] = "website" }
    if (url.isNotBlank()) meta { attributes["property"] = "og:url"; attributes["content"] = url }
    if (!image.isNullOrBlank()) meta { attributes["property"] = "og:image"; attributes["content"] = image }
}

internal fun clipPublic(text: String): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.length <= 160) return clean
    return clean.take(157).substringBeforeLast(' ').trimEnd() + "…"
}

private fun FlowContent.shareActions(copyLabel: String = "Linki kopyala") {
    div(classes = "share-row") {
        button(type = ButtonType.button, classes = "button-ghost") {
            attributes["data-share"] = "1"
            +"Linki paylaş"
        }
        button(type = ButtonType.button, classes = "button-ghost") {
            attributes["data-copy"] = "1"
            +copyLabel
        }
    }
}

private fun FlowContent.socialLinks() {
    a(href = Site.INSTAGRAM, classes = "ig") {
        attributes["target"] = "_blank"
        attributes["rel"] = "noopener"
        attributes["aria-label"] = "Instagram"
        unsafe { +INSTAGRAM }
    }
    a(href = Site.FACEBOOK, classes = "fb") {
        attributes["target"] = "_blank"
        attributes["rel"] = "noopener"
        attributes["aria-label"] = "Facebook"
        unsafe { +FACEBOOK }
    }
}

internal val QUESTIONS = listOf(
    "Güneş panelleri hangi türlerde bulunur?" to
        "Monokristal paneller daha yüksek verim verir ve çatıda daha az yer kaplar. Polikristal paneller fiyatı daha uygun olandır. İnce film paneller esnektir, verimi daha düşüktür. İhtiyacınıza göre gücü birlikte seçeriz.",
    "İnverter ne işe yarar?" to
        "Panelin ürettiği doğru akımı, evde ve iş yerinde kullanılan alternatif akıma çevirir. Off-grid şebekeden bağımsız çalışır, on-grid şebekeyle mahsuplaşır, hibrit ise aküyle kesintide de enerji verir.",
    "Akü seçerken nelere bakılır?" to
        "Kapasite, voltaj ve döngü ömrü. Jel akü sabit sistemlerde yaygındır. Lityum batarya daha hafif ve daha uzun ömürlüdür, fiyatı daha yüksektir. Gece ne kadar enerji kullanacağınıza göre kapasite belirlenir.",
    "Solar paket ne içerir?" to
        "Panele ek olarak inverter, gerekiyorsa akü ve şarj kontrol, kablo ve bağlantı parçaları. Bağ evi, sulama veya ev paketi güce göre değişir. Kurulumdan önce listeyi birlikte kontrol ederiz.",
    "Isı pompası güneş enerjisiyle çalışır mı?" to
        "Isı pompası havadaki ısıyı içeri taşır, ısıtma ve soğutmada az elektrik harcar. Güneşten üretilen elektrikle çalıştırıldığında şebeke tüketimi daha da düşer. Bunun için uygun güçte bir inverter gerekir.",
    "Sistem kendini kaç yılda karşılar?" to
        "Nusaybin ve çevresinde güneşlenme süresi uzundur. Tüketim, panel verimi ve elektrik fiyatına göre geri dönüş çoğu ev sisteminde birkaç yıl ile yedi yıl arasında değişir. Doğru boyutlandırma bu süreyi kısaltır."
)

fun HTML.faqPage(view: SiteView) = page(
    view,
    "Sıkça sorulan sorular",
    head = PageHead(
        path = "/sss",
        description = "Güneş paneli, inverter, akü, solar paket ve geri ödeme süresi hakkında Mars Solar Enerji yanıtları."
    )
) {
    section(classes = "section wrap faq") {
        p(classes = "eyebrow") { +"Mars Solar Enerji" }
        h1 { +"Sıkça sorulan sorular" }
        faqList()
    }
}

private fun FlowContent.faqList() {
    QUESTIONS.forEach { (question, answer) ->
        details {
            summary { +question }
            p { +answer }
        }
    }
}

private fun FlowContent.categoryMosaic(view: SiteView) {
    section(classes = "section wrap") {
        id = "kategoriler"
        h2(classes = "cats-title") { +"Öne Çıkan Kategoriler" }
        div(classes = "cats") {
            view.categories.forEach { category -> categoryTile(category) }
        }
    }
}

private fun FlowContent.hiddenCatalog(
    query: CatalogQuery,
    includeBrands: Boolean = true,
    includeFeatured: Boolean = true,
    includeStock: Boolean = true,
    includeSort: Boolean = true
) {
    if (includeStock && query.stockOnly) hiddenInput(name = "stok") { value = "1" }
    if (includeFeatured && query.featuredOnly) hiddenInput(name = "one") { value = "1" }
    if (includeBrands) query.brands.sorted().forEach { brand -> hiddenInput(name = "marka") { value = brand } }
    if (includeSort && query.sort.isNotBlank()) hiddenInput(name = "sirala") { value = query.sort }
    if (query.columns != 4) hiddenInput(name = "kolon") { value = query.columns.toString() }
}

private fun listingHref(path: String, query: CatalogQuery): String {
    val parts = mutableListOf<String>()
    if (query.stockOnly) parts += "stok=1"
    if (query.featuredOnly) parts += "one=1"
    query.brands.sorted().forEach { brand ->
        parts += "marka=${java.net.URLEncoder.encode(brand, Charsets.UTF_8)}"
    }
    if (query.sort.isNotBlank()) parts += "sirala=${query.sort}"
    if (query.columns != 4) parts += "kolon=${query.columns}"
    return if (parts.isEmpty()) path else "$path?${parts.joinToString("&")}"
}

private fun FlowContent.productGrid(
    products: List<ShopProduct>,
    back: String = "/sepet",
    columns: Int? = null,
    showPrices: Boolean = false
) {
    if (products.isEmpty()) {
        p(classes = "lede") { +"Bu bölüm henüz boş. Yönetimden eklenen ürünler burada listelenir." }
        return
    }
    div(classes = if (columns == null) "grid" else "grid cols-$columns") {
        products.forEach { productCard(it, back, showPrices) }
    }
}

private fun FlowContent.productCard(product: ShopProduct, back: String, showPrices: Boolean) {
    article(classes = "card") {
        a(href = "/urun/${product.slug}") {
            if (product.featured) span(classes = "badge") { +"Öne çıkan" }
            productMedia(product)
            if (product.brand.isNotBlank()) em(classes = "brand") { +product.brand }
            strong { +product.name }
            if (showPrices) span(classes = "price") { +product.priceLabel }
        }
        if (product.inStock) {
            form(action = "/sepet", method = FormMethod.post) {
                hiddenInput(name = "id") { value = product.id }
                hiddenInput(name = "qty") { value = "1" }
                hiddenInput(name = "back") { value = back }
                button(classes = "cart-add", type = ButtonType.submit) { +"Sepete ekle" }
            }
        } else {
            span(classes = "stock-no") { +"Tükendi" }
        }
    }
}

private fun FlowContent.productAdminRow(product: ShopProduct, showPrices: Boolean) {
    div(classes = "admin-row") {
        attributes["data-admin-item"] = listOf(product.name, product.brand, product.categoryName)
            .joinToString(" ")
            .lowercase(java.util.Locale.forLanguageTag("tr-TR"))
        productMedia(product)
        div {
            a(href = "/yonetim/urun/${product.id}") { strong { +product.name } }
            p(classes = "muted") {
                val visibility = if (showPrices) "sitede görünür" else "sitede gizli"
                +"${product.priceLabel} · $visibility · stok ${product.stockQty} · ${product.photoCount} fotoğraf"
            }
        }
        form(action = "/yonetim/urun/yayin", method = FormMethod.post, classes = "row-actions") {
            hiddenInput(name = "id") { value = product.id }
            hiddenInput(name = "active") { value = if (product.active) "0" else "1" }
            button(classes = if (product.active) "button-ghost" else "button", type = ButtonType.submit) {
                +(if (product.active) "Yayını durdur" else "Yayınla")
            }
        }
    }
}

private fun kotlinx.html.TBODY.bodyRow(label: String, value: String) {
    tr { th { +label }; td { +value } }
}

private fun Long.toPriceField(): String {
    val whole = this / 100
    val cents = this % 100
    return if (cents == 0L) whole.toString() else "$whole,${cents.toString().padStart(2, '0')}"
}

private fun FlowContent.stat(label: String, value: String) {
    article(classes = "stat") {
        span { +label }
        strong { +value }
    }
}

private fun FlowContent.adminFrame(view: SiteView, title: String, current: String, block: FlowContent.() -> Unit) {
    div(classes = "desk") {
        input(type = InputType.checkBox, classes = "desk-toggle") { id = "desk-nav" }
        aside(classes = "rail") {
            a(classes = "rail-brand", href = "/yonetim") {
                img(src = "/assets/logo.svg", alt = "")
                span { +"Yönetim" }
            }
            nav {
                railLink("/yonetim", "Özet", current == "home")
                railLink("/yonetim/urunler", "Ürünler", current == "products")
                railLink("/yonetim/kategoriler", "Kategoriler", current == "categories")
                railLink("/yonetim/urun/yeni", "Yeni ürün", current == "product")
            }
            form(action = "/yonetim/fiyat", method = FormMethod.post, classes = "price-switch") {
                label {
                    span {
                        strong { +"Sitede fiyat" }
                        small { +(if (view.showPrices) "Ziyaretçi görüyor" else "Ziyaretçi görmüyor") }
                    }
                    span(classes = "switch") {
                        input(type = InputType.checkBox, name = "show") {
                            value = "1"
                            checked = view.showPrices
                            attributes["onchange"] = "this.form.submit()"
                            attributes["aria-label"] = "Sitede fiyatları göster"
                        }
                        span(classes = "track") {}
                    }
                }
            }
            div(classes = "rail-foot") {
                a(classes = "rail-link", href = "/") { +"Siteyi gör" }
                form(action = "/yonetim/cikis", method = FormMethod.post) {
                    button(classes = "rail-link out", type = ButtonType.submit) { +"Çıkış" }
                }
            }
        }
        div(classes = "desk-main") {
            header(classes = "desk-top") {
                label(classes = "desk-menu") {
                    htmlFor = "desk-nav"
                    +"Menü"
                }
                h1 { +title }
                span(classes = if (view.showPrices) "state on" else "state") {
                    +(if (view.showPrices) "Fiyatlar açık" else "Fiyatlar gizli")
                }
            }
            div(classes = "desk-body") {
                block()
                script {
                    unsafe {
                        +"""
                        document.querySelectorAll(".file-pick input").forEach(function(input) {
                          input.addEventListener("change", function() {
                            var name = input.parentElement.querySelector(".file-name");
                            if (!name) return;
                            var files = input.files;
                            if (!files || !files.length) name.textContent = "Henüz dosya seçilmedi";
                            else if (files.length === 1) name.textContent = files[0].name;
                            else name.textContent = files.length + " dosya seçildi";
                          });
                        });
                        """.trimIndent()
                    }
                }
            }
        }
    }
}

private fun FlowContent.filePick(name: String, multiple: Boolean) {
    label(classes = "file-pick") {
        input(type = InputType.file, name = name) {
            accept = "image/*"
            if (multiple) attributes["multiple"] = "multiple"
        }
        span(classes = "file-button") { +"Dosya seç" }
        span(classes = "file-name") { +"Henüz dosya seçilmedi" }
    }
}

private fun FlowContent.railLink(href: String, label: String, current: Boolean) {
    a(href = href, classes = if (current) "rail-link on" else "rail-link") { +label }
}

private fun FlowContent.article(classes: String, block: FlowContent.() -> Unit) {
    section(classes = classes, block = block)
}

private const val PHONE = """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.2 3.8h2.8l1 2.4-1.7 1a12 12 0 005.5 5.5l1-1.7 2.4 1v2.8a1.6 1.6 0 01-1.8 1.6A14.2 14.2 0 014.6 5.6 1.6 1.6 0 016.2 3.8h2z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"/></svg>"""
private const val WHATSAPP = """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4.2a7.2 7.2 0 00-6.2 10.8L5.1 19l4.1-.9A7.2 7.2 0 1012 4.2z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M9.1 8.8c.15-.35.35-.35.55-.35h.45c.15 0 .35.05.45.4.15.45.55 1.45.6 1.55.08.15 0 .28-.12.42l-.28.35c.35.62.9 1.15 1.55 1.5l.35-.28c.14-.12.27-.2.42-.12.1.08 1.1.5 1.55.65.35.15.35.3.35.5v.45c0 .2-.05.4-.4.55-.45.28-1.25.4-2.05.05-1.05-.5-2.3-1.45-3.05-2.6-.65-1-.9-2-.55-2.65z" fill="currentColor"/></svg>"""
private const val INSTAGRAM = """<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="5" fill="none" stroke="currentColor" stroke-width="1.6"/><circle cx="12" cy="12" r="3.4" fill="none" stroke="currentColor" stroke-width="1.6"/><circle cx="17.1" cy="6.9" r="0.9" fill="currentColor"/></svg>"""
private const val FACEBOOK = """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 8.5h2.4V6H14c-1.9 0-3.4 1.5-3.4 3.4V12H8.4v2.6h2.2V20h2.8v-5.4h2.3l.4-2.6H13.4v-2.1c0-.8.5-1.4 1.2-1.4z" fill="currentColor"/></svg>"""
private const val USER = """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M5.5 19.2a6.5 6.5 0 0113 0" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>"""
private const val CART = """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 7h15l-1.5 8h-12z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M6 7L5 4H2" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="9" cy="20" r="1.3" fill="currentColor"/><circle cx="17" cy="20" r="1.3" fill="currentColor"/></svg>"""
private const val SEARCH = """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M16 16l5 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>"""
private const val MENU = """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>"""
