-- ShopSphere sample data (TASK-006)
-- Dev password for every user is: password
-- password_hash is a bcrypt hash. Do not store or insert plaintext passwords.
-- Apply shopsphere_schema.sql before this script.

USE shopsphere_db;

INSERT INTO roles (id, role_name) VALUES
    (1, 'CUSTOMER'),
    (2, 'SELLER'),
    (3, 'ADMIN');

INSERT INTO users (id, name, email, password_hash, phone) VALUES
    (1, 'Asha Admin', 'admin@shopsphere.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '9000000001'),
    (2, 'Riya Shah', 'riya.shah@shopsphere.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '9000000002'),
    (3, 'Kabir Menon', 'kabir.menon@shopsphere.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '9000000003'),
    (4, 'Meera Iyer', 'meera.iyer@shopsphere.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '9000000004'),
    (5, 'Arjun Das', 'arjun.das@shopsphere.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '9000000005');

INSERT INTO user_roles (user_id, role_id) VALUES
    (1, 3),
    (2, 1),
    (2, 2),
    (3, 2),
    (4, 1),
    (5, 1);

INSERT INTO addresses (id, user_id, line1, city, state, pincode, country, is_default) VALUES
    (1, 4, '14 Residency Road', 'Bengaluru', 'Karnataka', '560001', 'India', 1),
    (2, 4, '22 FC Road', 'Pune', 'Maharashtra', '411001', 'India', 0),
    (3, 5, '8 Park Street', 'Kolkata', 'West Bengal', '700016', 'India', 1);

INSERT INTO categories (id, name, parent_id) VALUES
    (1, 'Electronics', NULL),
    (2, 'Phones', 1),
    (3, 'Laptops', 1),
    (4, 'Fashion', NULL),
    (5, 'Home', NULL);

INSERT INTO products (id, seller_id, category_id, name, description, sku, price, active) VALUES
    (1, 2, 2, 'Nova X1 Phone', '5G smartphone with a 128 GB storage option.', 'PHN-001', 19999.00, 1),
    (2, 2, 2, 'Nova X1 Mini', 'Compact phone with a dual camera.', 'PHN-002', 14999.00, 1),
    (3, 3, 2, 'Pixelon A5', 'OLED display smartphone.', 'PHN-003', 29999.00, 1),
    (4, 3, 2, 'Pixelon Lite', 'Entry-level smartphone.', 'PHN-004', 9999.00, 1),
    (5, 2, 3, 'AeroBook 14', '14-inch laptop for everyday work.', 'LAP-001', 54999.00, 1),
    (6, 2, 3, 'AeroBook Pro 16', '16-inch laptop for heavier workloads.', 'LAP-002', 72999.00, 1),
    (7, 3, 3, 'TerraBook 15', '15-inch laptop with a numeric keypad.', 'LAP-003', 45999.00, 1),
    (8, 3, 4, 'Linen Shirt', 'Breathable long-sleeve shirt.', 'FSH-001', 1299.00, 1),
    (9, 2, 4, 'Wool Jacket', 'Lightweight wool-blend jacket.', 'FSH-002', 3499.00, 1),
    (10, 3, 5, 'Ceramic Lamp', 'Desk lamp with a ceramic base.', 'HOM-001', 1899.00, 1);

INSERT INTO product_images (id, product_id, image_url, sort_order) VALUES
    (1, 1, 'https://cdn.shopsphere.test/products/PHN-001.jpg', 0),
    (2, 2, 'https://cdn.shopsphere.test/products/PHN-002.jpg', 0),
    (3, 3, 'https://cdn.shopsphere.test/products/PHN-003.jpg', 0),
    (4, 4, 'https://cdn.shopsphere.test/products/PHN-004.jpg', 0),
    (5, 5, 'https://cdn.shopsphere.test/products/LAP-001.jpg', 0),
    (6, 6, 'https://cdn.shopsphere.test/products/LAP-002.jpg', 0),
    (7, 7, 'https://cdn.shopsphere.test/products/LAP-003.jpg', 0),
    (8, 8, 'https://cdn.shopsphere.test/products/FSH-001.jpg', 0),
    (9, 9, 'https://cdn.shopsphere.test/products/FSH-002.jpg', 0),
    (10, 10, 'https://cdn.shopsphere.test/products/HOM-001.jpg', 0);

INSERT INTO inventory (id, product_id, stock_quantity) VALUES
    (1, 1, 25),
    (2, 2, 40),
    (3, 3, 15),
    (4, 4, 50),
    (5, 5, 10),
    (6, 6, 8),
    (7, 7, 12),
    (8, 8, 100),
    (9, 9, 30),
    (10, 10, 20);

INSERT INTO carts (id, user_id) VALUES
    (1, 4),
    (2, 5);

INSERT INTO cart_items (id, cart_id, product_id, quantity) VALUES
    (1, 1, 1, 1),
    (2, 1, 8, 2),
    (3, 2, 5, 1);

INSERT INTO wishlists (id, user_id) VALUES
    (1, 4),
    (2, 5);

INSERT INTO wishlist_items (id, wishlist_id, product_id) VALUES
    (1, 1, 6),
    (2, 1, 9),
    (3, 2, 3);

INSERT INTO orders (id, user_id, total_amount, status) VALUES
    (1, 4, 22597.00, 'PAID'),
    (2, 5, 54999.00, 'PENDING');

-- product_name and unit_price are copies from the moment of purchase.
INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity, line_total) VALUES
    (1, 1, 1, 'Nova X1 Phone', 19999.00, 1, 19999.00),
    (2, 1, 8, 'Linen Shirt', 1299.00, 2, 2598.00),
    (3, 2, 5, 'AeroBook 14', 54999.00, 1, 54999.00);

INSERT INTO payments (id, order_id, amount, payment_status, payment_method, transaction_ref, paid_at) VALUES
    (1, 1, 22597.00, 'SUCCESS', 'UPI', 'TXN-1001', CURRENT_TIMESTAMP(6)),
    (2, 2, 54999.00, 'PENDING', 'CARD', 'TXN-1002', NULL);

ALTER TABLE roles AUTO_INCREMENT = 4;
ALTER TABLE users AUTO_INCREMENT = 6;
ALTER TABLE addresses AUTO_INCREMENT = 4;
ALTER TABLE categories AUTO_INCREMENT = 6;
ALTER TABLE products AUTO_INCREMENT = 11;
ALTER TABLE product_images AUTO_INCREMENT = 11;
ALTER TABLE inventory AUTO_INCREMENT = 11;
ALTER TABLE carts AUTO_INCREMENT = 3;
ALTER TABLE cart_items AUTO_INCREMENT = 4;
ALTER TABLE wishlists AUTO_INCREMENT = 3;
ALTER TABLE wishlist_items AUTO_INCREMENT = 4;
ALTER TABLE orders AUTO_INCREMENT = 3;
ALTER TABLE order_items AUTO_INCREMENT = 4;
ALTER TABLE payments AUTO_INCREMENT = 3;
