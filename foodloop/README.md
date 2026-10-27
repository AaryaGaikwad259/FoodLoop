# FoodLoop — Online Food Ordering System

A Spring Boot REST API where customers browse food, manage a cart, place orders, track order progress and grab discounted **surplus-food deals**; administrators manage the menu, orders and deals.

> **Verification status (please read):** The code was written in an environment with **no Maven, no internet and no MySQL**.
> A JDK parser pass confirmed the source files have **no syntax errors**, but the project has **NOT been compiled with its dependencies, the tests have NOT been run, and MySQL connectivity has NOT been tested.**
> Expect to run `mvn clean test` once and fix any small errors (send them to your assistant). See "Verification checklist" at the end.

## 1. Problem statement & objectives
Ordering food and handling surplus food are usually separate problems. FoodLoop provides a secure backend that:
1. lets customers order food with server-calculated prices,
2. gives customers a transparent **order timeline** and an **honest preparation-time estimate**,
3. helps restaurants **reduce food waste** by selling surplus items at a discount for a limited time/quantity.

## 2. Features
| Area | What it does |
|---|---|
| Auth | Register, login (JWT), BCrypt passwords, USER/ADMIN roles, no self-promotion to admin |
| Profile | View / update name, phone, address |
| Categories | Full CRUD (admin writes, public reads) |
| Food | Full CRUD, search by name, filter by category/availability, availability toggle |
| Cart | Add/update/remove/clear; same item merges; server-side totals; own-cart-only |
| Orders | Transactional placement, price & name snapshots, history, cancellation rules |
| Timeline | `order_status_history` row for **every** status change |
| Estimate | Documented formula using prep times, order size and kitchen queue |
| Surplus deals | Admin-created time-boxed discounts, optional quantity limit, row locking against overselling, expiry checked at purchase time |

Not implemented (by design): recommendations/ML, coupons, inventory, analytics (optional features were skipped to keep the core stable).

## 3. Technology
Java 21 · Spring Boot 3.3.5 · Maven · Spring Web · Spring Data JPA/Hibernate · MySQL 8 · Spring Security + JWT (jjwt 0.12.6) · BCrypt · Jakarta Validation · JUnit 5 · Mockito · Lombok (entities only).

## 4. Prerequisites (Windows)
1. **JDK 21** (e.g. Temurin) — check with `java -version`.
2. **Maven 3.9+** — check with `mvn -v`. (A Maven Wrapper was *not* included because it could not be generated offline; plain `mvn` works the same.)
3. **MySQL 8** running on port 3306.
4. **Postman** (for API testing).

## 5. Database setup
The app uses `createDatabaseIfNotExist=true`, so the database is normally created automatically. To create it manually:
```sql
CREATE DATABASE foodloop_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
Tables are created/updated by Hibernate (`spring.jpa.hibernate.ddl-auto=update`). That is convenient for learning; **production systems should use a migration tool such as Flyway or Liquibase instead.**

## 6. Configuration (environment variables)
| Variable | Meaning | Default |
|---|---|---|
| `DB_USERNAME` | MySQL user | `root` |
| `DB_PASSWORD` | MySQL password | *(empty)* |
| `JWT_SECRET` | ≥ 32 characters; signs tokens | a dev-only fallback (**set your own**) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Creates the first admin at startup (only if both set; password ≥ 8 chars) | not set |

PowerShell (current window only):
```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_mysql_password"
$env:JWT_SECRET  = "put-a-long-random-string-of-at-least-32-characters"
$env:ADMIN_EMAIL = "admin@foodloop.local"
$env:ADMIN_PASSWORD = "ChangeMe123"
```
Spring Boot does **not** read a `.env` file automatically — set variables in the terminal (or in your IDE run configuration) before starting.

### Creating the first administrator safely
Registering through the API always creates a `USER` (a `role` field in the request is ignored). To get an admin, start the app **once** with `ADMIN_EMAIL` and `ADMIN_PASSWORD` set; `AdminSeeder` creates the account if that email doesn't exist. Then remove the variables if you like. No credentials are hard-coded.

## 7. Build, test, run
```powershell
mvn clean test        # compiles + runs the unit tests (no MySQL needed)
mvn spring-boot:run   # starts on http://localhost:8080 (needs MySQL)
```
Or `mvn clean package` then `java -jar target/foodloop-1.0.0.jar`.

## 8. Request flow (Controller → Service → Repository → DB)
```
Postman ──HTTP──► JwtAuthFilter (checks token) ──► Controller (@Valid DTO, thin)
                                                        │
                                                   Service (business rules, @Transactional)
                                                        │
                                                 Repository (Spring Data JPA)
                                                        │
                                                  MySQL (foodloop_db)
