package tr.com.marssolar.web

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import java.util.concurrent.TimeUnit

class ShopStore(config: AppConfig) {
    val ready: Boolean
    private val db: Firestore?

    init {
        val file = config.credentialsFile
        if (file == null) {
            ready = false
            db = null
        } else {
            if (FirebaseApp.getApps().isEmpty()) {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(file.inputStream()))
                    .setProjectId(config.projectId)
                    .build()
                FirebaseApp.initializeApp(options)
            }
            db = FirestoreClient.getFirestore()
            ready = true
            ensureCategories()
        }
    }

    fun categories(includeHidden: Boolean = false): List<ShopCategory> {
        val all = read(CATEGORIES) { it.toCategory() }.sortedBy { it.sort }
        if (includeHidden) return all
        val hiddenIds = all.filter { it.hidden }.map { it.id }.toSet()
        return all.filter { !it.hidden && it.parentId !in hiddenIds }
    }

    fun category(slug: String, includeHidden: Boolean = false): ShopCategory? =
        categories(includeHidden).firstOrNull { it.slug == slug }

    fun showPrices(): Boolean =
        one(SETTINGS, "catalog")?.getBoolean("showPrices") == true

    fun setShowPrices(value: Boolean) {
        requireDatabase().collection(SETTINGS).document("catalog")
            .set(mapOf("showPrices" to value, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .get(20, TimeUnit.SECONDS)
    }

    fun products(activeOnly: Boolean = true): List<ShopProduct> =
        read(PRODUCTS) { it.toProduct() }
            .filter { !activeOnly || it.active }
            .sortedBy { it.name.lowercase() }

    fun featured(): List<ShopProduct> {
        val visible = categories().map { it.id }.toSet()
        val open = products().filter { it.categoryId in visible }
        return open.filter { it.featured }.ifEmpty { open.take(8) }
    }

    fun byCategory(categoryId: String): List<ShopProduct> =
        products().filter { it.categoryId == categoryId }

    fun bySlug(slug: String): ShopProduct? =
        products().firstOrNull { it.slug == slug }

    fun byId(id: String): ShopProduct? =
        one(PRODUCTS, id)?.toProduct()

    fun search(query: String): List<ShopProduct> {
        val key = query.shopKey()
        if (key.isEmpty()) return emptyList()
        val visible = categories().map { it.id }.toSet()
        return products().filter { it.categoryId in visible && it.matches(key) }
    }

    fun suggest(query: String): Pair<List<ShopCategory>, List<ShopProduct>> {
        val key = query.shopKey()
        if (key.length < 2) return emptyList<ShopCategory>() to emptyList()
        val visible = categories()
        val categories = visible.filter { it.name.shopKey().contains(key) || it.blurb.shopKey().contains(key) }.take(6)
        val ids = visible.map { it.id }.toSet()
        val products = products().filter { it.categoryId in ids && it.matches(key) }.take(8)
        return categories to products
    }

    private fun ShopProduct.matches(key: String): Boolean =
        name.shopKey().contains(key) ||
            brand.shopKey().contains(key) ||
            summary.shopKey().contains(key) ||
            categoryName.shopKey().contains(key) ||
            description.shopKey().contains(key)

    fun save(draft: ProductDraft, categories: List<ShopCategory>): String {
        val database = requireDatabase()
        val id = draft.id.ifBlank { database.collection(PRODUCTS).document().id }
        val category = categories.firstOrNull { it.id == draft.categoryId }
        val slug = uniqueSlug(slugify(draft.name), exceptId = id)
        val now = System.currentTimeMillis()
        draft.removeImageIndexes.distinct().sortedDescending().forEach { index ->
            if (index >= 0) removeImageAt(id, index)
        }
        val room = (MAX_IMAGES - imageCount(id)).coerceAtLeast(0)
        draft.images.take(room).forEach { bytes ->
            val jpeg = Images.jpeg(bytes)
            writeImage(id, imageCount(id), jpeg, now)
        }
        val count = imageCount(id)
        val data = mapOf(
            "name" to draft.name.trim(),
            "slug" to slug,
            "summary" to draft.summary.trim(),
            "description" to draft.description.trim(),
            "priceKurus" to draft.priceKurus,
            "categoryId" to draft.categoryId,
            "categoryName" to category?.name.orEmpty(),
            "brand" to draft.brand.trim(),
            "stockQty" to draft.stockQty,
            "featured" to draft.featured,
            "active" to draft.active,
            "hasImage" to (count > 0),
            "imageCount" to count,
            "updatedAt" to now
        )
        database.collection(PRODUCTS).document(id).set(data, SetOptions.merge()).get(20, TimeUnit.SECONDS)
        return id
    }

    fun setActive(id: String, active: Boolean) {
        requireDatabase().collection(PRODUCTS).document(id)
            .set(mapOf("active" to active, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .get(20, TimeUnit.SECONDS)
    }

    fun saveCategory(
        id: String,
        name: String,
        blurb: String,
        hidden: Boolean,
        parentId: String = "",
        image: ByteArray? = null,
        removeImage: Boolean = false
    ): String {
        val cleaned = name.trim()
        if (cleaned.length < 2) throw IllegalArgumentException("Kategori adı en az 2 karakter olmalı.")
        val database = requireDatabase()
        val docId = id.ifBlank { database.collection(CATEGORIES).document().id }
        val all = categories(includeHidden = true)
        val current = all.firstOrNull { it.id == docId }
        val parent = parentId.trim()
        if (parent.isNotBlank()) {
            val chosen = all.firstOrNull { it.id == parent }
                ?: throw IllegalArgumentException("Üst kategori bulunamadı.")
            if (chosen.id == docId) throw IllegalArgumentException("Kategori kendisinin altına konamaz.")
            if (chosen.parentId.isNotBlank()) throw IllegalArgumentException("Alt kategori yalnızca bir ana kategorinin altına eklenebilir.")
            if (all.any { it.parentId == docId }) throw IllegalArgumentException("Alt kategorisi olan kayıt, başka bir kategorinin altına alınamaz.")
        }
        val now = System.currentTimeMillis()
        var hasImage = current?.hasImage == true
        if (removeImage) {
            database.collection(CATEGORY_IMAGES).document(docId).delete().get(20, TimeUnit.SECONDS)
            hasImage = false
        }
        if (image != null && image.isNotEmpty()) {
            database.collection(CATEGORY_IMAGES).document(docId)
                .set(mapOf("data" to Blob.fromBytes(Images.jpeg(image)), "updatedAt" to now))
                .get(20, TimeUnit.SECONDS)
            hasImage = true
        }
        val slug = uniqueCategorySlug(slugify(cleaned), docId)
        val sort = current?.sort ?: ((all.maxOfOrNull { it.sort } ?: 0) + 1)
        database.collection(CATEGORIES).document(docId).set(
            mapOf(
                "name" to cleaned,
                "slug" to slug,
                "blurb" to blurb.trim(),
                "hidden" to hidden,
                "sort" to sort,
                "parentId" to parent,
                "hasImage" to hasImage,
                "updatedAt" to now
            ),
            SetOptions.merge()
        ).get(20, TimeUnit.SECONDS)
        if (current != null && current.name != cleaned) {
            products(activeOnly = false).filter { it.categoryId == docId }.forEach { product ->
                database.collection(PRODUCTS).document(product.id)
                    .set(mapOf("categoryName" to cleaned), SetOptions.merge())
                    .get(20, TimeUnit.SECONDS)
            }
        }
        return docId
    }

    fun setCategoryHidden(id: String, hidden: Boolean) {
        requireDatabase().collection(CATEGORIES).document(id)
            .set(mapOf("hidden" to hidden), SetOptions.merge())
            .get(20, TimeUnit.SECONDS)
    }

    fun deleteCategory(id: String) {
        val database = requireDatabase()
        if (categories(includeHidden = true).any { it.parentId == id }) {
            throw IllegalArgumentException("Önce alt kategorileri silin veya başka ana kategoriye alın.")
        }
        if (products(activeOnly = false).any { it.categoryId == id }) {
            throw IllegalArgumentException("Bu kategoride ürün var. Ürünleri taşıyın veya kategoriyi gizleyin.")
        }
        database.collection(CATEGORY_IMAGES).document(id).delete().get(20, TimeUnit.SECONDS)
        database.collection(CATEGORIES).document(id).delete().get(20, TimeUnit.SECONDS)
    }

    fun categoryImage(id: String): ByteArray? =
        one(CATEGORY_IMAGES, id)?.getBlob("data")?.toBytes()

    fun saveCustomer(customer: ShopCustomer) {
        requireDatabase().collection(CUSTOMERS).document(customer.id).set(
            mapOf(
                "name" to customer.name,
                "firstName" to customer.firstName,
                "lastName" to customer.lastName,
                "email" to customer.email,
                "phone" to customer.phone,
                "gender" to customer.gender,
                "marketing" to customer.marketing,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).get(20, TimeUnit.SECONDS)
    }

    fun customer(id: String): ShopCustomer? {
        val snapshot = one(CUSTOMERS, id) ?: return null
        return ShopCustomer(
            id = snapshot.id,
            name = snapshot.getString("name").orEmpty(),
            email = snapshot.getString("email").orEmpty(),
            phone = snapshot.getString("phone").orEmpty(),
            firstName = snapshot.getString("firstName").orEmpty(),
            lastName = snapshot.getString("lastName").orEmpty(),
            gender = snapshot.getString("gender").orEmpty(),
            marketing = snapshot.getBoolean("marketing") ?: false
        )
    }

    fun ensureCategory(category: ShopCategory) {
        val database = db ?: return
        val ref = database.collection(CATEGORIES).document(category.id)
        if (ref.get().get(20, TimeUnit.SECONDS).exists()) return
        ref.set(
            mapOf(
                "name" to category.name,
                "slug" to category.slug,
                "blurb" to category.blurb,
                "sort" to category.sort
            )
        ).get(20, TimeUnit.SECONDS)
    }

    fun delete(id: String) {
        val database = requireDatabase()
        database.collection(PRODUCTS).document(id).delete().get(20, TimeUnit.SECONDS)
        repeat(MAX_IMAGES) { index -> deleteImageDoc(id, index) }
        database.collection(IMAGES).document(id).delete().get(20, TimeUnit.SECONDS)
    }

    fun image(id: String, index: Int = 0): ByteArray? {
        if (index !in 0 until MAX_IMAGES) return null
        val keyed = one(IMAGES, "${id}__$index")?.getBlob("data")?.toBytes()
        if (keyed != null) return keyed
        if (index == 0) return one(IMAGES, id)?.getBlob("data")?.toBytes()
        return null
    }

    private fun imageCount(productId: String): Int {
        var count = 0
        while (count < MAX_IMAGES && image(productId, count) != null) count++
        return count
    }

    private fun writeImage(productId: String, index: Int, jpeg: ByteArray, now: Long) {
        requireDatabase().collection(IMAGES).document("${productId}__$index")
            .set(mapOf("data" to Blob.fromBytes(jpeg), "updatedAt" to now))
            .get(20, TimeUnit.SECONDS)
    }

    private fun removeImageAt(productId: String, index: Int) {
        val count = imageCount(productId)
        if (index !in 0 until count) return
        for (cursor in index until count - 1) {
            val next = image(productId, cursor + 1) ?: break
            writeImage(productId, cursor, next, System.currentTimeMillis())
        }
        deleteImageDoc(productId, count - 1)
        if (count - 1 == 0) {
            requireDatabase().collection(IMAGES).document(productId).delete().get(20, TimeUnit.SECONDS)
        }
    }

    private fun deleteImageDoc(productId: String, index: Int) {
        requireDatabase().collection(IMAGES).document("${productId}__$index").delete().get(20, TimeUnit.SECONDS)
        if (index == 0) {
            requireDatabase().collection(IMAGES).document(productId).delete().get(20, TimeUnit.SECONDS)
        }
    }

    private fun uniqueCategorySlug(base: String, exceptId: String): String {
        val taken = categories(includeHidden = true).filter { it.id != exceptId }.map { it.slug }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    private fun uniqueSlug(base: String, exceptId: String): String {
        val taken = products(activeOnly = false).filter { it.id != exceptId }.map { it.slug }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    private fun ensureCategories() {
        val database = db ?: return
        val existing = database.collection(CATEGORIES).limit(1).get().get(20, TimeUnit.SECONDS)
        if (!existing.isEmpty) return
        SEED.forEach { category ->
            database.collection(CATEGORIES).document(category.id).set(
                mapOf(
                    "name" to category.name,
                    "slug" to category.slug,
                    "blurb" to category.blurb,
                    "sort" to category.sort
                )
            ).get(20, TimeUnit.SECONDS)
        }
    }

    private fun requireDatabase(): Firestore =
        db ?: error("Firebase bağlantısı yok. local.properties içindeki hizmet hesabı yolunu kontrol edin.")

    private fun one(collection: String, id: String) =
        db?.collection(collection)?.document(id)?.get()?.get(20, TimeUnit.SECONDS)?.takeIf { it.exists() }

    private fun <T> read(collection: String, map: (com.google.cloud.firestore.DocumentSnapshot) -> T): List<T> {
        val database = db ?: return emptyList()
        return database.collection(collection).get().get(20, TimeUnit.SECONDS).documents.map(map)
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toCategory() = ShopCategory(
        id = id,
        name = getString("name").orEmpty(),
        slug = getString("slug").orEmpty(),
        blurb = getString("blurb").orEmpty(),
        sort = getLong("sort")?.toInt() ?: 0,
        hidden = getBoolean("hidden") ?: false,
        parentId = getString("parentId").orEmpty(),
        hasImage = getBoolean("hasImage") ?: false,
        updatedAt = getLong("updatedAt") ?: 0
    )

    private fun com.google.cloud.firestore.DocumentSnapshot.toProduct() = ShopProduct(
        id = id,
        name = getString("name").orEmpty(),
        slug = getString("slug").orEmpty(),
        summary = getString("summary").orEmpty(),
        description = getString("description").orEmpty(),
        priceKurus = getLong("priceKurus") ?: 0,
        categoryId = getString("categoryId").orEmpty(),
        categoryName = getString("categoryName").orEmpty(),
        brand = getString("brand").orEmpty(),
        stockQty = getLong("stockQty")?.toInt() ?: 0,
        featured = getBoolean("featured") ?: false,
        active = getBoolean("active") ?: true,
        hasImage = getBoolean("hasImage") ?: false,
        imageCount = getLong("imageCount")?.toInt()
            ?: if (getBoolean("hasImage") == true) 1 else 0,
        updatedAt = getLong("updatedAt") ?: 0
    )

    companion object {
        const val CATEGORIES = "shopCategories"
        const val PRODUCTS = "shopProducts"
        const val IMAGES = "shopProductImages"
        const val CUSTOMERS = "shopCustomers"
        const val CATEGORY_IMAGES = "shopCategoryImages"
        const val SETTINGS = "shopSettings"
        const val MAX_IMAGES = 8

        val SEED = listOf(
            ShopCategory("paneller", "Güneş Panelleri", "gunes-panelleri", "Çatı ve arazi için panel seçenekleri.", 1),
            ShopCategory("invertorler", "İnvertörler", "invertorler", "Off-grid, on-grid ve hibrit.", 2),
            ShopCategory("akuler", "Aküler", "akuler", "Lityum ve jel akü grupları.", 3),
            ShopCategory("suruculer", "Solar Sürücü", "solar-surucu", "Pompa ve motor sürücüleri.", 4),
            ShopCategory("sarj", "Şarj Kontrol", "sarj-kontrol", "Aküyü koruyan şarj kontrol cihazları.", 5),
            ShopCategory("aydinlatma", "Solar Aydınlatma", "solar-aydinlatma", "Bahçe ve sokak için solar lambalar.", 6),
            ShopCategory("kablo", "Kablo ve Konnektör", "kablo-konnektor", "Solar kablo ve bağlantı elemanları.", 7)
        )
    }
}
