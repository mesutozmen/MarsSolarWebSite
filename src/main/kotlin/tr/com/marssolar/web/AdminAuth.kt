package tr.com.marssolar.web

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class AuthAccount(val uid: String, val email: String)

class AdminAuth(private val config: AppConfig) {
    private val http = HttpClient.newHttpClient()

    fun accepts(email: String, password: String): Boolean {
        if (!email.trim().equals(config.adminEmail, ignoreCase = true)) return false
        return signIn(email, password) != null
    }

    fun signIn(email: String, password: String): AuthAccount? {
        val response = call("accounts:signInWithPassword", email, password) ?: return null
        return account(response)
    }

    fun signUp(email: String, password: String): AuthAccount {
        if (password.length < 6) throw IllegalArgumentException("Şifre en az 6 karakter olmalı.")
        val response = call("accounts:signUp", email, password)
            ?: throw IllegalArgumentException(signupMessage(lastError))
        return account(response) ?: throw IllegalArgumentException("Üyelik oluşturulamadı.")
    }

    private var lastError: String = ""

    private fun call(method: String, email: String, password: String): String? {
        if (email.isBlank() || password.isBlank() || config.apiKey.isBlank()) return null
        val body = buildString {
            append("""{"email":""")
            append(json(email.trim()))
            append(""","password":""")
            append(json(password))
            append(""","returnSecureToken":true}""")
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://identitytoolkit.googleapis.com/v1/$method?key=${config.apiKey}"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200 && response.body().contains("localId")) return response.body()
        lastError = response.body()
        return null
    }

    private fun account(body: String): AuthAccount? {
        val uid = value(body, "localId") ?: return null
        val email = value(body, "email") ?: return null
        return AuthAccount(uid, email)
    }

    private fun value(body: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)

    private fun signupMessage(body: String): String = when {
        "EMAIL_EXISTS" in body -> "Bu e-posta zaten kayıtlı. Giriş yapın."
        "INVALID_EMAIL" in body -> "E-posta adresi geçersiz."
        "WEAK_PASSWORD" in body -> "Şifre en az 6 karakter olmalı."
        "API_KEY" in body -> "Üyelik servisi şu an yanıt vermiyor. Biraz sonra tekrar deneyin."
        else -> "Üyelik oluşturulamadı."
    }

    private fun json(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
