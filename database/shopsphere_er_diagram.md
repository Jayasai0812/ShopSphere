# ShopSphere ER diagram

Core catalog, cart, wishlist, order, and payment tables. `order_items.product_name` and `order_items.unit_price` are a purchase snapshot.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : assigned
    users ||--o{ addresses : has
    users ||--o| carts : owns
    users ||--o| wishlists : owns
    users ||--o{ products : sells
    users ||--o{ orders : places
    categories ||--o{ categories : parent
    categories ||--o{ products : contains
    products ||--|| inventory : stocked_as
    products ||--o{ product_images : has
    products ||--o{ cart_items : referenced_by
    products ||--o{ wishlist_items : saved_in
    products ||--o{ order_items : snapshotted_in
    carts ||--o{ cart_items : contains
    wishlists ||--o{ wishlist_items : contains
    orders ||--o{ order_items : contains
    orders ||--o{ payments : paid_by

    users {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        varchar phone
        timestamp created_at
        timestamp updated_at
    }
    roles {
        bigint id PK
        varchar role_name UK
    }
    user_roles {
        bigint user_id PK_FK
        bigint role_id PK_FK
    }
    addresses {
        bigint id PK
        bigint user_id FK
        varchar line1
        varchar city
        varchar pincode
        bit is_default
    }
    categories {
        bigint id PK
        varchar name UK
        bigint parent_id FK
        timestamp created_at
        timestamp updated_at
    }
    products {
        bigint id PK
        bigint seller_id FK
        bigint category_id FK
        varchar name
        varchar sku UK
        decimal price
        bit active
    }
    product_images {
        bigint id PK
        bigint product_id FK
        varchar image_url
        int sort_order
    }
    inventory {
        bigint id PK
        bigint product_id FK_UK
        int stock_quantity
    }
    carts {
        bigint id PK
        bigint user_id FK_UK
        timestamp created_at
        timestamp updated_at
    }
    cart_items {
        bigint id PK
        bigint cart_id FK
        bigint product_id FK
        int quantity
    }
    wishlists {
        bigint id PK
        bigint user_id FK_UK
        timestamp created_at
        timestamp updated_at
    }
    wishlist_items {
        bigint id PK
        bigint wishlist_id FK
        bigint product_id FK
    }
    orders {
        bigint id PK
        bigint user_id FK
        decimal total_amount
        varchar status
        timestamp created_at
        timestamp updated_at
    }
    order_items {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        varchar product_name
        decimal unit_price
        int quantity
        decimal line_total
    }
    payments {
        bigint id PK
        bigint order_id FK
        decimal amount
        varchar payment_status
        varchar payment_method
        varchar transaction_ref UK
        timestamp paid_at
    }
```

Cardinality:

- User to Role is many-to-many through `user_roles`.
- User to Address, and Category to Product, are one-to-many.
- User to Cart and User to Wishlist are one-to-one.
- Product to Inventory is one-to-one.
- Wishlist to Product is many-to-many through `wishlist_items`.
- Order to Order Item and Order to Payment are one-to-many.
