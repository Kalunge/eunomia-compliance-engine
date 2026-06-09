package io.kalunge.eunomia.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Storage abstraction. Sprint 1 ships a single LocalStorageService that
 * writes blobs under ./data; production swaps to S3 (or any object
 * store) by providing a different StorageService bean.
 *
 * The interface intentionally returns/accepts opaque [String] keys
 * rather than file paths — the caller never needs to know whether the
 * blob lives on local disk, in S3, or in GCS.
 */
interface StorageService {
    /**
     * Store the multipart file's bytes under a fresh key. Returns the
     * key the caller should persist on the domain entity.
     */
    fun store(file: MultipartFile, tenantId: UUID): StorageResult

    /**
     * Read bytes back as an [InputStream]. Caller is responsible for
     * closing the stream.
     */
    fun read(key: String): InputStream

    /**
     * Remove the blob. No-op if the key doesn't exist; we don't want
     * a delete that races against another caller to fail loudly.
     */
    fun delete(key: String)
}

data class StorageResult(
    val key: String,
    val sizeBytes: Long,
)

/**
 * Local-filesystem implementation. Tenant-scoped subdirectories keep
 * blobs separated on disk even though the [StorageService] contract
 * doesn't require it — defence in depth.
 */
@Service
class LocalStorageService(
    @Value("\${eunomia.storage.local.root:./data}") private val rootPath: String,
) : StorageService {

    private val root: Path = Path.of(rootPath).toAbsolutePath().also { Files.createDirectories(it) }

    override fun store(file: MultipartFile, tenantId: UUID): StorageResult {
        require(!file.isEmpty) { "Uploaded file is empty." }

        val tenantDir = root.resolve(tenantId.toString()).also { Files.createDirectories(it) }
        val key = "${tenantId}/${UUID.randomUUID()}"
        val target = root.resolve(key)

        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        return StorageResult(key = key, sizeBytes = target.fileSize())
    }

    override fun read(key: String): InputStream {
        val path = root.resolve(key)
        check(path.exists()) { "Storage key not found: $key" }
        return Files.newInputStream(path)
    }

    override fun delete(key: String) {
        runCatching { Files.deleteIfExists(root.resolve(key)) }
    }
}
