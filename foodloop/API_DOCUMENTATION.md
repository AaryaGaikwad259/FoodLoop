# FoodLoop — API Documentation

Base URL: `http://localhost:8080` · JSON everywhere · Protected endpoints need `Authorization: Bearer <token>`.

**Roles:** *Public* = no token · *USER* = any logged-in user · *ADMIN* = admin only.

**Error format (all errors):**
```json
{ "timestamp": "2026-10-25T10:15:30", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "fieldErrors": { "email": "Email format is invalid" } }
```
(`fieldErrors` only appears for validation errors.) Status codes: 200, 201, 204, 400, 401, 403, 404, 409, 500.

---
## Authentication
### POST `/api/auth/register` — Public
Body: `{ "name":"Asha Patil", "email":"asha@example.com", "password":"Passw0rd1", "phone":"9876543210", "address":"12 Main Street" }`
(`password` 8–64 chars with a letter and a digit; `phone`, `address` optional; any `role` sent is ignored.)
**201** → `{ "id":1, "name":"Asha Patil", "email":"asha@example.com", "phone":"...", "address":"...", "role":"USER" }`
Errors: 400 validation, 409 email exists.

### POST `/api/auth/login` — Public
Body: `{ "email":"asha@example.com", "password":"Passw0rd1" }`
**200** → `{ "token":"<jwt>", "tokenType":"Bearer", "expiresInMs":86400000, "user":{...} }`
Errors: 400 validation, 401 invalid credentials.

## User
| Method & URL | Role | Body | Response |
|---|---|---|---|
| GET `/api/users/me` | USER | – | 200 profile |
| PUT `/api/users/me` | USER | `{ "name":"..", "phone":"..", "address":".." }` | 200 profile. Errors 400, 401 |

## Categories
| Method & URL | Role | Body | Response / errors |
|---|---|---|---|
| GET `/api/categories` | Public | – | 200 list (customers see active only; admin sees all) |
| GET `/api/categories/{id}` | Public | – | 200 · 404 |
| POST `/api/categories` | ADMIN | `{ "name":"Pizza", "description":"..", "active":true }` | 201 · 400 · 401 · 403 · 409 duplicate name |
| PUT `/api/categories/{id}` | ADMIN | same | 200 · 400 · 404 · 409 |
| DELETE `/api/categories/{id}` | ADMIN | – | 204 · 404 · 409 (still has food) |

Response: `{ "id":1, "name":"Pizza", "description":"..", "active":true }`

## Food
| Method & URL | Role | Notes |
|---|---|---|
| GET `/api/foods?name=&categoryId=&available=` | Public | Customers always get available items only; `available` filter works for admin. 200 list |
| GET `/api/foods/{id}` | Public | 200 · 404 (unavailable items are 404 for customers) |
| POST `/api/foods` | ADMIN | 201 · 400 (also unknown/inactive category) · 403 |
| PUT `/api/foods/{id}` | ADMIN | 200 · 400 · 404 |
| PATCH `/api/foods/{id}/availability` | ADMIN | body `{ "available": false }` → 200 |
| DELETE `/api/foods/{id}` | ADMIN | 204 · 404 · 409 (appears in past orders → mark unavailable) |

Request body: `{ "name":"Margherita", "description":"..", "price":250.00, "imageUrl":"https://..", "available":true, "prepTimeMinutes":20, "categoryId":1 }`
(`price` > 0, ≤ 2 decimals; `prepTimeMinutes` 1–240.)
Response: `{ "id":5, "name":"..", "description":"..", "price":250.00, "imageUrl":"..", "available":true, "prepTimeMinutes":20, "categoryId":1, "categoryName":"Pizza" }`

## Cart (USER — always the caller's own cart)
| Method & URL | Body | Response |
|---|---|---|
| GET `/api/cart` | – | 200 cart |
| POST `/api/cart/items` | `{ "foodItemId":5, "quantity":2 }` | 201 cart · 400 (qty<1, >50, unavailable) · 404 food |
| PATCH `/api/cart/items/{itemId}` | `{ "quantity":3 }` | 200 · 400 · 404 (not in your cart) |
| DELETE `/api/cart/items/{itemId}` | – | 200 cart · 404 |
| DELETE `/api/cart` | – | 204 |

