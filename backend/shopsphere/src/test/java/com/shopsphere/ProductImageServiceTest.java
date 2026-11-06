package com.shopsphere;

import com.shopsphere.entity.Product;
import com.shopsphere.repository.ProductImageRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.service.ProductImageService;
import com.shopsphere.service.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for filesystem/DB consistency in ProductImageService:
 * a failed database save must not leave an orphaned file on disk,
 * and absolute storage paths must be preserved by StorageProperties.
 */
class ProductImageServiceTest {

        @TempDir
        Path tempDir;

        private byte[] realPng() {
                try {
                        var img = new java.awt.image.BufferedImage(1, 1,
                                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var out = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(img, "png", out);
                        return out.toByteArray();
                } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                }
        }

        @Test
        void failedDatabaseSaveCleansUpTheUploadedFile() {
                ProductImageRepository imageRepo = Mockito.mock(ProductImageRepository.class);
                ProductRepository productRepo = Mockito.mock(ProductRepository.class);
                StorageProperties storage = new StorageProperties(
                                tempDir.toString(), "/images");
                ProductImageService service = new ProductImageService(
                                imageRepo, productRepo, storage);

                Product product = new Product();
                Mockito.when(productRepo.findById(1L)).thenReturn(Optional.of(product));
                Mockito.when(imageRepo.findByProductIdOrderBySortOrderAsc(1L))
                                .thenReturn(java.util.List.of());
                Mockito.when(imageRepo.findByProductIdAndPrimaryImageTrue(1L))
                                .thenReturn(Optional.empty());
                Mockito.when(imageRepo.save(Mockito.any()))
                                .thenThrow(new RuntimeException("db down"));

                MockMultipartFile file = new MockMultipartFile("file", "x.png",
                                "image/png", realPng());

                assertThatThrownBy(() -> service.uploadImage(1L, file))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("db down");

                // The file written to disk before the failed save was removed.
                try (var files = Files.list(tempDir)) {
                        assertThat(files.count()).isZero();
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
                Mockito.verify(imageRepo).save(Mockito.any());
        }

        @Test
        void validUploadPersistsAndKeepsTheFile() {
                ProductImageRepository imageRepo = Mockito.mock(ProductImageRepository.class);
                ProductRepository productRepo = Mockito.mock(ProductRepository.class);
                StorageProperties storage = new StorageProperties(
                                tempDir.toString(), "/images");
                ProductImageService service = new ProductImageService(
                                imageRepo, productRepo, storage);

                Product product = new Product();
                Mockito.when(productRepo.findById(1L)).thenReturn(Optional.of(product));
                Mockito.when(imageRepo.findByProductIdOrderBySortOrderAsc(1L))
                                .thenReturn(java.util.List.of());
                Mockito.when(imageRepo.findByProductIdAndPrimaryImageTrue(1L))
                                .thenReturn(Optional.empty());
                Mockito.when(imageRepo.save(Mockito.any()))
                                .thenAnswer(inv -> inv.getArgument(0));

                var response = service.uploadImage(1L,
                                new MockMultipartFile("file", "ok.png",
                                                "image/png", realPng()));

                assertThat(response.imageUrl()).startsWith("/images/");
                try (var files = Files.list(tempDir)) {
                        assertThat(files.count()).isEqualTo(1);
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }

        @Test
        void absoluteStoragePathIsPreserved() {
                // POSIX absolute path survives the constructor normalization.
                StorageProperties posix = new StorageProperties(
                                "/var/lib/shopsphere/images", "/images");
                assertThat(posix.dir()).isEqualTo("/var/lib/shopsphere/images");

                // Windows drive-letter absolute path survives too.
                StorageProperties windows = new StorageProperties(
                                "C:\\data\\shopsphere\\images", "/images");
                assertThat(windows.dir()).isEqualTo("C:\\data\\shopsphere\\images");

                // A leading-slash path is absolute and preserved as-is.
                StorageProperties absoluteSlash = new StorageProperties(
                                "/uploads/images", "/images");
                assertThat(absoluteSlash.dir()).isEqualTo("/uploads/images");
        }
}
