package com.shopsphere.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.service.StorageProperties;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves uploaded image files from the storage directory. Only direct
 * children of the configured directory are exposed (no traversal), and the
 * file must exist — otherwise 404.
 */
@RestController
@RequestMapping("${app.storage.public-prefix:/images}")
public class StorageController {

        private final StorageProperties storageProperties;

        public StorageController(StorageProperties storageProperties) {
                this.storageProperties = storageProperties;
        }

        @GetMapping("/{fileName:.+}")
        public ResponseEntity<FileSystemResource> serve(@PathVariable String fileName) {
                Path dir = Path.of(storageProperties.dir()).toAbsolutePath().normalize();
                Path file = dir.resolve(fileName).normalize();
                if (!file.getParent().equals(dir) || !Files.isRegularFile(file)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                MediaType mediaType = mediaTypeOf(fileName);
                return ResponseEntity.ok()
                                .contentType(mediaType)
                                .body(new FileSystemResource(file));
        }

        private static MediaType mediaTypeOf(String fileName) {
                String lower = fileName.toLowerCase();
                if (lower.endsWith(".png")) {
                        return MediaType.IMAGE_PNG;
                }
                if (lower.endsWith(".webp")) {
                        return MediaType.parseMediaType("image/webp");
                }
                return MediaType.IMAGE_JPEG;
        }
}
