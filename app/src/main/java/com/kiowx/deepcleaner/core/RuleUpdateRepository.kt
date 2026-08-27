package com.kiowx.deepcleaner.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class RuleUpdateRepository(context: Context) {
    private val file = File(context.filesDir, "verified_remote_rules.json")

    fun info(): RuleUpdateInfo = runCatching {
        if (!file.isFile) return@runCatching RuleUpdateInfo()
        val root = JSONObject(file.readText())
        RuleUpdateInfo(root.optInt("version"), file.lastModified(), root.optJSONArray("rules")?.length() ?: 0)
    }.getOrDefault(RuleUpdateInfo())

    fun rules(): List<CustomCleanRule> = runCatching {
        if (!file.isFile) return@runCatching emptyList()
        val root = JSONObject(file.readText())
        CustomRuleRepository.fromJson(root.optJSONArray("rules") ?: org.json.JSONArray()).map { it.copy(source = "签名规则") }
    }.getOrDefault(emptyList())

    fun update(): RuleUpdateInfo {
        val payload = download(RULES_URL, 1_048_576)
        val signature = download(SIGNATURE_URL, 16_384).toString(Charsets.UTF_8).trim()
        require(SignedRuleVerifier.verify(payload, signature, PUBLIC_KEY_BASE64)) { "规则签名校验失败" }
        val root = JSONObject(payload.toString(Charsets.UTF_8))
        require(root.optString("format") == "deep-cleaner-rules") { "规则格式不受支持" }
        val version = root.optInt("version")
        require(version > 0) { "规则版本无效" }
        val parsed = CustomRuleRepository.fromJson(root.optJSONArray("rules") ?: org.json.JSONArray())
        require(parsed.isNotEmpty()) { "规则列表为空" }
        atomicWrite(file, root.toString())
        return RuleUpdateInfo(version, System.currentTimeMillis(), parsed.size)
    }

    private fun download(url: String, maximumBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json,text/plain")
        return try {
            require(connection.responseCode in 200..299) { "服务器返回 ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maximumBytes) { "下载内容过大" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    internal companion object {
        const val RULES_URL = "https://raw.githubusercontent.com/Kiowx/Deep_Cleaner/main/rules/clean-rules.json"
        const val SIGNATURE_URL = "https://raw.githubusercontent.com/Kiowx/Deep_Cleaner/main/rules/clean-rules.json.sig"
        internal const val PUBLIC_KEY_BASE64 = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA4jAIJkkfUw6Bg31Ta3bO9UcZpRo4TRvF5FyVDlrklmyIwGOy9AqszSuKNEZ0en6kYHXP21dkXR3mTDSFbnXlx9xdQ+xSt8AmELKVqfzVHyvOeol39Dce7Cip+7NwnmLvjA5ArQapcO+y1YWKBo8Byip6Y0+Fn5jJU0ihEkfl4TzGtW58TqaaG1JSf7eA99Je44YLCgCYQFTHH2EjdEY1NY670BwhKsks3/v3CmMYuu7VdARTZ1XgcC/PAz7WAh9m8O1NCa5Syue+dI5+U3Xz80zEbuC6cmBib4VM26do7AMQVgsixJ445DIeQA301XYI1RKJoTT7MXWCVoqiB1Phl7N8p59wlqsbcv1Y//Wc5Cmxe+7gZYcA5LxTVHweJm1mQUELVKyBEceY8ZMH6+sRLFwQpC8bS+sqLcsySnk89KYhLUJFAug44YHjpwLzia5qbv5iq1zwilGDKIAF2kFgB2tCJ8ayQkZ6YGKkbMe/COI7nZ7Y6LJdtoyUcLabEE7FAgMBAAE="
    }
}

object SignedRuleVerifier {
    fun verify(payload: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = runCatching {
        val decoder = java.util.Base64.getDecoder()
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoder.decode(publicKeyBase64)))
        Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(payload)
            verify(decoder.decode(signatureBase64))
        }
    }.getOrDefault(false)
}
