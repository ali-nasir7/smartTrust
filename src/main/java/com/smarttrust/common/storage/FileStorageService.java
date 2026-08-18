package com.smarttrust.common.storage;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores provider verification documents (CNIC front/back, selfie) on local disk.
 *
 * Security:
 *  - Files are stored OUTSIDE the web root and are served only through
 *    authenticated, admin/provider-only endpoints (never static resources).
 *  - File names are generated (UUID) — user-controlled names never touch the filesystem.
 *  - Content type is whitelisted (jpeg/png/webp) and verified by magic bytes, not just extension.
 *  - Stored paths are relative to the configured base dir; path traversal is rejected.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path baseDir;
    private final long maxFileSizeBytes;

    public FileStorageService(@Value("${smarttrust.storage.upload-dir:./uploads}") String uploadDir,
                              @Value("${smarttrust.storage.max-file-size-bytes:5242880}") long maxFileSizeBytes) {
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        try {
            Files.createDirectories(baseDir);
            log.info("FileStorageService baseDir={} maxFileSizeBytes={}", baseDir, maxFileSizeBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload dir " + baseDir, e);
        }
    }

    /**
     * Validates and stores a document file.
     * @return the stored RELATIVE path (e.g. providers/12/cnic_front_ab12....jpg) to persist in DB.
     */
    public String storeDocument(Long userId, String docTypeLabel, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ErrorCode.DOCUMENT_INVALID_TYPE, org.springframework.http.HttpStatus.BAD_REQUEST,
                    "File is empty: " + docTypeLabel);
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw BusinessException.of(ErrorCode.FILE_TOO_LARGE, org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "File too large (max " + (maxFileSizeBytes / (1024 * 1024)) + " MB): " + docTypeLabel);
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw BusinessException.of(ErrorCode.DOCUMENT_INVALID_TYPE, org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG or WEBP images are allowed: " + docTypeLabel);
        }

        try (InputStream in = file.getInputStream()) {
            if (!looksLikeImage(in, ext)) {
                throw BusinessException.of(ErrorCode.DOCUMENT_INVALID_TYPE, org.springframework.http.HttpStatus.BAD_REQUEST,
                        "File content is not a valid image (magic bytes check failed): " + docTypeLabel);
            }
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.FILE_STORAGE_ERROR, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read uploaded file");
        }

        // Generated name only — original name never used on disk (path traversal impossible).
        String relative = "providers/" + userId + "/" + docTypeLabel.toLowerCase(Locale.ROOT)
                + "_" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store document userId={} type={}", userId, docTypeLabel, e);
            throw BusinessException.of(ErrorCode.FILE_STORAGE_ERROR, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store file");
        }
        log.info("Stored document userId={} type={} size={}B", userId, docTypeLabel, file.getSize());
        return relative.replace('\\', '/');
    }

    /** Resolves a stored relative path; rejects anything escaping the base dir. */
    public Path resolve(String relativePath) {
        Path p = baseDir.resolve(relativePath).normalize();
        if (!p.startsWith(baseDir)) {
            throw BusinessException.of(ErrorCode.FILE_STORAGE_ERROR, org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid file path");
        }
        return p;
    }

    /** Best-effort delete of a stored file (used when replacing documents). */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("Could not delete old document file {}: {}", relativePath, e.getMessage());
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Minimal magic-byte sniffing: JPEG (FF D8 FF), PNG (89 50 4E 47), WEBP (RIFF....WEBP). */
    private static boolean looksLikeImage(InputStream in, String ext) throws IOException {
        byte[] head = in.readNBytes(12);
        if (head.length < 12) return false;
        boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
        boolean webp = head[0] == 0x52 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x46
                && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50;
        return switch (ext) {
            case "jpg", "jpeg" -> jpeg;
            case "png" -> png;
            case "webp" -> webp;
            default -> false;
        };
    }
}
