package tr.com.marssolar.web

import com.google.gson.JsonParser
import java.io.File

fun main() {
    val config = AppConfig.load()
    val store = ShopStore(config)
    check(store.ready) { "Firebase bağlantısı yok." }
    store.ensureCategory(
        ShopCategory("dalgic", "DC Dalgıç", "dc-dalgic", "Solar dalgıç pompalar.", 8)
    )
    val root = File("import-cache")
    val document = JsonParser.parseString(File(root, "products.json").readText()).asJsonArray
    var saved = 0
    var withImage = 0
    document.forEach { element ->
        val item = element.asJsonObject
        val id = item.get("id").asString
        val imageFile = File(root, "images/$id.jpg")
        val image = imageFile.takeIf { it.isFile && it.length() > 0 }?.readBytes()
        val price = parseLira(item.get("price").asString)
            ?: error("$id için fiyat okunamadı.")
        val draft = ProductDraft(
            id = id,
            name = item.get("name").asString,
            summary = item.get("summary").asString,
            description = item.get("description").asString,
            priceKurus = price,
            categoryId = item.get("categoryId").asString,
            brand = item.get("brand").asString,
            stockQty = item.get("stock").asInt,
                featured = item.get("featured").asBoolean,
                active = true,
                images = listOfNotNull(image)
            )
        val categories = store.categories(includeHidden = true)
        val keptImage = try {
            store.save(draft, categories)
            image != null
        } catch (error: IllegalArgumentException) {
            store.save(draft.copy(images = emptyList()), categories)
            println("görsel atlandı $id ${error.message}")
            false
        }
        saved++
        if (keptImage) withImage++
        println("kaydedildi $saved ${item.get("name").asString}")
    }
    println("bitti: $saved ürün, $withImage görsel")
}
