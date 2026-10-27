# FoodLoop — Database Design

Database: `foodloop_db` (MySQL 8). Money columns are `DECIMAL(10,2)` (`BigDecimal` in Java). Tables are generated from the JPA entities.

## Tables
| Table | Important columns | Keys / constraints |
|---|---|---|
| `users` | id, name, email, password_hash, phone, address, role (`USER`/`ADMIN`), created_at | PK id; **UNIQUE email** |
| `categories` | id, name, description, active | PK id; UNIQUE name |
| `food_items` | id, name, description, price, image_url, available, prep_time_minutes, category_id | PK id; FK category_id → categories; index on name |
| `carts` | id, user_id | PK id; FK user_id → users, **UNIQUE** (one cart per user) |
| `cart_items` | id, cart_id, food_item_id, quantity | PK id; FKs to carts, food_items; **UNIQUE (cart_id, food_item_id)** |
| `orders` | id, user_id, status, total_amount, delivery_address, order_date, estimated_prep_minutes | PK id; FK user_id → users; indexes on user_id, status |
| `order_items` | id, order_id, food_item_id, deal_id, **food_name**, **unit_price**, quantity, line_total | PK id; FKs to orders, food_items, surplus_deals (deal_id nullable) |
| `order_status_history` | id, order_id, previous_status (nullable), new_status, changed_at, changed_by, description | PK id; FK order_id → orders; index on order_id |
| `surplus_deals` | id, food_item_id, original_price, discounted_price, discount_percent, start_time, end_time, quantity_limit (nullable), quantity_sold, active, created_at | PK id; FK food_item_id → food_items; index (food_item_id, active) |

## Relationships
| Relationship | Type | Reason |
|---|---|---|
| User – Cart | One-to-One | each customer has one cart (`carts.user_id` unique) |
| User – Order | One-to-Many | a customer places many orders |
| Category – FoodItem | One-to-Many | a category groups many foods |
| Cart – CartItem | One-to-Many (cascade + orphanRemoval) | items live and die with the cart |
| CartItem – FoodItem | Many-to-One | many carts can contain the same food |
| Order – OrderItem | One-to-Many (cascade + orphanRemoval) | line items belong to the order |
| OrderItem – FoodItem | Many-to-One | which menu item was bought (deletion of ordered food is blocked) |
| OrderItem – SurplusDeal | Many-to-One (optional) | which deal gave the discount |
| Order – OrderStatusHistory | One-to-Many (cascade) | timeline of status changes |
| FoodItem – SurplusDeal | One-to-Many | several deals over time for one food |

**Why snapshots?** `order_items.food_name/unit_price/line_total` copy the data at purchase time, so changing a price or renaming a food later never rewrites history.

## ER diagram
```mermaid
erDiagram
    USERS ||--o| CARTS : has
    USERS ||--o{ ORDERS : places
    CATEGORIES ||--o{ FOOD_ITEMS : contains
    CARTS ||--o{ CART_ITEMS : holds
    FOOD_ITEMS ||--o{ CART_ITEMS : "added as"
    ORDERS ||--o{ ORDER_ITEMS : includes
    FOOD_ITEMS ||--o{ ORDER_ITEMS : "bought as"
    ORDERS ||--o{ ORDER_STATUS_HISTORY : records
    FOOD_ITEMS ||--o{ SURPLUS_DEALS : "discounted by"
    SURPLUS_DEALS ||--o{ ORDER_ITEMS : "applied to"

    USERS { bigint id PK
            varchar email UK
            varchar password_hash
            varchar role }
    CATEGORIES { bigint id PK
                 varchar name UK
                 boolean active }
    FOOD_ITEMS { bigint id PK
                 varchar name
                 decimal price
                 boolean available
                 int prep_time_minutes
                 bigint category_id FK }
    CARTS { bigint id PK
            bigint user_id FK_UK }
    CART_ITEMS { bigint id PK
                 bigint cart_id FK
                 bigint food_item_id FK
                 int quantity }
    ORDERS { bigint id PK
             bigint user_id FK
             varchar status
             decimal total_amount
             int estimated_prep_minutes }
    ORDER_ITEMS { bigint id PK
                  bigint order_id FK
                  bigint food_item_id FK
                  bigint deal_id FK
                  varchar food_name
                  decimal unit_price
                  int quantity }
    ORDER_STATUS_HISTORY { bigint id PK
                           bigint order_id FK
                           varchar previous_status
                           varchar new_status
                           datetime changed_at }
    SURPLUS_DEALS { bigint id PK
                    bigint food_item_id FK
                    decimal discounted_price
                    datetime start_time
                    datetime end_time
                    int quantity_limit
                    int quantity_sold
                    boolean active }
```
