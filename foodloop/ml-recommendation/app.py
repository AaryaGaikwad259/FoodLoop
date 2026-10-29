
from flask import Flask, jsonify, request
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

app = Flask(__name__)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "running",
        "message": "FoodLoop ML API is ready"
    })


@app.route("/recommend-menu", methods=["POST"])
def recommend_menu():
    data = request.get_json(silent=True) or {}

    items = data.get("items", [])
    item_id = data.get("item_id")
    preference = str(data.get("preference", "")).strip()
    limit = data.get("limit", 5)

    if not isinstance(items, list) or not items:
        return jsonify({"error": "Provide a non-empty items list"}), 400

    try:
        limit = int(limit)
    except (TypeError, ValueError):
        return jsonify({"error": "limit must be an integer"}), 400

    if limit < 1 or limit > 20:
        return jsonify({"error": "limit must be between 1 and 20"}), 400

    # Only use available FoodLoop menu items with real database IDs.
    items = [
        item for item in items
        if isinstance(item, dict)
        and item.get("id") is not None
        and item.get("available", True) is True
    ]

    if not items:
        return jsonify({"recommendations": []})

    if item_id is None and not preference:
        return jsonify({
            "error": "Provide either item_id or preference"
        }), 400

    if item_id is not None and not any(
        str(item["id"]) == str(item_id) for item in items
    ):
        return jsonify({"error": "Food item ID not found"}), 404

    # Build searchable text from actual FoodLoop menu data.
    documents = [
        " ".join([
            str(item.get("name") or ""),
            str(item.get("description") or ""),
            str(item.get("categoryName") or "")
        ]).strip()
        for item in items
    ]

    vectorizer = TfidfVectorizer(stop_words="english")
    matrix = vectorizer.fit_transform(documents)

    if item_id is not None:
        selected_index = next(
            i for i, item in enumerate(items)
            if str(item["id"]) == str(item_id)
        )
        scores = cosine_similarity(
            matrix[selected_index], matrix
        ).flatten()
        # Exclude the selected item itself.
        scores[selected_index] = -1
    else:
        query_vector = vectorizer.transform([preference])
        scores = cosine_similarity(
            query_vector, matrix
        ).flatten()

    ranked_indices = sorted(
        range(len(items)),
        key=lambda i: float(scores[i]),
        reverse=True
    )

    recommendations = []

    for i in ranked_indices:
        if len(recommendations) >= limit:
            break

        # Don't recommend the same item as the selected item.
        if item_id is not None and str(items[i]["id"]) == str(item_id):
            continue

        result = dict(items[i])
        result["similarity_score"] = round(
            max(0.0, float(scores[i])), 3
        )
        recommendations.append(result)

    return jsonify({
        "recommendations": recommendations
    })


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5001, debug=False)
