package com.example.training_platform.curriculum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.example.training_platform.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class LocalPdfStorageService {

    private final StorageProperties storageProperties;

    public LocalPdfStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    void ensureRootExists() throws IOException {
        Files.createDirectories(root());
    }

    public Path root() {
        return Path.of(storageProperties.getRoot()).toAbsolutePath().normalize();
    }

    /**
     * Persists PDF bytes under {@code curricula/{curriculumId}/{uuid}.pdf}.
     *
     * @return path relative to storage root, using forward slashes
     */
    public String savePdf(long curriculumId, byte[] data) {
        String relative = "curricula/" + curriculumId + "/" + UUID.randomUUID() + ".pdf";
        Path absolute = root().resolve(relative);
        try {
            Files.createDirectories(absolute.getParent());
            Files.write(absolute, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write PDF", e);
        }
        return relative.replace('\\', '/');
    }

    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path path = root().resolve(relativePath).normalize();
        if (!path.startsWith(root())) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete PDF", e);
        }
    }
}