Cart response: `{ "id":1, "items":[{ "id":9, "foodItemId":5, "foodName":"..", "regularPrice":250.00, "unitPrice":175.00, "dealApplied":true, "quantity":2, "lineTotal":350.00 }], "totalUnits":2, "totalAmount":350.00 }`

## Orders (USER)
| Method & URL | Body | Response / errors |
|---|---|---|
| POST `/api/orders` | `{ "deliveryAddress":"45 Station Road, Aurangabad" }` | 201 order · 400 empty cart / unavailable item / bad address · 409 deal quantity exceeded |
| GET `/api/orders/my` | – | 200 list (newest first) |
| GET `/api/orders/{id}` | – | 200 · 403 not yours · 404 |
| PATCH `/api/orders/{id}/cancel` | – | 200 · 403 · 404 · 409 (status doesn't allow cancel) |
| GET `/api/orders/{id}/timeline` | – | 200 `[ { "previousStatus":null, "newStatus":"PLACED", "changedAt":"..", "changedBy":"asha@example.com", "description":"Order placed by customer" } ]` |
| GET `/api/orders/{id}/estimate` | – | 200 `{ "orderId":1, "status":"PLACED", "estimatedPrepMinutes":22, "estimatedReadyAt":"..", "note":"This is an ESTIMATE ..." }` |

Order response: `{ "id":1, "status":"PLACED", "totalAmount":500.00, "deliveryAddress":"..", "orderDate":"..", "estimatedPrepMinutes":22, "customerName":"..", "customerEmail":"..", "items":[{ "id":1, "foodItemId":5, "foodName":"..", "unitPrice":250.00, "quantity":2, "lineTotal":500.00, "dealApplied":false }] }`

Customers can cancel only in `PLACED` or `CONFIRMED`. Admins may view any order/timeline/estimate through these same URLs.

## Admin orders (ADMIN)
| Method & URL | Body | Response / errors |
|---|---|---|
| GET `/api/admin/orders?status=PLACED` | – | 200 list (status optional) · 400 bad status value |
| PATCH `/api/admin/orders/{id}/status` | `{ "status":"CONFIRMED", "note":"optional" }` | 200 · 404 · 409 invalid transition |

Allowed transitions: PLACED→CONFIRMED/CANCELLED · CONFIRMED→PREPARING/CANCELLED · PREPARING→READY/CANCELLED · READY→OUT_FOR_DELIVERY · OUT_FOR_DELIVERY→DELIVERED.

## Surplus deals
| Method & URL | Role | Notes |
|---|---|---|
| GET `/api/surplus-deals` | Public | only deals valid *now* (active, in time window, not sold out) |
| GET `/api/surplus-deals/{id}` | Public | 200 · 404 (includes `currentlyValid` flag) |
| GET `/api/admin/surplus-deals` | ADMIN | all deals |
| POST `/api/admin/surplus-deals` | ADMIN | 201 · 400 · 403 |
| PUT `/api/admin/surplus-deals/{id}` | ADMIN | 200 · 400 · 404 |
| PATCH `/api/admin/surplus-deals/{id}/status` | ADMIN | body `{ "active": false }` → 200 (deactivate) |
| DELETE `/api/admin/surplus-deals/{id}` | ADMIN | 204 · 404 · 409 (used in orders → deactivate) |

Deal request: `{ "foodItemId":5, "discountPercent":30, "startTime":"2026-10-25T10:00:00", "endTime":"2026-10-26T22:00:00", "quantityLimit":10, "active":true }`
Send **either** `discountPercent` (1–99) **or** `discountedPrice` (must be below the regular price) — the server computes the other. `endTime` must be after `startTime` and in the future. `quantityLimit` is optional (omit = unlimited).
Deal response: `{ "id":3, "foodItemId":5, "foodName":"..", "originalPrice":250.00, "discountedPrice":175.00, "discountPercent":30.00, "startTime":"..", "endTime":"..", "quantityLimit":10, "quantitySold":0, "remainingQuantity":10, "active":true, "currentlyValid":true }`
