package com.shopsphere;

import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.ProductImage;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductImageRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product image management acceptance tests. Exercises the admin image
 * lifecycle through the real HTTP endpoints: upload, validation, primary
 * flag, reorder, delete, authorization and image metadata in product
 * responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private ProductImageRepository imageRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private UserRepository userRepository;

        private static boolean seeded;
        private static long productId;

        /** Creates a FRESH product for the calling test (unique SKU), so
         *  every test starts with zero images regardless of execution order. */
        private void seed() {
                User seller = userRepository.findByEmail("img-seller@shopsphere.test")
                                .orElseGet(() -> {
                                        User u = new User();
                                        u.setName("Image Seller");
                                        u.setEmail("img-seller@shopsphere.test");
                                        u.setPasswordHash("x");
                                        return userRepository.save(u);
                                });
                Category category = categoryRepository.findByNameIgnoreCase("Image Cat")
                                .orElseGet(() -> {
                                        Category c = new Category();
                                        c.setName("Image Cat");
                                        return categoryRepository.save(c);
                                });
                Product p = new Product();
                p.setName("Image Product");
                p.setSku("IMG-SKU-" + java.util.UUID.randomUUID()
                                                .toString().substring(0, 8));
                p.setPrice(new BigDecimal("25.00"));
                p.setCategory(category);
                p.setSeller(seller);
                p.setActive(true);
                Inventory inv = new Inventory();
                inv.setProduct(p);
                inv.setStockQuantity(5);
                p.setInventory(inv);
                productId = productRepository.save(p).getId();
                seeded = true;
        }

        /** Generates a real, decodable PNG at test runtime via ImageIO. */
        private static byte[] realPng() {
                try {
                        var img = new java.awt.image.BufferedImage(
                                        1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        img.setRGB(0, 0, 0xFF000000);
                        var out = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(img, "png", out);
                        return out.toByteArray();
                } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                }
        }

        private static final byte[] REAL_PNG = realPng();

        private MockMultipartFile png(String name) {
                return new MockMultipartFile("file", name, "image/png", REAL_PNG);
        }

        private static final String ADMIN_AUTH = "withAdmin";
        private static final String CUSTOMER_AUTH = "withCustomer";

        private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
                return org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("admin-img@shopsphere.test")
                                .roles("ADMIN");
        }

        private org.springframework.test.web.servlet.request.RequestPostProcessor customer() {
                return org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("customer-img@shopsphere.test")
                                .roles("CUSTOMER");
        }

        private long upload(String fileName, String contentType, byte[] bytes)
                        throws Exception {
                MvcResult result = mockMvc.perform(multipart(
                                "/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", fileName,
                                                contentType, bytes))
                                .with(admin()))
                                .andExpect(status().isCreated())
                                .andReturn();
                return ((Number) com.jayway.jsonpath.JsonPath.read(
                                result.getResponse().getContentAsString(), "$.id")).longValue();
        }

        @Test
        void uploadFirstImageBecomesPrimaryAndMetadataIsStored() throws Exception {
                seed();
                long firstId = upload("alpha.png", "image/png", png("alpha.png").getBytes());
                long secondId = upload("bravo.png", "image/png", png("bravo.png").getBytes());

                // First upload is primary; later ones are not.
                mockMvc.perform(get("/api/v1/admin/products/{id}/images", productId)
                                                .with(admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(firstId))
                                .andExpect(jsonPath("$[0].isPrimary").value(true))
                                .andExpect(jsonPath("$[1].id").value(secondId))
                                .andExpect(jsonPath("$[1].isPrimary").value(false))
                                .andExpect(jsonPath("$[1].sortOrder").value(1));

                // Only metadata is in the DB: URL stored, binary NOT.
                var stored = imageRepository.findById(firstId).orElseThrow();
                assertThat(stored.getImageUrl()).startsWith("/images/");
                assertThat(stored.getImageUrl()).endsWith(".png");

                // The file exists in the storage directory (outside the DB).
                Path file = Path.of("target/test-images",
                                stored.getImageUrl().substring("/images/".length()));
                assertThat(file).exists();

                // The product detail response carries the image metadata.
                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.images[0].id").value(firstId))
                                .andExpect(jsonPath("$.images[0].isPrimary").value(true));

                // The file itself is served publicly.
                mockMvc.perform(get(stored.getImageUrl()))
                                .andExpect(status().isOk());
        }

        @Test
        void rejectsInvalidFileTypeAndOversizedFile() throws Exception {
                seed();
                // Text file masquerading as an upload → 415.
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", "notes.txt",
                                                "text/plain", "hello".getBytes()))
                                .with(admin()))
                                .andExpect(status().isUnsupportedMediaType());

                // Extension/content-type mismatch → 415.
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", "evil.png",
                                                "application/pdf", new byte[] { 1 }))
                                .with(admin()))
                                .andExpect(status().isUnsupportedMediaType());

                // Oversized (> 2 MB) → 400.
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", "big.png",
                                                "image/png", new byte[2 * 1024 * 1024 + 1]))
                                .with(admin()))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsCorruptedAndSpoofedContent() throws Exception {
                seed();
                // Truncated PNG header only — passes extension/MIME checks but
                // does not decode → 415.
                byte[] corruptedHeader = java.util.Arrays.copyOf(REAL_PNG, 6);
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", "corrupt.png",
                                                "image/png", corruptedHeader))
                                .with(admin()))
                                .andExpect(status().isUnsupportedMediaType());
                // PNG bytes declared as JPEG → 415 (content/MIME spoofing).
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", "spoof.jpg",
                                                "image/jpeg", REAL_PNG))
                                .with(admin()))
                                .andExpect(status().isUnsupportedMediaType());

                // Garbage bytes with an image type → 415.
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(new MockMultipartFile("file", "junk.png",
                                                "image/png", "not-an-image".getBytes()))
                                .with(admin()))
                                .andExpect(status().isUnsupportedMediaType());

                // Nothing was persisted for this product.
                assertThat(imageRepository.findByProductIdOrderBySortOrderAsc(productId))
                                .isEmpty();
        }

        @Test
        void setPrimaryMovesTheFlag() throws Exception {
                seed();
                long firstId = upload("p1.png", "image/png", png("p1.png").getBytes());
                long secondId = upload("p2.png", "image/png", png("p2.png").getBytes());

                mockMvc.perform(patch("/api/v1/admin/products/{id}/images/{imageId}/primary",
                                productId, secondId).with(admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isPrimary").value(true));

                var first = imageRepository.findById(firstId).orElseThrow();
                var second = imageRepository.findById(secondId).orElseThrow();
                assertThat(first.isPrimaryImage()).isFalse();
                assertThat(second.isPrimaryImage()).isTrue();

                // Public product response: primary image first.
                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.images[0].id").value(secondId));
        }

        @Test
        void reorderRenumbersAndValidatesTheCompleteList() throws Exception {
                seed();
                long a = upload("r1.png", "image/png", png("r1.png").getBytes());
                long b = upload("r2.png", "image/png", png("r2.png").getBytes());
                long c = upload("r3.png", "image/png", png("r3.png").getBytes());

                // Incomplete list → 400.
                mockMvc.perform(patch("/api/v1/admin/products/{id}/images/order", productId)
                                .with(admin())
                                .contentType(
                                                org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("[" + a + "," + b + "]"))
                                .andExpect(status().isBadRequest());

                // Duplicate ids → 400.
                mockMvc.perform(patch("/api/v1/admin/products/{id}/images/order", productId)
                                .with(admin())
                                .contentType(
                                                org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("[" + a + "," + a + "," + c + "]"))
                                .andExpect(status().isBadRequest());

                // Valid reorder.
                mockMvc.perform(patch("/api/v1/admin/products/{id}/images/order", productId)
                                .with(admin())
                                .contentType(
                                                org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("[" + c + "," + b + "," + a + "]"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(c))
                                .andExpect(jsonPath("$[0].sortOrder").value(0))
                                .andExpect(jsonPath("$[2].id").value(a))
                                .andExpect(jsonPath("$[2].sortOrder").value(2));
        }

        @Test
        void deleteRemovesRowAndFileAndReordersRemainder() throws Exception {
                seed();
                long a = upload("d1.png", "image/png", png("d1.png").getBytes());
                long b = upload("d2.png", "image/png", png("d2.png").getBytes());
                String urlA = imageRepository.findById(a).orElseThrow().getImageUrl();

                mockMvc.perform(delete("/api/v1/admin/products/{id}/images/{imageId}",
                                productId, a).with(admin()))
                                .andExpect(status().isNoContent());

                assertThat(imageRepository.findById(a)).isEmpty();
                assertThat(Path.of("target/test-images",
                                urlA.substring("/images/".length()))).doesNotExist();

                // b is re-numbered to position 0 and promoted to primary.
                mockMvc.perform(get("/api/v1/admin/products/{id}/images", productId)
                                                .with(admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(b))
                                .andExpect(jsonPath("$[0].sortOrder").value(0))
                                .andExpect(jsonPath("$[0].isPrimary").value(true));

                // Deleting an image of another product → 404.
                mockMvc.perform(delete("/api/v1/admin/products/{id}/images/{imageId}",
                                productId, 99999).with(admin()))
                                .andExpect(status().isNotFound());
        }

        @Test
        void imageManagementIsAdminOnly() throws Exception {
                seed();
                // Anonymous upload → 401.
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(png("anon.png")))
                                .andExpect(status().isUnauthorized());

                // Customer upload → 403.
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(png("cust.png")).with(customer()))
                                .andExpect(status().isForbidden());

                // Customer cannot even list admin images.
                mockMvc.perform(get("/api/v1/admin/products/{id}/images", productId)
                                .with(customer()))
                                .andExpect(status().isForbidden());

                // Public GET product remains open (no auth needed).
                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(status().isOk());
        }

        @Test
        void maxImagesPerProductIsEnforced() throws Exception {
                seed();
                for (int i = 0; i < 10; i++) {
                        upload("cap" + i + ".png", "image/png", png("cap.png").getBytes());
                }
                mockMvc.perform(multipart("/api/v1/admin/products/{id}/images", productId)
                                .file(png("over.png")).with(admin()))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void multipleUploadsKeepExactlyOnePrimary() throws Exception {
                seed();
                long first = upload("m1.png", "image/png", png("m1.png").getBytes());
                long second = upload("m2.png", "image/png", png("m2.png").getBytes());
                long third = upload("m3.png", "image/png", png("m3.png").getBytes());

                // The first upload is primary; uploads 2 and 3 are not.
                var all = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
                assertThat(all).hasSize(3);
                assertThat(all.stream().filter(ProductImage::isPrimaryImage)).hasSize(1);
                assertThat(all.get(0).getId()).isEqualTo(first);
                assertThat(all.get(0).isPrimaryImage()).isTrue();
                assertThat(all.get(1).getId()).isEqualTo(second);
                assertThat(all.get(1).isPrimaryImage()).isFalse();
                assertThat(all.get(2).getId()).isEqualTo(third);
                assertThat(all.get(2).isPrimaryImage()).isFalse();

                // Public response also exposes exactly one primary.
                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.images.length()").value(3))
                                .andExpect(jsonPath("$.images[0].id").value((int) first))
                                .andExpect(jsonPath("$.images[0].isPrimary").value(true));
        }

        @Test
        void nonexistentProductReturns404ForListAndReorder() throws Exception {
                seed();
                mockMvc.perform(get("/api/v1/admin/products/{id}/images", 999999)
                                .with(admin()))
                                .andExpect(status().isNotFound());

                mockMvc.perform(patch("/api/v1/admin/products/{id}/images/order", 999999)
                                .with(admin())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("[1]"))
                                .andExpect(status().isNotFound());
        }
}
