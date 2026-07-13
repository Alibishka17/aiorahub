package com.truehire.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CvStorageService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private final Path root;

    public CvStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("cv");
    }

    public StoredCv store(Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите файл CV.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("CV должен быть не больше 10 МБ.");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Разрешены только PDF, DOC и DOCX.");
        }

        Files.createDirectories(root);
        String storageKey = userId + "-" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storageKey).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("Некорректное имя файла.");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredCv(originalName, normalizedContentType(extension), storageKey);
    }

    public Resource load(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IOException("CV не найден.");
        }
        Path file = root.resolve(storageKey).normalize();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) {
            throw new IOException("CV не найден.");
        }
        return new UrlResource(file.toUri());
    }

    public void delete(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank()) return;
        Path file = root.resolve(storageKey).normalize();
        if (file.getParent().equals(root)) {
            Files.deleteIfExists(file);
        }
    }

    private String safeOriginalName(String name) {
        String safe = name == null ? "cv.pdf" : Path.of(name).getFileName().toString().trim();
        return safe.isBlank() ? "cv.pdf" : safe.replaceAll("[\\r\\n]", "_");
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String extension) {
        if ("pdf".equals(extension)) return "application/pdf";
        if ("docx".equals(extension)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/msword";
    }

    public record StoredCv(String fileName, String contentType, String storageKey) {}
}