Response DTO ◄── Service maps Entity → DTO ◄── Repository returns entities
Errors anywhere ──► GlobalExceptionHandler ──► consistent JSON { timestamp, status, error, message, fieldErrors }
```

## 9. Key design decisions
* **`CustomerOrder` entity** (table `orders`): `Order` is a reserved SQL word, so the class has a safe name.
* **DTO records** are grouped in a few holder classes (`AuthDtos`, `CatalogDtos`, …) to keep the file count manageable.
* **Snapshots:** `order_items` stores `food_name`, `unit_price`, `line_total`, so old orders never change when the menu changes. Food that was ever ordered cannot be deleted (409) — mark it unavailable.
* **Cart totals** are never sent by the client; prices come from the database (deal price if a deal is valid).
* **Ownership:** the cart is looked up by the logged-in user; orders are checked (`403` for other users' orders; admins may view any).
* **Order lifecycle** (enforced in `OrderStatus`): `PLACED → CONFIRMED → PREPARING → READY → OUT_FOR_DELIVERY → DELIVERED`. Customers may cancel in `PLACED`/`CONFIRMED`; admins may also cancel in `PREPARING`. All other jumps → `409`.
* **Preparation estimate** = longest item prep time + 2 min per extra unit (cap 30) + 2 min per pending order ahead (cap 30). Pending = `PLACED/CONFIRMED/PREPARING`. It is an estimate of kitchen time, **not** a delivery promise.
* **Surplus deals:** a deal is valid only if `active`, `start ≤ now < end`, and not sold out — evaluated on every read/purchase, so an expired deal can never be bought even without a cleanup job. At checkout the deal row is locked (`SELECT … FOR UPDATE`, `PESSIMISTIC_WRITE`) inside the order transaction. If you order more units than remain at the deal price you get `409` (reduce quantity); if none remain, the regular price applies. Cancelling an order returns deal units.
* **Security:** stateless JWT; roles read from the DB on each request; `/api/admin/**` and all catalog writes need `ADMIN`; JSON bodies for 401/403.

## 10. Postman testing
1. Import `FoodLoop.postman_collection.json` (Postman → Import).
2. Start MySQL and the app **with `ADMIN_EMAIL`/`ADMIN_PASSWORD` set**. In the collection's *Variables* tab set `adminEmail` / `adminPassword` to the same values.
3. Run the folders **in order 1 → 7** (Collection Runner works). Tokens and ids (`userToken`, `adminToken`, `categoryId`, `foodId`, `orderId`, `dealId` …) are saved automatically by test scripts. Each request name starts with its expected status, e.g. `[403]`.
4. If you re-run the collection, change `userEmail` first (email must be unique).

## 11. API summary
Full details (roles, bodies, responses, errors) are in [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md). Database design and ER diagram: [`DATABASE_DESIGN.md`](DATABASE_DESIGN.md). Viva/presentation help: [`PRESENTATION_GUIDE.md`](PRESENTATION_GUIDE.md).

## 12. Troubleshooting
| Problem | Fix |
|---|---|
| `Communications link failure` | MySQL isn't running / wrong port |
| `Access denied for user` | Wrong `DB_USERNAME` / `DB_PASSWORD` |
| `JWT_SECRET must be at least 32 characters` | Use a longer secret |
| `401` on every request | Missing `Authorization: Bearer <token>` header or expired token (24 h) |
| `403` on admin APIs | Logged in as a customer; log in with the seeded admin |
| Port 8080 busy | Add `server.port=8081` to `application.properties` |
| Admin login fails | Start the app once with `ADMIN_EMAIL`/`ADMIN_PASSWORD` set |

## 13. Limitations & future scope
No payments, no delivery-partner tracking, no pagination, no refresh tokens, no rate limiting, schema managed by `ddl-auto=update`, integration tests against a real DB are not included. Future: coupons, stock tracking, analytics, Flyway, Swagger/OpenAPI, and integration of your separate recommendation service (it can consume `GET /api/foods` and `GET /api/orders/my` through the existing REST API — nothing recommendation-specific was added here).

## 14. Verification checklist (do this first)
- [ ] `mvn -v` shows Java 21
- [ ] `mvn clean test` — all tests green (fix/report any compile errors)
- [ ] `mvn spring-boot:run` connects to MySQL; tables appear in `foodloop_db`
- [ ] Run the Postman collection folders 1 → 7
