import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import "./App.css";

const API = "http://localhost:8080/api";

const CATEGORIES = [
  "All", "Pizza", "Burgers", "Noodles & Chinese", "Pasta",
  "Sandwiches & Wraps", "Indian Main Course", "Rice & Biryani",
  "Snacks & Sides", "Desserts", "Beverages",
];

const HERO_IMAGE =
  "https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=1200&q=85";

const DEALS = [
  {
    id: "deal1",
    title: "The Weekend Treat",
    description: "A little something for your next comfort-food night.",
    discount: "20% OFF",
    code: "WEEKEND20",
    image: "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=80",
  },
  {
    id: "deal2",
    title: "Burger Break",
    description: "Make your burger break a little more special.",
    discount: "Combo offer",
    code: "BURGERCOMBO",
    image: "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=900&q=80",
  },
  {
    id: "deal3",
    title: "Sweet Ending",
    description: "Save room for something sweet after your meal.",
    discount: "Dessert deal",
    code: "SWEET10",
    image: "https://images.unsplash.com/photo-1563805042-7684c019e1cb?auto=format&fit=crop&w=900&q=80",
  },
];

const money = (value) =>
  `₹${Number(value || 0).toLocaleString("en-IN", {
    maximumFractionDigits: 2,
  })}`;

const readStore = (key, fallback) => {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
};

const getCategory = (food) =>
  food.categoryName || food.category?.name || food.category || "Food";

// Attach the logged-in user's JWT to protected API requests.
const authConfig = () => {
  const token = localStorage.getItem("foodloop-token");

  return {
    timeout: 10000,
    headers: token
      ? { Authorization: `Bearer ${token}` }
      : {},
  };
};

// Convert backend cart DTOs into the format used by the existing UI.
function mapCartItem(item, foods) {
  const food = foods.find(
    (f) => String(f.id) === String(item.foodItemId)
  );

  return {
    id: item.id, // Database cart-item ID, used for PATCH/DELETE
    foodId: item.foodItemId,
    name: item.foodName,
    price: Number(item.unitPrice ?? item.regularPrice ?? 0),
    regularPrice: Number(item.regularPrice ?? item.unitPrice ?? 0),
    quantity: item.quantity,
    imageUrl: food?.imageUrl || food?.imageURL || food?.image || "",
    categoryName: food ? getCategory(food) : "Food",
    dealApplied: Boolean(item.dealApplied),
  };
}

function getErrorMessage(error, fallback) {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    fallback
  );
}

