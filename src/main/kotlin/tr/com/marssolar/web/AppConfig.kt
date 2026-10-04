package tr.com.marssolar.web

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties

data class AppConfig(
    val port: Int,
    val projectId: String,
    val credentialsFile: File?,
    val apiKey: String,
    val adminEmail: String,
    val sessionSecret: String
) {
    companion object {
        fun load(): AppConfig {
            val properties = Properties()
            val file = File("local.properties")
            if (file.exists()) file.inputStream().use { properties.load(it.reader(StandardCharsets.UTF_8)) }
            fun value(key: String): String = properties.getProperty(key).orEmpty().trim()
            val credentials = value("firebase.credentials").takeIf { it.isNotEmpty() }?.let(::File)
            return AppConfig(
                port = value("port").toIntOrNull() ?: 8080,
                projectId = "mars-solar-m4s7t",
                credentialsFile = credentials?.takeIf { it.isFile },
                apiKey = value("firebase.apiKey"),
                adminEmail = value("admin.email").ifBlank { "eng.mesutozmen@gmail.com" },
                sessionSecret = value("session.secret").ifBlank { "mars-solar-dev-secret" }
            )
        }
    }
}
