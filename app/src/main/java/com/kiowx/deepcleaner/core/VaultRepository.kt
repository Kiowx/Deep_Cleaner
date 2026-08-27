package com.kiowx.deepcleaner.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultRepository(private val context: Context) {
    private val root = File(context.filesDir, "vault").apply { mkdirs() }
    private val index = File(root, "index.json")
    private val lock = Any()

    fun list(): List<VaultEntry> = synchronized(lock) { read().sortedByDescending(VaultEntry::addedAt) }

    fun import(uri: Uri): VaultEntry = synchronized(lock) {
        val metadata = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                (if (nameIndex >= 0) cursor.getString(nameIndex) else null) to
                    (if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L)
            }
        }
        val name = metadata?.first?.takeIf { !it.isNullOrBlank() }?.take(160) ?: "安全文件"
        val id = UUID.randomUUID().toString()
        val target = File(root, "$id.dcv")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val iv = cipher.iv
        require(iv.size in 12..32) { "无效加密参数" }
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            target.outputStream().buffered().use { rawOutput ->
                rawOutput.write(iv.size)
                rawOutput.write(iv)
                javax.crypto.CipherOutputStream(rawOutput, cipher).use { encrypted -> input.copyTo(encrypted, 1024 * 1024) }
            }
        } ?: error("无法读取所选文件")
        val entry = VaultEntry(
            id = id,
            name = name,
            size = metadata?.second?.takeIf { it >= 0 } ?: target.length(),
            addedAt = System.currentTimeMillis(),
            mimeType = context.contentResolver.getType(uri).orEmpty(),
        )
        write(read() + entry)
        entry
    }

    fun export(entry: VaultEntry, destination: Uri) = synchronized(lock) {
        val source = File(root, "${entry.id}.dcv")
        require(source.isFile) { "保险箱文件不存在" }
        source.inputStream().buffered().use { rawInput ->
            val ivLength = rawInput.read()
            require(ivLength in 12..32) { "保险箱文件已损坏" }
            val iv = ByteArray(ivLength)
            require(rawInput.read(iv) == iv.size) { "保险箱文件已损坏" }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            }
            context.contentResolver.openOutputStream(destination, "w")?.buffered()?.use { output ->
                javax.crypto.CipherInputStream(rawInput, cipher).use { decrypted -> decrypted.copyTo(output, 1024 * 1024) }
            } ?: error("无法写入目标文件")
        }
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val entries = read()
        val target = File(root, "$id.dcv")
        val deleted = !target.exists() || target.delete()
        if (deleted) write(entries.filterNot { it.id == id })
        deleted
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun read(): List<VaultEntry> = runCatching {
        if (!index.isFile) return@runCatching emptyList()
        val array = JSONArray(index.readText())
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                if (id.isBlank() || !File(root, "$id.dcv").isFile) continue
                add(VaultEntry(id, item.optString("name"), item.optLong("size"), item.optLong("addedAt"), item.optString("mimeType")))
            }
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<VaultEntry>) {
        val array = JSONArray()
        entries.distinctBy(VaultEntry::id).forEach { entry ->
            array.put(
                JSONObject().put("id", entry.id).put("name", entry.name).put("size", entry.size)
                    .put("addedAt", entry.addedAt).put("mimeType", entry.mimeType),
            )
        }
        atomicWrite(index, array.toString())
    }

    private companion object {
        const val KEY_ALIAS = "deep_cleaner_vault_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