function formatStatus(status) {
  if (!status) return "Order placed";

  return String(status)
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function App() {
  const [foods, setFoods] = useState([]);
  const [loadingFoods, setLoadingFoods] = useState(true);
  const [menuError, setMenuError] = useState("");
  const [recommendations, setRecommendations] = useState([]);
  const [loadingRecommendations, setLoadingRecommendations] = useState(true);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All");
  const [sortBy, setSortBy] = useState("recommended");

  // Cart and orders now come from the backend, not localStorage.
  const [cart, setCart] = useState([]);
  const [orders, setOrders] = useState([]);

  const [page, setPage] = useState("home");
  const [authMode, setAuthMode] = useState("login");

  const [user, setUser] = useState(() => {
    const token = localStorage.getItem("foodloop-token");
    return token ? readStore("foodloop-user", null) : null;
  });

  const [notice, setNotice] = useState("");
  const [noticeTimer, setNoticeTimer] = useState(null);

  const [authForm, setAuthForm] = useState({
    name: "",
    email: "",
    phone: "",
    password: "",
  });

  const [checkoutForm, setCheckoutForm] = useState({
    name: "",
    phone: "",
    address: "",
    city: "",
    pincode: "",
    payment: "Cash on Delivery",
    instructions: "",
  });

  const [authError, setAuthError] = useState("");
  const [checkoutError, setCheckoutError] = useState("");
  const [mobileMenu, setMobileMenu] = useState(false);

  // Remove old frontend-only demo data.
  useEffect(() => {
    localStorage.removeItem("foodloop-cart");
    localStorage.removeItem("foodloop-demo-orders");
    localStorage.removeItem("foodloop-demo-user");
  }, []);

  // Keep the authenticated user available after a page refresh.
  useEffect(() => {
    if (user && localStorage.getItem("foodloop-token")) {
      localStorage.setItem("foodloop-user", JSON.stringify(user));
    } else {
      localStorage.removeItem("foodloop-user");
    }

    localStorage.removeItem("foodloop-demo-user");
  }, [user]);

  // Fetch the public food menu.
  useEffect(() => {
    let active = true;

    axios
      .get(`${API}/foods`, { timeout: 8000 })
      .then((response) => {
        const body = response.data;

        const data = Array.isArray(body)
          ? body
          : Array.isArray(body?.content)
            ? body.content
            : Array.isArray(body?.data)
              ? body.data
              : null;

        if (!data) {
          throw new Error("Unexpected menu response");
        }

        if (active) {
          setFoods(data);
          setMenuError("");
        }
      })
      .catch((error) => {
        console.error("FoodLoop menu request failed:", error);

        if (active) {
          setMenuError(
            "We couldn't load the live menu. Check that Spring Boot is running, then try again."
          );
        }
      })
      .finally(() => {
        if (active) setLoadingFoods(false);
      });

    return () => {
      active = false;
    };
  }
  , []);

  // Fetch ML-powered food recommendations.
  useEffect(() => {
    let active = true;

    axios
      .get(`${API}/recommendations?preference=popular%20tasty%20food&limit=5`, {
        timeout: 15000,
      })
      .then((response) => {
        const body = response.data;

        const data = Array.isArray(body)
          ? body
          : body?.recommendations ||
            body?.content ||
            body?.data ||
            [];

        if (active) {
          setRecommendations(Array.isArray(data) ? data : []);
        }
      })
      .catch((error) => {
        console.error("Recommendation request failed:", error);
        if (active) setRecommendations([]);
      })
      .finally(() => {
        if (active) setLoadingRecommendations(false);
      });

    return () => {
      active = false;
    };
  }, []);

  // Load the current user's cart from the database.
  async function refreshCart() {
    if (!localStorage.getItem("foodloop-token")) {
      setCart([]);
      return;
    }

    try {
      const response = await axios.get(
        `${API}/cart`,
        authConfig()
      );

      const backendItems = Array.isArray(response.data?.items)
        ? response.data.items
        : [];

      setCart(
        backendItems.map((item) => mapCartItem(item, foods))
      );
    } catch (error) {
      console.error("Could not load cart:", error);

      if (error.response?.status === 401) {
        signOut(false);
        notify("Your session has expired. Please log in again.");
      } else {
        notify(
          getErrorMessage(error, "Could not load your cart.")
        );
      }
    }
  }

  // Load orders belonging to the current user.
  async function refreshOrders() {
    if (!localStorage.getItem("foodloop-token")) {
      setOrders([]);
      return;
    }

    try {
      const response = await axios.get(
        `${API}/orders/my`,
        authConfig()
      );

      setOrders(Array.isArray(response.data) ? response.data : []);
    } catch (error) {
      console.error("Could not load orders:", error);

      if (error.response?.status === 401) {
        signOut(false);
        notify("Your session has expired. Please log in again.");
      } else {
        notify(
          getErrorMessage(error, "Could not load your order history.")
        );
      }
    }
  }

  // When a user logs in, fetch their database cart and order history.
  // When they log out, don't show the previous account's data.
  useEffect(() => {
    let active = true;

    async function loadAccountData() {
      if (!user || !localStorage.getItem("foodloop-token")) {
        setCart([]);
        setOrders([]);
        return;
      }

      try {
        const [cartResponse, ordersResponse] = await Promise.all([
          axios.get(`${API}/cart`, authConfig()),
          axios.get(`${API}/orders/my`, authConfig()),
        ]);

        if (!active) return;

        const backendItems = Array.isArray(cartResponse.data?.items)
          ? cartResponse.data.items
          : [];

        setCart(
          backendItems.map((item) => mapCartItem(item, foods))
        );

        setOrders(
          Array.isArray(ordersResponse.data)
            ? ordersResponse.data
            : []
        );
      } catch (error) {
        if (!active) return;

        console.error("Could not load account data:", error);

        if (error.response?.status === 401) {
          signOut(false);
          notify("Please log in again to access your account.");
        } else {
          notify(
            getErrorMessage(
              error,
              "Could not load your account data. Please try again."
            )
          );
        }
      }
    }

    loadAccountData();

    return () => {
      active = false;
    };
  }, [user, foods]);

  // Handle toast notifications.
  useEffect(() => {
    if (noticeTimer) window.clearTimeout(noticeTimer);

    if (notice) {
      const timer = window.setTimeout(() => setNotice(""), 2600);
      setNoticeTimer(timer);
    }

    return () => {
      if (noticeTimer) window.clearTimeout(noticeTimer);
    };
  }, [notice]);

  const filteredFoods = useMemo(() => {
    const query = search.trim().toLowerCase();

    const list = foods.filter((food) => {
      const matchesText = [
        food.name,
        food.description,
        getCategory(food),
      ].some((value) =>
        String(value || "").toLowerCase().includes(query)
      );

      return (
        matchesText &&
        (category === "All" ||
          getCategory(food).toLowerCase() === category.toLowerCase())
      );
    });

    if (sortBy === "price-low") {
      list.sort((a, b) => Number(a.price) - Number(b.price));
    }

    if (sortBy === "price-high") {
      list.sort((a, b) => Number(b.price) - Number(a.price));
    }

    if (sortBy === "name") {
      list.sort((a, b) =>
        String(a.name || "").localeCompare(String(b.name || ""))
      );
    }

    return list;
  }, [foods, search, category, sortBy]);

  const cartCount = cart.reduce(
    (sum, item) => sum + Number(item.quantity || 0),
    0
  );

  const subtotal = cart.reduce(
    (sum, item) =>
      sum + Number(item.price || 0) * Number(item.quantity || 0),
    0
  );

  const deliveryFee =
    subtotal === 0 || subtotal >= 499 ? 0 : 35;

  const tax = Math.round(subtotal * 0.05 * 100) / 100;
  const grandTotal = subtotal + deliveryFee + tax;

  function notify(message) {
    setNotice(message);
  }

  function go(nextPage) {
    setPage(nextPage);
    setMobileMenu(false);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  // Add an item to the authenticated user's backend cart.
  async function addToCart(food) {
    if (food.available === false) return;

    if (!user || !localStorage.getItem("foodloop-token")) {
      notify("Please log in before adding items to your cart.");
      startAuth("login");
      return;
    }

    try {
      await axios.post(
        `${API}/cart/items`,
        {
          foodItemId: food.id,
          quantity: 1,
        },
        authConfig()
      );

      await refreshCart();
      notify(`${food.name} added to your cart`);
    } catch (error) {
      console.error("Could not add item to cart:", error);

      notify(
        getErrorMessage(error, "Could not add this item to your cart.")
      );
    }
  }

  // Update a database cart item's quantity.
  async function changeQuantity(cartItemId, delta) {
    const item = cart.find(
      (current) => String(current.id) === String(cartItemId)
    );

    if (!item) return;

    const newQuantity = item.quantity + delta;

    try {
      if (newQuantity <= 0) {
        await axios.delete(
          `${API}/cart/items/${cartItemId}`,
          authConfig()
        );
      } else {
        await axios.patch(
          `${API}/cart/items/${cartItemId}`,
          { quantity: newQuantity },
          authConfig()
        );
      }

      await refreshCart();
    } catch (error) {
      console.error("Could not update cart:", error);

      notify(
        getErrorMessage(error, "Could not update your cart.")
      );
    }
  }

  async function clearCart() {
    try {
      await axios.delete(`${API}/cart`, authConfig());
      setCart([]);
      notify("Your cart has been cleared.");
    } catch (error) {
      console.error("Could not clear cart:", error);

      notify(
        getErrorMessage(error, "Could not clear your cart.")
      );
    }
  }

  function showMenu() {
    go("menu");
  }

  function startAuth(mode) {
    setAuthMode(mode);
    setAuthError("");

    setAuthForm({
      name: "",
      email: "",
      phone: "",
      password: "",
    });

    go("auth");
  }

  async function submitAuth(event) {
    event.preventDefault();
    setAuthError("");

    const email = authForm.email.trim().toLowerCase();
    const password = authForm.password;

    if (!email || !password) {
      setAuthError("Enter your email and password.");
      return;
    }

    if (authMode === "register" && !authForm.name.trim()) {
      setAuthError("Please enter your name.");
      return;
    }

    try {
      if (authMode === "register") {
        await axios.post(`${API}/auth/register`, {
          name: authForm.name.trim(),
          email,
          phone: authForm.phone.trim(),
          password,
        });
      }

      const response = await axios.post(`${API}/auth/login`, {
        email,
        password,
      });

      const data = response.data;

      if (!data?.token || !data?.user) {
        throw new Error("Invalid authentication response from server.");
      }

      const loggedInUser = {
        name: data.user.name,
        email: data.user.email,
        phone: data.user.phone || "",
        role: data.user.role,
      };

      // Save the token before updating user state.
      localStorage.setItem("foodloop-token", data.token);
      localStorage.setItem(
        "foodloop-user",
        JSON.stringify(loggedInUser)
      );

      setUser(loggedInUser);

      setCheckoutForm((old) => ({
        ...old,
        name: loggedInUser.name || "",
        phone: loggedInUser.phone || "",
      }));

      notify(
        authMode === "register"
          ? "Account created successfully!"
          : "Login successful!"
      );

      go("home");
    } catch (error) {
      console.error("Authentication failed:", error);

      if (error.response?.status === 401) {
        setAuthError("Invalid email or password.");
      } else if (error.response?.status === 409) {
        setAuthError("An account with this email already exists.");
      } else if (error.response?.status === 400) {
        setAuthError(
          error.response.data?.message ||
          "Please check your details and try again."
        );
      } else if (!error.response) {
        setAuthError(
          "Cannot connect to the server. Check that the backend is running."
        );
      } else {
        setAuthError(
          error.response.data?.message ||
          "Authentication failed. Please try again."
        );
      }
    }
  }

  function signOut(showMessage = true) {
    localStorage.removeItem("foodloop-token");
    localStorage.removeItem("foodloop-user");
    localStorage.removeItem("foodloop-demo-user");

    setUser(null);
    setCart([]);
    setOrders([]);

    if (showMessage) {
      notify("You have signed out");
    }
  }

  // Place an actual order through the Spring Boot backend.
  async function submitOrder(event) {
    event.preventDefault();
    setCheckoutError("");

    if (!user || !localStorage.getItem("foodloop-token")) {
      setCheckoutError("Please log in before placing an order.");
      return;
    }

    if (!cart.length) {
      setCheckoutError("Your cart is empty.");
      return;
    }

    if (
      !checkoutForm.name.trim() ||
      !checkoutForm.phone.trim() ||
      !checkoutForm.address.trim() ||
      !checkoutForm.city.trim() ||
      !checkoutForm.pincode.trim()
    ) {
      setCheckoutError(
        "Please complete your name, phone number and delivery address."
      );
      return;
    }

    // The backend currently accepts only deliveryAddress.
    // Combine the address, city and PIN into that field.
    const deliveryAddress = [
      checkoutForm.address.trim(),
      checkoutForm.city.trim(),
      `PIN: ${checkoutForm.pincode.trim()}`,
      checkoutForm.instructions.trim()
        ? `Instructions: ${checkoutForm.instructions.trim()}`
        : "",
    ]
      .filter(Boolean)
      .join(", ");

    if (deliveryAddress.length > 255) {
      setCheckoutError(
        "Your combined delivery address is too long. Please shorten it to fit within 255 characters."
      );
      return;
    }

    if (deliveryAddress.length < 10) {
      setCheckoutError(
        "Please enter a more complete delivery address."
      );
      return;
    }

    try {
      const response = await axios.post(
        `${API}/orders`,
        { deliveryAddress },
        authConfig()
      );

      const placedOrder = response.data;

      // The backend creates the order and clears the database cart.
      setCart([]);
      setCheckoutForm((old) => ({
        ...old,
        instructions: "",
      }));

      await refreshOrders();

      notify(
        `Order #${placedOrder.id} placed successfully!`
      );

      go("orders");
    } catch (error) {
      console.error("Could not place order:", error);

      setCheckoutError(
        getErrorMessage(
          error,
          "We couldn't place your order. Please check your cart and try again."
        )
      );
    }
  }

  function beginCheckout() {
    if (!cart.length) {
      notify("Add something to your cart first");
      return;
    }

    if (!user || !localStorage.getItem("foodloop-token")) {
      notify("Please log in before checkout.");
      startAuth("login");
      return;
    }

    setCheckoutForm((old) => ({
      ...old,
      name: old.name || user.name,
      phone: old.phone || user.phone || "",
    }));

    go("checkout");
  }

  return (
    <div className="app-shell" id="top">
      <div className="announcement">
        Good food, good mood.{" "}
        <span>Made for your everyday cravings.</span>
      </div>

      <header className="navbar">
        <a
          className="brand"
          href="#top"
          onClick={(e) => {
            e.preventDefault();
            go("home");
          }}
          aria-label="FoodLoop home"
        >
          Food<span>Loop</span>
          <small>GOOD FOOD. EASY DAYS.</small>
        </a>

        <button
          className="mobile-menu-toggle"
          onClick={() => setMobileMenu(!mobileMenu)}
          aria-expanded={mobileMenu}
        >
          {mobileMenu ? "Close" : "Menu"}
        </button>


        <nav
          className={`nav-links ${mobileMenu ? "nav-open" : ""}`}
          aria-label="Main navigation"
        >
          <button
            className={page === "home" ? "nav-link selected" : "nav-link"}
            onClick={() => go("home")}
          >
            Home
          </button>

          <button
            className={page === "menu" ? "nav-link selected" : "nav-link"}
            onClick={showMenu}
          >
            Menu
          </button>

          <button
            className={page === "deals" ? "nav-link selected" : "nav-link"}
            onClick={() => go("deals")}
          >
            Deals
          </button>

          <button
            className={page === "orders" ? "nav-link selected" : "nav-link"}
            onClick={() => go("orders")}
          >
            My Orders
          </button>

          {user?.role === "ADMIN" && (
            <button
              className={page === "admin" ? "nav-link selected" : "nav-link"}
              onClick={() => go("admin")}
            >
              Admin Dashboard
            </button>
          )}
        </nav>

        <div className="nav-actions">
          {user ? (
            <button
              className="account-button"
              onClick={() => go("profile")}
            >
              {user.name.split(" ")[0]}
            </button>
          ) : (
            <button
              className="login-link"
              onClick={() => startAuth("login")}
            >
              Log in
            </button>
          )}

          <button
            className="cart-button"
            onClick={() => go("cart")}
          >
            Cart <span>{cartCount}</span>
          </button>
        </div>
      </header>

      <main>
      {page === "home" && (
        <HomePage
          onMenu={showMenu}
          onDeals={() => go("deals")}
          onAdd={addToCart}
          foods={foods.slice(0, 3)}
          recommendations={recommendations}
          loadingRecommendations={loadingRecommendations}
        />
      )}

        {page === "menu" && (
          <section className="menu-section page-width">
            <div className="section-heading">
              <div>
                <span className="eyebrow">MADE FOR YOUR CRAVINGS</span>
                <h1>
                  Explore the menu<span>.</span>
                </h1>
                <p>
                  Find your next favourite, freshly chosen for you.
                </p>
              </div>

              <div className="food-count">
                {foods.length} menu items
              </div>
            </div>

            <div className="menu-tools">
              <label className="search-box">
                <span>Search</span>
                <input
                  type="search"
                  placeholder="Search pizza, burgers, pasta..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                <button
                  type="button"
                  onClick={() => setSearch("")}
                  hidden={!search}
                >
                  Clear
                </button>
              </label>

              <label className="sort-control">
                <span>Sort by</span>
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value)}
                >
                  <option value="recommended">Recommended</option>
                  <option value="price-low">Price: Low to high</option>
                  <option value="price-high">Price: High to low</option>
                  <option value="name">Name: A to Z</option>
                </select>
              </label>
            </div>

            <div className="category-list">
              {CATEGORIES.map((item) => (
                <button
                  key={item}
                  className={`category-chip ${category === item ? "active" : ""
                    }`}
                  onClick={() => setCategory(item)}
                >
                  {item}
                </button>
              ))}
            </div>

            {loadingFoods ? (
              <div className="state-panel">
                <div className="loader-line" />
                <h3>Preparing the menu</h3>
                <p>Fetching the latest dishes.</p>
              </div>
            ) : menuError ? (
              <div className="state-panel error-panel">
                <h3>Menu temporarily unavailable</h3>
                <p>{menuError}</p>

                <button
                  className="button button-dark"
                  onClick={() => {
                    setLoadingFoods(true);
                    setMenuError("");

                    axios
                      .get(`${API}/foods`)
                      .then((response) => {
                        const data = Array.isArray(response.data)
                          ? response.data
                          : response.data?.content;

                        if (!Array.isArray(data)) {
                          throw new Error("Unexpected menu response");
                        }

                        setFoods(data);
                      })
                      .catch(() =>
                        setMenuError(
                          "Still unable to connect. Confirm the backend is running on port 8080."
                        )
                      )
                      .finally(() => setLoadingFoods(false));
                  }}
                >
                  Try again
                </button>
              </div>
            ) : filteredFoods.length ? (
              <>
                <div className="results-line">
                  Showing <strong>{filteredFoods.length}</strong> dishes
                  <span>Prices in Indian rupees</span>
                </div>

                <div className="food-grid">
                  {filteredFoods.map((food) => (
                    <FoodCard
                      key={food.id}
                      food={food}
                      onAdd={addToCart}
                    />
                  ))}
                </div>
              </>
            ) : (
              <div className="state-panel">
                <h3>No dishes found</h3>
                <p>Try a different search or category.</p>

                <button
                  className="button button-outline"
                  onClick={() => {
                    setSearch("");
                    setCategory("All");
                    setSortBy("recommended");
                  }}
                >
                  Clear filters
                </button>
              </div>
            )}
          </section>
        )}

        {page === "cart" && (
          <CartPage
            cart={cart}
            subtotal={subtotal}
            deliveryFee={deliveryFee}
            tax={tax}
            total={grandTotal}
            onChange={changeQuantity}
            onMenu={showMenu}
            onCheckout={beginCheckout}
            onClear={clearCart}
          />
        )}

        {page === "auth" && (
          <AuthPage
            mode={authMode}
            form={authForm}
            setForm={setAuthForm}
            error={authError}
            onSubmit={submitAuth}
            onSwitch={() => {
              setAuthMode(
                authMode === "login" ? "register" : "login"
              );
              setAuthError("");
            }}
            onHome={() => go("home")}
          />
        )}

        {page === "checkout" && (
          <CheckoutPage
            form={checkoutForm}
            setForm={setCheckoutForm}
            error={checkoutError}
            cart={cart}
            subtotal={subtotal}
            deliveryFee={deliveryFee}
            tax={tax}
            total={grandTotal}
            onSubmit={submitOrder}
            onBack={() => go("cart")}
          />
        )}

        {page === "orders" && (
          <OrdersPage
            orders={orders}
            onMenu={showMenu}
            onSignIn={() => startAuth("login")}
            user={user}
          />
        )}

        {page === "profile" && (
          <ProfilePage
            user={user}
            orders={orders}
            onOrders={() => go("orders")}
            onSignOut={() => signOut(true)}
            onEdit={(next) => {
              setUser(next);
              notify("Profile updated in this browser.");
            }}
            onLogin={() => startAuth("login")}
          />
        )}

        {page === "deals" && (
          <DealsPage onMenu={showMenu} onNotify={notify} />
        )}
        {page === "admin" && user?.role === "ADMIN" && (
          <AdminDashboard />
        )}
      </main>

      <footer className="footer">
        <div className="footer-main">
          <a
            className="brand"
            href="#top"
            onClick={(e) => {
              e.preventDefault();
              go("home");
            }}
          >
            Food<span>Loop</span>
            <small>GOOD FOOD. EASY DAYS.</small>
          </a>

          <p>
            Good food brings people together.
            <br />
            Your next favourite is just around the corner.
          </p>

          <div className="footer-links">
            <button onClick={showMenu}>Browse menu</button>
            <button onClick={() => go("deals")}>Offers</button>
            <button onClick={() => go("orders")}>Track orders</button>
          </div>
        </div>

        <div className="footer-bottom">
          <span>© 2026 FoodLoop</span>
          <span>Fresh choices. Simple ordering.</span>
        </div>
      </footer>

      {notice && (
        <div className="toast" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}

function HomePage({ onMenu, onDeals, onAdd, foods, recommendations, loadingRecommendations}) {
  return (
    <>
      <section className="hero">
        <div className="hero-content">
          <span className="eyebrow">GOOD FOOD, GOOD MOOD</span>
          <h1>
            Your cravings.
            <br />
            <span>Your favourites.</span>
          </h1>
          <p>
            From quick bites to comfort-food classics, discover dishes
            that make your day a little better.
          </p>

          <div className="hero-actions">
            <button className="button button-dark" onClick={onMenu}>
              Explore the menu <span>→</span>
            </button>
            <button className="text-button" onClick={onDeals}>
              See today's offers
            </button>
          </div>

          <div className="hero-stats">
            <div>
              <strong>50+</strong>
              <span>Food choices</span>
            </div>
            <div>
              <strong>11</strong>
              <span>Categories</span>
            </div>
            <div>
              <strong>Easy</strong>
              <span>Everyday ordering</span>
            </div>
          </div>
        </div>

        <div className="hero-visual">
          <img src={HERO_IMAGE} alt="Freshly baked pizza on a table" />

          <div className="hero-note">
            <span className="note-label">YOUR NEXT CRAVING</span>
            <strong>Something delicious</strong>
            <small>Just a few clicks away</small>
          </div>

          <div className="hero-stamp">
            MADE WITH
            <br />
            GOOD TASTE
          </div>
        </div>
      </section>

      <section className="home-intro page-width">
        <div>
          <span className="eyebrow">
            A LITTLE SOMETHING FOR EVERYONE
          </span>
          <h2>
            Good days start
            <br />
            with good food<span>.</span>
          </h2>
        </div>

        <p>
          Explore a menu full of familiar favourites and new cravings.
          Choose what sounds good, add it to your cart, and make your
          next meal easy.
        </p>
      </section>

      <section className="featured-section page-width">
        <div className="section-heading">
          <div>
            <span className="eyebrow">POPULAR PICKS</span>
            <h2>
              Worth a little craving<span>.</span>
            </h2>
            <p>A few menu favourites to get you started.</p>
          </div>

          <button className="text-button" onClick={onMenu}>
            View full menu <span>→</span>
          </button>
        </div>

        {foods.length ? (
          <div className="food-grid featured-grid">
            {foods.map((food) => (
              <FoodCard
                key={food.id}
                food={food}
                onAdd={onAdd}
              />
            ))}
          </div>
        ) : (
          <div className="featured-placeholder">
            <p>Our menu is getting ready.</p>
            <button className="button button-outline" onClick={onMenu}>
              Browse menu
            </button>
          </div>
        )}
      </section>

    <section className="featured-section page-width">
      <div className="section-heading">
        <div>
          <span className="eyebrow">POWERED BY MACHINE LEARNING</span>
          <h2>
            Picked for your cravings<span>.</span>
          </h2>
          <p>Discover dishes recommended by our FoodLoop ML service.</p>
        </div>
      </div>

      {loadingRecommendations ? (
        <div className="state-panel">
          <h3>Finding your next favourite...</h3>
        </div>
      ) : recommendations.length > 0 ? (
        <div className="food-grid featured-grid">
          {recommendations.map((food) => (
            <FoodCard
              key={food.id}
              food={food}
              onAdd={onAdd}
            />
          ))}
        </div>
      ) : (
        <div className="featured-placeholder">
          <p>No recommendations are available right now.</p>
          <button className="button button-outline" onClick={onMenu}>
            Explore the menu
          </button>
        </div>
      )}
    </section>


      <section className="deal-banner">
        <div>
          <span className="eyebrow">A GOOD TIME TO ORDER</span>
          <h2>
            Something delicious
            <br />
            is calling.
          </h2>
          <p>
            Explore our offers and find a little extra reason to treat
            yourself.
          </p>
          <button className="button button-light" onClick={onDeals}>
            Explore offers <span>→</span>
          </button>
        </div>

        <img
          src="https://images.unsplash.com/photo-1579751626657-72bc17010498?auto=format&fit=crop&w=900&q=80"
          alt="A table full of delicious food"
          loading="lazy"
        />
      </section>

      <section className="how-section page-width">
        <div className="section-heading">
          <div>
            <span className="eyebrow">NO FUSS, JUST FOOD</span>
            <h2>
              Easy as one, two, three<span>.</span>
            </h2>
          </div>
        </div>

        <div className="steps-grid">
          <div>
            <span className="step-number">01</span>
            <h3>Find your favourite</h3>
            <p>
              Browse the menu and discover something that sounds good.
            </p>
          </div>

          <div>
            <span className="step-number">02</span>
            <h3>Make it yours</h3>
            <p>
              Add dishes to your cart and choose the quantities you want.
            </p>
          </div>

          <div>
            <span className="step-number">03</span>
            <h3>Settle in</h3>
            <p>
              Enter your delivery details and review your order before
              placing it.
            </p>
          </div>
        </div>
      </section>
    </>
  );
}

function FoodCard({ food, onAdd }) {
  const [imageFailed, setImageFailed] = useState(false);

  const image = food.imageUrl || food.imageURL || food.image;

  return (
    <article className="food-card">
      <div className="food-image">
        {image && !imageFailed ? (
          <img
            src={image}
            alt={food.name}
            loading="lazy"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="image-placeholder">
            <span>{getCategory(food)}</span>
            <small>Image unavailable</small>
          </div>
        )}

        <span className="food-category">{getCategory(food)}</span>
      </div>

      <div className="food-info">
        <div className="food-title">
          <h3>{food.name}</h3>
          <strong>{money(food.price)}</strong>
        </div>

        <p>
          {food.description ||
            "A delicious choice, made for your cravings."}
        </p>

        <div className="food-footer">
          <span>
            {food.prepTimeMinutes ??
              food.preparationTime ??
              "—"}{" "}
            min
          </span>

          <span
            className={
              food.available === false ? "unavailable" : "available"
            }
          >
            {food.available === false ? "Unavailable" : "Available"}
          </span>
        </div>

        <button
          className="add-cart-button"
          disabled={food.available === false}
          onClick={() => onAdd(food)}
        >
          {food.available === false
            ? "Currently unavailable"
            : "Add to cart"}
        </button>
      </div>
    </article>
  );
}

function PriceSummary({ subtotal, deliveryFee, tax, total }) {
  return (
    <div className="price-summary">
      <div>
        <span>Subtotal</span>
        <strong>{money(subtotal)}</strong>
      </div>

      <div>
        <span>Delivery</span>
        <strong>
          {deliveryFee === 0 ? "Free" : money(deliveryFee)}
        </strong>
      </div>

      <div>
        <span>Estimated tax (5%)</span>
        <strong>{money(tax)}</strong>
      </div>

      <div className="summary-total">
        <span>Total</span>
        <strong>{money(total)}</strong>
      </div>

      <small>
        Delivery is free on orders of ₹499 or more. Tax is an estimate
        for this frontend preview.
      </small>
    </div>
  );
}

function CartPage({
  cart,
  subtotal,
  deliveryFee,
  tax,
  total,
  onChange,
  onMenu,
  onCheckout,
  onClear,
}) {
  return (
    <section className="page-width inner-page">
      <div className="page-title-row">
        <div>
          <span className="eyebrow">YOUR SELECTION</span>
          <h1>
            Your cart<span>.</span>
          </h1>
          <p>Everything you picked, all in one place.</p>
        </div>

        <button className="text-button" onClick={onMenu}>
          Continue shopping <span>→</span>
        </button>
      </div>

      {!cart.length ? (
        <div className="empty-cart">
          <div className="empty-illustration">
            Your next favourite is waiting.
          </div>
          <h2>Your cart is empty</h2>
          <p>
            Take a look through the menu and find something delicious.
          </p>
          <button className="button button-dark" onClick={onMenu}>
            Explore the menu
          </button>
        </div>
      ) : (
        <div className="cart-layout">
          <div className="cart-list">
            {cart.map((item) => (
              <div className="cart-row" key={item.id}>
                <div className="cart-row-image">
                  {item.imageUrl ? (
                    <img src={item.imageUrl} alt={item.name} />
                  ) : (
                    <span>{getCategory(item)}</span>
                  )}
                </div>

                <div className="cart-row-info">
                  <span className="eyebrow">
                    {getCategory(item)}
                  </span>
                  <h3>{item.name}</h3>
                  <strong>{money(item.price)}</strong>

                  <div className="quantity-control">
                    <button
                      aria-label={`Remove one ${item.name}`}
                      onClick={() => onChange(item.id, -1)}
                    >
                      −
                    </button>

                    <span>{item.quantity}</span>

                    <button
                      aria-label={`Add one ${item.name}`}
                      onClick={() => onChange(item.id, 1)}
                    >
                      +
                    </button>
                  </div>
                </div>

                <strong className="row-total">
                  {money(Number(item.price) * item.quantity)}
                </strong>
              </div>
            ))}

            <button
              className="text-button clear-cart"
              onClick={() => {
                if (window.confirm("Remove all items from your cart?")) {
                  onClear();
                }
              }}
            >
              Clear cart
            </button>
          </div>

          <aside className="checkout-card">
            <span className="eyebrow">ORDER SUMMARY</span>
            <h2>Ready when you are.</h2>

            <PriceSummary
              subtotal={subtotal}
              deliveryFee={deliveryFee}
              tax={tax}
              total={total}
            />

            <button
              className="button button-dark full-width"
              onClick={onCheckout}
            >
              Continue to checkout <span>→</span>
            </button>

            <p className="secure-note">
              Review your delivery details before placing the order.
            </p>
          </aside>
        </div>
      )}
    </section>
  );
}

function AuthPage({
  mode,
  form,
  setForm,
  error,
  onSubmit,
  onSwitch,
  onHome,
}) {
  const isRegister = mode === "register";

  return (
    <section className="auth-layout">
      <div className="auth-story">
        <span className="eyebrow">WELCOME TO FOODLOOP</span>
        <h1>
          Good food.
          <br />
          <span>Good to have you.</span>
        </h1>
        <p>
          Find your favourites, keep your orders together, and make
          your next meal simple.
        </p>

        <img
          src="https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=1000&q=80"
          alt="Fresh food prepared for a meal"
        />
      </div>

      <div className="auth-card">
        <button className="back-link" onClick={onHome}>
          ← Back to home
        </button>

        <span className="eyebrow">
          {isRegister ? "LET'S GET STARTED" : "GOOD TO SEE YOU AGAIN"}
        </span>

        <h2>
          {isRegister ? "Create your account" : "Welcome back"}
          <span>.</span>
        </h2>

        <p>
          {isRegister
            ? "A few details and you're ready to go."
            : "Sign in to continue to FoodLoop."}
        </p>

        <form onSubmit={onSubmit} className="form-stack">
          {isRegister && (
            <label>
              Full name
              <input
                autoComplete="name"
                value={form.name}
                onChange={(e) =>
                  setForm({ ...form, name: e.target.value })
                }
                placeholder="Your name"
                required
              />
            </label>
          )}

          <label>
            Email address
            <input
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={(e) =>
                setForm({ ...form, email: e.target.value })
              }
              placeholder="you@example.com"
              required
            />
          </label>

          {isRegister && (
            <label>
              Phone number <span className="optional">(optional)</span>
              <input
                type="tel"
                autoComplete="tel"
                value={form.phone}
                onChange={(e) =>
                  setForm({ ...form, phone: e.target.value })
                }
                placeholder="Your phone number"
              />
            </label>
          )}

          <label>
            Password
            <input
              type="password"
              autoComplete={
                isRegister ? "new-password" : "current-password"
              }
              value={form.password}
              onChange={(e) =>
                setForm({ ...form, password: e.target.value })
              }
              placeholder="Enter your password"
              minLength="6"
              required
            />
          </label>

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          <button
            className="button button-dark full-width"
            type="submit"
          >
            {isRegister ? "Create account" : "Log in"} <span>→</span>
          </button>
        </form>

        <p className="auth-switch">
          {isRegister
            ? "Already have an account?"
            : "New to FoodLoop?"}{" "}
          <button onClick={onSwitch}>
            {isRegister ? "Log in" : "Create an account"}
          </button>
        </p>
      </div>
    </section>
  );
}

function CheckoutPage({
  form,
  setForm,
  error,
  cart,
  subtotal,
  deliveryFee,
  tax,
  total,
  onSubmit,
  onBack,
}) {
  const update = (key, value) =>
    setForm((old) => ({ ...old, [key]: value }));

  return (
    <section className="page-width inner-page">
      <div className="page-title-row">
        <div>
          <span className="eyebrow">ALMOST THERE</span>
          <h1>
            Checkout<span>.</span>
          </h1>
          <p>Where should we bring your order?</p>
        </div>

        <button className="text-button" onClick={onBack}>
          ← Back to cart
        </button>
      </div>

      {!cart.length ? (
        <div className="state-panel">
          <h3>Your cart is empty</h3>
          <p>Add a dish before checking out.</p>
          <button className="button button-dark" onClick={onBack}>
            Return to cart
          </button>
        </div>
      ) : (
        <form className="checkout-layout" onSubmit={onSubmit}>
          <div className="checkout-form-card">
            <span className="eyebrow">DELIVERY DETAILS</span>
            <h2>Where to?</h2>

            <div className="form-grid">
              <label>
                Full name
                <input
                  value={form.name}
                  onChange={(e) => update("name", e.target.value)}
                  autoComplete="name"
                  required
                  placeholder="Name for the order"
                />
              </label>

              <label>
                Phone number
                <input
                  type="tel"
                  value={form.phone}
                  onChange={(e) => update("phone", e.target.value)}
                  autoComplete="tel"
                  required
                  placeholder="Contact number"
                />
              </label>

              <label className="field-wide">
                Full delivery address
                <textarea
                  value={form.address}
                  onChange={(e) => update("address", e.target.value)}
                  autoComplete="street-address"
                  required
                  rows="3"
                  placeholder="House / flat, street, area"
                />
              </label>

              <label>
                City
                <input
                  value={form.city}
                  onChange={(e) => update("city", e.target.value)}
                  required
                  placeholder="City"
                />
              </label>

              <label>
                PIN code
                <input
                  value={form.pincode}
                  onChange={(e) => update("pincode", e.target.value)}
                  inputMode="numeric"
                  required
                  pattern="[0-9]{6}"
                  minLength="6"
                  maxLength="6"
                  placeholder="6-digit PIN code"
                />
              </label>

              <label className="field-wide">
                Delivery instructions{" "}
                <span className="optional">(optional)</span>
                <textarea
                  value={form.instructions}
                  onChange={(e) =>
                    update("instructions", e.target.value)
                  }
                  rows="2"
                  placeholder="Anything the delivery person should know?"
                />
              </label>
            </div>

            <div className="payment-choice">
              <span className="eyebrow">PAYMENT METHOD</span>

              <label className="payment-option">
                <input
                  type="radio"
                  name="payment"
                  value="Cash on Delivery"
                  checked={form.payment === "Cash on Delivery"}
                  onChange={(e) => update("payment", e.target.value)}
                />
                <span>
                  <strong>Cash on Delivery</strong>
                  <small>Pay when your order arrives</small>
                </span>
              </label>

              <label className="payment-option disabled-option">
                <input
                  type="radio"
                  name="payment"
                  value="Online payment (coming soon)"
                  checked={
                    form.payment === "Online payment (coming soon)"
                  }
                  onChange={(e) => update("payment", e.target.value)}
                />
                <span>
                  <strong>Online payment</strong>
                  <small>Coming soon</small>
                </span>
              </label>
            </div>

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <button
              className="button button-dark full-width"
              type="submit"
            >
              Place order <span>→</span>
            </button>

            <p className="demo-note">
              Your order will be saved to the backend. The backend
              currently stores the combined delivery address; payment
              method, phone number and delivery instructions are not
              saved as separate order fields.
            </p>
          </div>

          <aside className="checkout-card">
            <span className="eyebrow">YOUR ORDER</span>
            <h2>
              {cart.length} {cart.length === 1 ? "item" : "items"} selected
            </h2>

            <div className="checkout-mini-items">
              {cart.map((item) => (
                <div key={item.id}>
                  <span>
                    {item.quantity} × {item.name}
                  </span>
                  <strong>
                    {money(item.price * item.quantity)}
                  </strong>
                </div>
              ))}
            </div>

            <PriceSummary
              subtotal={subtotal}
              deliveryFee={deliveryFee}
              tax={tax}
              total={total}
            />
          </aside>
        </form>
      )}
    </section>
  );
}

function OrdersPage({ orders, onMenu, onSignIn, user }) {
  const [tracking, setTracking] = useState({});
  const [cancellingId, setCancellingId] = useState(null);
  const [cancelError, setCancelError] = useState("");
  const [confirmOrderId, setConfirmOrderId] = useState(null);

  async function loadTimeline(orderId) {
    try {
      const response = await axios.get(
        `${API}/orders/${orderId}/timeline`,
        authConfig()
      );

      console.log("Timeline API response:", response.data);

      setTracking((old) => ({
        ...old,
        [orderId]: response.data,
      }));
    } catch (error) {
      console.error("Could not load order timeline:", error);

      setCancelError(
        error.response?.data?.message ||
        "Could not load order tracking. Please try again."
      );
    }
  }

  async function cancelOrder() {
    if (confirmOrderId === null || cancellingId !== null) return;

    const orderId = confirmOrderId;
    setCancellingId(orderId);
    setCancelError("");

    try {
      await axios.patch(
        `${API}/orders/${orderId}/cancel`,
        {},
        authConfig()
      );

      window.location.reload();
    } catch (error) {
      console.error("Could not cancel order:", error);

      setCancelError(
        error.response?.data?.message ||
        error.response?.data?.error ||
        `Could not cancel order. HTTP ${error.response?.status || "Network Error"
        }`
      );

      setConfirmOrderId(null);
    } finally {
      setCancellingId(null);
    }
  }

  const stages = [
    "PLACED",
    "CONFIRMED",
    "PREPARING",
    "READY",
    "OUT_FOR_DELIVERY",
    "DELIVERED",
  ];

  return (
    <section className="page-width inner-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">YOUR FOODLOOP</span>
          <h1>My Orders</h1>
          <p>Track your orders and check their latest status.</p>
        </div>

        <button className="btn btn-primary" onClick={onMenu}>
          Order More
        </button>
      </div>

      {cancelError && (
        <div className="alert alert-error" role="alert">
          {cancelError}
        </div>
      )}

      {!user ? (
        <div className="empty-state">
          <h3>Sign in to view your orders</h3>
          <button className="btn btn-primary" onClick={onSignIn}>
            Sign In
          </button>
        </div>
      ) : !orders || orders.length === 0 ? (
        <div className="empty-state">
          <h3>No orders yet</h3>
          <p>Your orders will appear here after checkout.</p>
          <button className="btn btn-primary" onClick={onMenu}>
            Explore Food
          </button>
        </div>
      ) : (
        <div className="orders-list">
          {orders.map((order) => {
            const items = Array.isArray(order.items) ? order.items : [];
            const currentStage = stages.indexOf(order.status);
            const canCancel = ["PLACED", "CONFIRMED"].includes(
              order.status
            );

            return (
              <article className="order-card" key={order.id}>
                <div className="order-card-header">
                  <div>
                    <span className="eyebrow">ORDER #{order.id}</span>
                    <h3>
                      {order.status
                        ?.replaceAll("_", " ")
                        .toLowerCase()
                        .replace(/\b\w/g, (char) => char.toUpperCase())}
                    </h3>
                  </div>

                  <div className="order-total">
                    ₹
                    {Number(
                      order.totalAmount ?? order.total ?? 0
                    ).toFixed(2)}
                  </div>
                </div>

                <div className="order-items">
                  {items.map((item, index) => (
                    <div
                      className="order-item"
                      key={item.id ?? `${order.id}-${index}`}
                    >
                      <span>
                        {item.foodName ?? item.name ?? "Food item"} ×{" "}
                        {item.quantity ?? 1}
                      </span>

                      <span>
                        ₹
                        {Number(
                          item.subtotal ??
                          item.totalPrice ??
                          (item.price ?? 0) * (item.quantity ?? 1)
                        ).toFixed(2)}
                      </span>
                    </div>
                  ))}
                </div>

                <div className="order-timeline">
                  <h4>Order Progress</h4>

                  {order.status === "CANCELLED" ? (
                    <p>This order has been cancelled.</p>
                  ) : (
                    <div className="timeline-stages">
                      {stages.map((stage, index) => (
                        <div
                          className={`timeline-stage ${currentStage >= index ? "completed" : ""
                            } ${currentStage === index ? "current" : ""
                            }`}
                          key={stage}
                        >
                          <span className="timeline-dot" />
                          <span className="timeline-label">
                            {stage.replaceAll("_", " ")}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}

                  <button
                    className="btn btn-secondary"
                    onClick={() => loadTimeline(order.id)}
                  >
                    Track Order
                  </button>

                  {tracking[order.id] && (
                    <div className="tracking-details">
                      {Array.isArray(tracking[order.id]) &&
                        tracking[order.id].length > 0 ? (
                        tracking[order.id].map((event, index) => (
                          <div
                            className="tracking-event"
                            key={event.id ?? index}
                          >
                            <strong>
                              {(event.newStatus ??
                                event.previousStatus ??
                                "ORDER UPDATE"
                              ).replaceAll("_", " ")}
                            </strong>

                            {event.description && (
                              <p>{event.description}</p>
                            )}

                            {event.changedBy && (
                              <p>Updated by: {event.changedBy}</p>
                            )}

                            {event.changedAt && (
                              <small>
                                {new Date(
                                  event.changedAt
                                ).toLocaleString()}
                              </small>
                            )}
                          </div>
                        ))
                      ) : (
                        <p>No tracking updates available yet.</p>
                      )}
                    </div>
                  )}
                </div>

                {canCancel && (
                  <button
                    className="btn btn-danger"
                    onClick={() => {
                      setCancelError("");
                      setConfirmOrderId(order.id);
                    }}
                    disabled={cancellingId === order.id}
                  >
                    Cancel Order
                  </button>
                )}
              </article>
            );
          })}
        </div>
      )}

      {confirmOrderId !== null && (
        <div
          className="cancel-modal-overlay"
          onClick={(event) => {
            if (
              event.target === event.currentTarget &&
              cancellingId === null
            ) {
              setConfirmOrderId(null);
            }
          }}
        >
          <div
            className="cancel-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-modal-title"
          >
            <div className="cancel-modal-icon" aria-hidden="true">
              !
            </div>

            <h2 id="cancel-modal-title">Cancel this order?</h2>

            <p>
              Are you sure you want to cancel order #{confirmOrderId}?
              You may not be able to undo this action.
            </p>

            <div className="cancel-modal-actions">
              <button
                type="button"
                className="cancel-modal-keep"
                onClick={() => setConfirmOrderId(null)}
                disabled={cancellingId !== null}
              >
                No, Keep Order
              </button>

              <button
                type="button"
                className="cancel-modal-confirm"
                onClick={cancelOrder}
                disabled={cancellingId !== null}
              >
                {cancellingId !== null
                  ? "Cancelling..."
                  : "Yes, Cancel Order"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function ProfilePage({
  user,
  orders,
  onOrders,
  onSignOut,
  onEdit,
  onLogin,
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(user?.name || "");
  const [phone, setPhone] = useState(user?.phone || "");

  if (!user) {
    return (
      <section className="page-width inner-page">
        <div className="empty-cart">
          <h2>Your account, your favourites.</h2>
          <p>Log in or create an account to get started.</p>

          <button
            className="button button-dark"
            onClick={onLogin}
          >
            Log in
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="page-width inner-page">
      <div className="page-title-row">
        <div>
          <span className="eyebrow">YOUR SPACE</span>
          <h1>
            My account<span>.</span>
          </h1>
          <p>Manage your details and see your FoodLoop activity.</p>
        </div>
      </div>

      <div className="profile-layout">
        <div className="profile-card">
          <div className="profile-avatar">
            {user.name.trim().charAt(0).toUpperCase()}
          </div>

          <span className="eyebrow">FOODLOOP MEMBER</span>

          {editing ? (
            <form
              className="form-stack profile-edit"
              onSubmit={(e) => {
                e.preventDefault();

                onEdit({
                  ...user,
                  name: name.trim() || user.name,
                  phone: phone.trim(),
                });

                setEditing(false);
              }}
            >
              <label>
                Full name
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </label>

              <label>
                Phone number
                <input
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </label>

              <button
                className="button button-dark full-width"
                type="submit"
              >
                Save details
              </button>
            </form>
          ) : (
            <>
              <h2>{user.name}</h2>
              <p>{user.email}</p>
              <p>{user.phone || "No phone number added"}</p>

              <button
                className="button button-outline full-width"
                onClick={() => {
                  setName(user.name || "");
                  setPhone(user.phone || "");
                  setEditing(true);
                }}
              >
                Edit details
              </button>
            </>
          )}

          <button
            className="text-button sign-out-button"
            onClick={onSignOut}
          >
            Sign out
          </button>
        </div>

        <div className="profile-content">
          <div className="profile-stat">
            <span className="eyebrow">YOUR ORDERS</span>
            <strong>{orders.length}</strong>
            <p>Orders saved to your account</p>

            <button className="text-button" onClick={onOrders}>
              View order history <span>→</span>
            </button>
          </div>

          <div className="profile-help">
            <span className="eyebrow">A QUICK NOTE</span>
            <h3>Your FoodLoop account</h3>
            <p>
              Your cart and order history are loaded from the backend.
              Profile edits currently apply only in this browser.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

function DealsPage({ onMenu, onNotify }) {
  const [copied, setCopied] = useState("");

  return (
    <section className="page-width inner-page">
      <div className="page-title-row">
        <div>
          <span className="eyebrow">A LITTLE EXTRA GOODNESS</span>
          <h1>
            Deals for your cravings<span>.</span>
          </h1>
          <p>Choose an offer for your next food moment.</p>
        </div>
      </div>

      <div className="deal-grid">
        {DEALS.map((deal) => (
          <article className="deal-card" key={deal.id}>
            <div className="deal-image">
              <img
                src={deal.image}
                alt={deal.title}
                loading="lazy"
              />
              <span>{deal.discount}</span>
            </div>

            <div className="deal-content">
              <span className="eyebrow">FOODLOOP OFFER</span>
              <h2>{deal.title}</h2>
              <p>{deal.description}</p>

              <div className="deal-code">
                <span>
                  Code <strong>{deal.code}</strong>
                </span>

                <button
                  onClick={() => {
                    navigator.clipboard
                      ?.writeText(deal.code)
                      .catch(() => { });

                    setCopied(deal.id);
                    onNotify(`Offer code ${deal.code} selected`);
                  }}
                >
                  {copied === deal.id ? "Selected" : "Copy code"}
                </button>
              </div>

              <button
                className="button button-dark full-width"
                onClick={onMenu}
              >
                Browse menu <span>→</span>
              </button>
            </div>
          </article>
        ))}
      </div>

      <p className="demo-note deals-note">
        Offers are sample frontend content. Discount codes are not
        applied to the checkout total yet.
      </p>
    </section>
  );
}
function AdminDashboard() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("ACTIVE");
  const [updatingId, setUpdatingId] = useState(null);

  // Only allow transitions supported by the backend.
  const allowedTransitions = {
    PLACED: ["CONFIRMED", "CANCELLED"],
    CONFIRMED: ["PREPARING", "CANCELLED"],
    PREPARING: ["READY", "CANCELLED"],
    READY: ["OUT_FOR_DELIVERY"],
    OUT_FOR_DELIVERY: ["DELIVERED"],
    DELIVERED: [],
    CANCELLED: [],
  };

  const statuses = [
    "PLACED",
    "CONFIRMED",
    "PREPARING",
    "READY",
    "OUT_FOR_DELIVERY",
    "DELIVERED",
    "CANCELLED",
  ];

  const money = (amount) =>
    `₹${Number(amount || 0).toFixed(2)}`;

  const formatStatus = (status) =>
    String(status || "")
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(/\b\w/g, (char) => char.toUpperCase());

  const loadOrders = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await axios.get(
        `${API}/admin/orders`,
        authConfig()
      );

      console.log("Admin orders received:", response.data);

      setOrders(
        Array.isArray(response.data) ? response.data : []
      );
    } catch (err) {
      console.error("Failed to load admin orders:", err);

      if (err.response?.status === 401) {
        setError("Session expired. Please log in again.");
      } else if (err.response?.status === 403) {
        setError(
          "Access denied. Please log in using your ADMIN account."
        );
      } else {
        setError(
          err.response?.data?.message ||
          `Failed to load orders. HTTP ${err.response?.status || "Network Error"
          }`
        );
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
  }, []);

  const updateStatus = async (orderId, status) => {
    const order = orders.find(
      (item) => (item.id ?? item.orderId) === orderId
    );

    if (!order) {
      setError("Order not found. Refresh and try again.");
      return;
    }

    const permitted = allowedTransitions[order.status] || [];

    if (!permitted.includes(status)) {
      setError(
        `Cannot change ${formatStatus(order.status)} to ${formatStatus(
          status
        )}.`
      );
      return;
    }

    setUpdatingId(orderId);
    setError("");

    try {
      const response = await axios.patch(
        `${API}/admin/orders/${orderId}/status`,
        {
          status,
          note: `Status updated to ${status}`,
        },
        authConfig()
      );

      // Update the order locally immediately.
      // This prevents it from disappearing while the list refreshes.
      const updatedOrder = response.data;

      setOrders((previousOrders) =>
        previousOrders.map((item) =>
          (item.id ?? item.orderId) === orderId
            ? { ...item, ...updatedOrder }
            : item
        )
      );

      // Reload the authoritative list from the backend.
      await loadOrders();
    } catch (err) {
      console.error("Failed to update order:", err);

      setError(
        err.response?.data?.message ||
        `Could not update order. HTTP ${err.response?.status || "Network Error"
        }`
      );
    } finally {
      setUpdatingId(null);
    }
  };

  // Active Orders includes every order that has not reached a
  // terminal state. Changing PLACED to CONFIRMED won't hide it.
  const filteredOrders = orders.filter((order) => {
    if (filter === "ACTIVE") {
      return !["DELIVERED", "CANCELLED"].includes(order.status);
    }

    if (filter === "ALL") {
      return true;
    }

    return order.status === filter;
  });

  const activeCount = orders.filter(
    (order) =>
      !["DELIVERED", "CANCELLED"].includes(order.status)
  ).length;

  const placedCount = orders.filter(
    (order) => order.status === "PLACED"
  ).length;

  const inProgressCount = orders.filter((order) =>
    [
      "CONFIRMED",
      "PREPARING",
      "READY",
      "OUT_FOR_DELIVERY",
    ].includes(order.status)
  ).length;

  const deliveredCount = orders.filter(
    (order) => order.status === "DELIVERED"
  ).length;

  return (
    <section className="admin-dashboard">
      <div className="section-heading">
        <div>
          <p className="eyebrow">MANAGEMENT</p>
          <h1>Admin Dashboard</h1>
          <p>Manage customer orders and track their progress.</p>
        </div>

        <button
          type="button"
          className="btn-primary"
          onClick={loadOrders}
          disabled={loading}
        >
          {loading ? "Loading..." : "Refresh Orders"}
        </button>
      </div>

      {error && (
        <div role="alert">
          {error}
        </div>
      )}

      <div className="admin-summary">
        <div className="admin-stat">
          <span>Active Orders</span>
          <strong>{activeCount}</strong>
        </div>

        <div className="admin-stat">
          <span>Awaiting Confirmation</span>
          <strong>{placedCount}</strong>
        </div>

        <div className="admin-stat">
          <span>In Progress</span>
          <strong>{inProgressCount}</strong>
        </div>

        <div className="admin-stat">
          <span>Delivered</span>
          <strong>{deliveredCount}</strong>
        </div>
      </div>

      <div className="admin-filters">
        <label htmlFor="order-status-filter">
          Show orders:
        </label>

        <select
          id="order-status-filter"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
        >
          <option value="ACTIVE">Active Orders</option>
          <option value="ALL">All Orders</option>

          {statuses.map((status) => (
            <option key={status} value={status}>
              {formatStatus(status)}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="admin-empty">
          <h3>Loading orders...</h3>
          <p>Please wait while we retrieve your orders.</p>
        </div>
      ) : filteredOrders.length === 0 ? (
        <div className="admin-empty">
          <h3>No orders found</h3>
          <p>
            {filter === "ACTIVE"
              ? "There are no active orders right now."
              : "No orders match this filter."}
          </p>
        </div>
      ) : (
        <div className="admin-orders">
          {filteredOrders.map((order) => {
            const orderId = order.id ?? order.orderId;
            const permittedStatuses =
              allowedTransitions[order.status] || [];

            return (
              <article
                className="admin-order-card"
                key={orderId}
              >
                <div className="admin-order-header">
                  <div>
                    <h3>Order #{orderId}</h3>

                    <p>
                      {order.createdAt
                        ? new Date(
                          order.createdAt
                        ).toLocaleString()
                        : order.orderDate
                          ? new Date(
                            order.orderDate
                          ).toLocaleString()
                          : "Date unavailable"}
                    </p>
                  </div>

                  <span
                    className={`status-badge status-${String(
                      order.status || ""
                    ).toLowerCase()}`}
                  >
                    {formatStatus(order.status)}
                  </span>
                </div>

                <div className="admin-order-details">
                  <p>
                    <strong>Customer:</strong>{" "}
                    {order.customerName ||
                      order.userName ||
                      order.customer?.name ||
                      order.user?.name ||
                      "Customer"}
                  </p>

                  <p>
                    <strong>Email:</strong>{" "}
                    {order.customerEmail ||
                      order.userEmail ||
                      order.customer?.email ||
                      order.user?.email ||
                      "Not available"}
                  </p>

                  <p>
                    <strong>Total:</strong>{" "}
                    {money(
                      order.totalAmount ?? order.total
                    )}
                  </p>
                </div>

                {Array.isArray(order.items) &&
                  order.items.length > 0 && (
                    <div className="admin-order-items">
                      <strong>Items</strong>

                      {order.items.map((item, index) => (
                        <p key={item.id ?? index}>
                          {item.foodName ||
                            item.itemName ||
                            item.name ||
                            item.foodItem?.name ||
                            "Food item"}{" "}
                          × {item.quantity ?? 1}
                        </p>
                      ))}
                    </div>
                  )}

                <div className="admin-order-actions">
                  <label htmlFor={`status-${orderId}`}>
                    Update status:
                  </label>

                  <select
                    id={`status-${orderId}`}
                    value=""
                    disabled={
                      updatingId === orderId ||
                      permittedStatuses.length === 0
                    }
                    onChange={(event) => {
                      const nextStatus = event.target.value;

                      if (nextStatus) {
                        updateStatus(orderId, nextStatus);
                      }
                    }}
                  >
                    <option value="" disabled>
                      {updatingId === orderId
                        ? "Updating..."
                        : permittedStatuses.length > 0
                          ? "Choose next status"
                          : "No further updates"}
                    </option>

                    {permittedStatuses.map((status) => (
                      <option key={status} value={status}>
                        {formatStatus(status)}
                      </option>
                    ))}
                  </select>

                  {updatingId === orderId && (
                    <span>Updating...</span>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
export default App;