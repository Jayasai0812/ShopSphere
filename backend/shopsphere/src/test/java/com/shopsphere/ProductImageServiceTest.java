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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for filesystem/DB consistency in ProductImageService:
 * a failed database save or a failed transaction COMMIT must not leave
 * an orphaned file on disk, and absolute storage paths must be preserved
 * by StorageProperties.
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

        /** Mocks the repos; saveFails=true makes save() throw. */
        private record ServiceMocks(ProductImageService service,
                        ProductImageRepository imageRepo) {
        }

        private ServiceMocks serviceWithMockedRepos(boolean saveFails) {
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
                if (saveFails) {
                        Mockito.when(imageRepo.save(Mockito.any()))
                                        .thenThrow(new RuntimeException("db down"));
                } else {
                        Mockito.when(imageRepo.save(Mockito.any()))
                                        .thenAnswer(inv -> inv.getArgument(0));
                }
                return new ServiceMocks(service, imageRepo);
        }

        @Test
        void failedDatabaseSaveCleansUpTheUploadedFile() {
                var mocks = serviceWithMockedRepos(true);

                MockMultipartFile file = new MockMultipartFile("file", "x.png",
                                "image/png", realPng());

                assertThatThrownBy(() -> mocks.service().uploadImage(1L, file))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("db down");

                // The file written to disk before the failed save was removed.
                try (var files = Files.list(tempDir)) {
                        assertThat(files.count()).isZero();
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
                Mockito.verify(mocks.imageRepo()).save(Mockito.any());
        }

        @Test
        void validUploadPersistsAndKeepsTheFile() {
                var mocks = serviceWithMockedRepos(false);

                var response = mocks.service().uploadImage(1L,
                                new MockMultipartFile("file", "ok.png",
                                                "image/png", realPng()));

                assertThat(response.imageUrl()).startsWith("/images/");
                try (var files = Files.list(tempDir)) {
                        assertThat(files.count()).isEqualTo(1);
                } catch (Exception e) {
                        throw new IllegalStateException(e);
                }
        }

        /**
         * Commit-failure scenario: the service method itself succeeds, but
         * the surrounding transaction rolls back afterwards (exactly what
         * happens when the actual DB commit fails). The registered
         * afterCompletion() synchronization must remove the file.
         */
        @Test
        void transactionRollbackAfterUploadCleansUpTheFile() {
                var mocks = serviceWithMockedRepos(false);

                // Simulate an active transaction around the upload, which then
                // rolls back AFTER the method returned (commit failure).
                org.springframework.transaction.support.TransactionSynchronizationManager
                                .initSynchronization();
                try {
                        mocks.service().uploadImage(1L, new MockMultipartFile("file",
                                        "rollback.png", "image/png", realPng()));
                        // Method returned successfully — now the "commit fails"
                        // and the synchronizations fire with ROLLED_BACK.
                        for (var sync : org.springframework.transaction.support.TransactionSynchronizationManager
                                        .getSynchronizations()) {
                                sync.afterCompletion(
                                        org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
                        }
                } finally {
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                        .clearSynchronization();
                }

                // The uploaded file was removed by the rollback cleanup.
                try (var files = Files.list(tempDir)) {
                        assertThat(files.count()).isZero();
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
