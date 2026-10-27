# FoodLoop — Presentation & Viva Guide

## 1. Project in 30 seconds
FoodLoop is a Spring Boot REST API for online food ordering. Customers browse food, fill a cart, place an order and follow its progress on a timeline. Admins manage the menu, orders and **surplus deals** — discounted food that must be sold soon, which helps reduce food waste. Everything is secured with JWT and stored in MySQL.

## 2. Problem & objectives
Problem: ordering systems often trust the client for prices, hide order progress, and ignore food waste.
Objectives: secure ordering with server-side prices · transparent order timeline · honest preparation estimate · discounted surplus deals · clean layered code.

## 3. Five-minute script
1. **(30 s) Intro** – name, problem, what FoodLoop does.
2. **(45 s) Architecture** – Controller → Service → Repository → MySQL; DTOs; JWT filter; global exception handler.
3. **(60 s) Database** – 9 tables; show the ER diagram: User–Cart (1:1), User–Order (1:N), Category–Food (1:N), Order–OrderItem (1:N), Order–History (1:N), Food–Deal (1:N). Mention price snapshots.
4. **(90 s) Live demo in Postman** – login as customer → add to cart → place order → show timeline → login as admin → confirm order → show timeline again → show a deal and the discounted price.
5. **(45 s) Security** – BCrypt, JWT, roles, ownership checks (show a 403 when another user opens my order), no client-controlled totals/roles.
6. **(30 s) Testing & limits** – JUnit/Mockito tests of business rules, Postman collection; limitations and future scope (recommendation service will plug in through the REST API).

## 4. Architecture explanation
* **Controller** – receives HTTP, validates the DTO with `@Valid`, calls one service method, returns JSON. No business logic.
* **Service** – all business rules and `@Transactional` boundaries.
* **Repository** – interfaces extending `JpaRepository`; Spring generates the SQL.
* **Entity** – Java classes mapped to tables. **DTO** – what goes in/out of the API (never expose entities).
* **Security** – `JwtAuthFilter` reads the token on each request; `SecurityConfig` maps URLs to roles.
* **Exception** – `GlobalExceptionHandler` turns exceptions into consistent JSON with correct status codes.

## 5. Walkthrough: placing an order (`POST /api/orders`)
1. `JwtAuthFilter` validates the token and loads the user.
2. `OrderController.place` validates the address and calls `OrderService.placeOrder`.
3. Inside **one transaction** the service: loads the user's cart → rejects it if empty → for each cart line loads the *current* food from the DB, rejects it if unavailable → looks for a valid deal and **locks** it → picks the deal or regular price → builds `OrderItem`s with name/price snapshots → sums the total → computes the estimate → adds the first history row → saves the order → clears the cart.
4. If anything throws, the transaction **rolls back**: no half-created order, cart untouched, deal counters unchanged.
5. The order is mapped to `OrderResponse` and returned with `201`.

## 6. Security explanation
Passwords: BCrypt hash (one-way, salted). Login returns a signed JWT (HMAC-SHA, 24 h). Each request sends `Authorization: Bearer …`. Roles come from the database. `/api/admin/**` and menu writes need ADMIN. Registration ignores any role field. Cart is found through the logged-in user; orders are compared with the owner (403 otherwise). Errors never reveal stack traces or secrets.

## 7. Important files to know
`OrderService` (core logic) · `OrderStatus` (allowed transitions) · `SurplusDealService` + `SurplusDealRepository` (deal rules + locking) · `PreparationEstimator` · `SecurityConfig` / `JwtAuthFilter` / `JwtService` · `GlobalExceptionHandler` · entities in `entity/`.

## 8. Viva questions and simple answers
1. **What is Spring Boot?** A framework that auto-configures a Spring app so we can build a web service quickly with embedded Tomcat and minimal setup.
2. **What is REST?** Using HTTP methods (GET/POST/PUT/PATCH/DELETE) on URLs that represent resources, returning JSON.
3. **Why layered architecture?** Each layer has one job, so code is easier to read, test and change.
4. **What does a repository do?** Talks to the database. We only write interfaces; Spring Data JPA implements them.
5. **What is JPA / Hibernate?** JPA is the standard for mapping Java objects to tables; Hibernate is the implementation that generates SQL.
6. **Why DTOs instead of entities?** To hide sensitive fields (password hash), avoid infinite JSON loops between related entities, and control exactly what the API accepts.
7. **What is `@Transactional`?** It groups database operations so they all succeed or all roll back.
8. **Why is order placement transactional?** To avoid half-saved orders, e.g. order saved but cart not cleared or deal counter changed.
9. **How is the password stored?** BCrypt-hashed. We compare with `matches`; the original password is never stored.
10. **What is JWT?** A signed token with the user's identity and expiry. The server verifies the signature, so no session is stored.
11. **Difference between authentication and authorization?** Authentication = who you are (login). Authorization = what you may do (roles).
12. **How do you stop users making themselves admin?** `RegisterRequest` has no role field and the service always sets `USER`. Admin is created only via environment variables at startup.
13. **How do you stop one user reading another's order?** `loadAuthorized` compares the order owner with the logged-in email and throws 403.
14. **Why can't the client send the price?** A client could cheat. The server reads prices from the database and calculates totals.
15. **What are price snapshots?** `order_items` copies name and price at purchase time so history stays correct after menu changes.
16. **Which relationships did you use?** 1:1 User–Cart; 1:N User–Order, Category–Food, Cart–CartItem, Order–OrderItem, Order–History, Food–Deal; N:1 items to food.
17. **What is cascade / orphanRemoval?** Cascade passes save/delete from parent to children; orphanRemoval deletes children removed from the parent's list (e.g. cart items).
18. **Why `BigDecimal` for money?** `double` has rounding errors; `BigDecimal` is exact.
19. **How does the order timeline work?** Every status change goes through `changeStatus`, which adds an `OrderStatusHistory` row (previous, new, time, who).
20. **How are invalid status changes blocked?** `OrderStatus.canTransitionTo` defines allowed next statuses; otherwise 409 Conflict.
21. **How is the preparation estimate calculated?** Longest prep time + 2 min per extra unit + 2 min per pending order, with caps. It is an estimate, not a delivery promise.
22. **What is a surplus deal?** A time-limited discount (optionally quantity-limited) so surplus food is sold instead of wasted.
23. **How do you prevent an expired deal being used?** Validity (`active`, time window, stock) is checked at purchase time, so no cleanup job is needed.
24. **How do you prevent overselling a limited deal?** The deal row is locked with `PESSIMISTIC_WRITE` inside the order transaction, so concurrent orders wait and then see the updated count.
25. **What does 409 mean and when do you use it?** Conflict with current state: duplicate email, invalid status change, deal sold out, deleting something still in use.
26. **How do you handle errors?** `@RestControllerAdvice` maps exceptions to a standard JSON body and the right status code; unknown errors return a generic 500 message.
27. **How did you test?** Mockito unit tests for business rules (totals, deals, cancellation, ownership, auth, validation of deals), plus a Postman collection for API flows.
28. **Why is `ddl-auto=update` not for production?** It can't safely manage schema changes; production should use Flyway/Liquibase migrations.
29. **How will the recommender integrate later?** As a separate service using the existing REST APIs (menu and order history). No recommendation code exists in this project.
30. **What are the limitations?** No payments, pagination, refresh tokens or rate limiting; integration tests with a real database are not included.

## 9. Honest status line for your viva
"The code was designed and unit-tested with mocks; end-to-end behaviour is demonstrated through the Postman collection against MySQL." (Only say this once you have actually run them!)
