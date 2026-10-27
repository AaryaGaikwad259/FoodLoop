import json
import urllib.request
import urllib.error
from getpass import getpass

BASE_URL = "http://localhost:8080/api"

# Direct image URLs grouped by food category.
# These are fixed image URLs, not random-image services.
CATEGORY_IMAGES = {
    1: "https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=800&q=80",
    2: "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=800&q=80",
    3: "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80",
    4: "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?auto=format&fit=crop&w=800&q=80",
    5: "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?auto=format&fit=crop&w=800&q=80",
    6: "https://images.unsplash.com/photo-1546833999-b9f581a1996d?auto=format&fit=crop&w=800&q=80",
    7: "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=800&q=80",
    8: "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=800&q=80",
    9: "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=800&q=80",
    10: "https://images.unsplash.com/photo-1544145945-f90425340c7e?auto=format&fit=crop&w=800&q=80",
}


def request_json(url, method="GET", token=None, body=None):
    headers = {"Accept": "application/json"}

    if token:
        headers["Authorization"] = f"Bearer {token}"

    data = None

    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(
        url,
        data=data,
        headers=headers,
        method=method
    )

    with urllib.request.urlopen(req, timeout=20) as response:
        content = response.read().decode("utf-8")
        return json.loads(content) if content else None


def image_url_works(url):
    try:
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "Mozilla/5.0"},
            method="GET"
        )

        with urllib.request.urlopen(req, timeout=15) as response:
            content_type = response.headers.get("Content-Type", "")

            # Read a small part to ensure the response isn't empty.
            first_bytes = response.read(32)

            return (
                response.status == 200
                and "image/" in content_type.lower()
                and len(first_bytes) > 0
            )

    except Exception:
        return False


def main():
    print("FoodLoop image URL updater")
    print()

    token = input("Paste your fresh admin JWT token: ").strip()

    if not token:
        print("No token entered. Exiting.")
        return

    try:
        foods = request_json(
            f"{BASE_URL}/foods",
            token=token
        )
    except Exception as error:
        print("Could not fetch food items:", error)
        return

    # Exclude Margherita Pizza (ID 1), whose existing image URL worked.
    targets = [food for food in foods if food["id"] != 1]

    print(f"\nFood items found: {len(foods)}")
    print(f"Items to update: {len(targets)}")

    confirm = input(
        "\nType UPDATE to replace the image URLs: "
    ).strip()

    if confirm != "UPDATE":
        print("Cancelled.")
        return

    success = 0
    skipped = 0
    failed = 0

    for food in targets:
        food_id = food["id"]
        name = food["name"]
        category_id = food["categoryId"]

        image_url = CATEGORY_IMAGES.get(category_id)

        if not image_url:
            print(f"SKIPPED: {name} - no image configured")
            skipped += 1
            continue

        # Check the image URL before modifying the database.
        if not image_url_works(image_url):
            print(f"SKIPPED: {name} - image URL did not load")
            skipped += 1
            continue

        # Preserve all existing food details.
        body = {
            "name": food["name"],
            "description": food.get("description"),
            "price": food["price"],
            "imageUrl": image_url,
            "available": food["available"],
            "prepTimeMinutes": food["prepTimeMinutes"],
            "categoryId": category_id
        }

        try:
            request_json(
                f"{BASE_URL}/foods/{food_id}",
                method="PUT",
                token=token,
                body=body
            )

            print(f"SUCCESS: {name}")
            success += 1

        except Exception as error:
            print(f"FAILED: {name} - {error}")
            failed += 1

    print("\n========== SUMMARY ==========")
    print(f"Successfully updated: {success}")
    print(f"Skipped: {skipped}")
    print(f"Failed: {failed}")
    print("=============================")


if __name__ == "__main__":
    main()