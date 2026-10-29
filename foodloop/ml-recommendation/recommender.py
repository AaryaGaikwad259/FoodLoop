
import joblib
import pandas as pd
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

BASE_DIR = Path(__file__).parent
DATA_FILE = BASE_DIR / "data" / "processed_foods.csv"
MODEL_DIR = BASE_DIR / "models"

MODEL_DIR.mkdir(exist_ok=True)

# Load processed data
df = pd.read_csv(DATA_FILE).fillna("")

# Combine ingredients and cuisine into recommendation features
df["features"] = (
    df["cuisine"].astype(str) + " " +
    df["ingredients"].astype(str).str.replace(",", " ", regex=False)
)

# Convert food features into numerical vectors
vectorizer = TfidfVectorizer(stop_words="english")
feature_matrix = vectorizer.fit_transform(df["features"])

# Calculate similarity between recipes
similarity_matrix = cosine_similarity(feature_matrix)

# Save the trained model and its data
joblib.dump(
    {
        "data": df,
        "vectorizer": vectorizer,
        "similarity": similarity_matrix,
    },
    MODEL_DIR / "food_recommender.joblib",
)

print("Recommendation model trained successfully!")
print("Recipes used:", len(df))
print("Cuisine categories:", df["cuisine"].nunique())
print("Model saved in:", MODEL_DIR / "food_recommender.joblib")


def recommend_similar(recipe_id, top_n=5):
    matches = df.index[df["id"].astype(str) == str(recipe_id)].tolist()

    if not matches:
        print("Recipe ID not found in the sample.")
        return

    index = matches[0]
    scores = similarity_matrix[index]

    # Exclude the selected recipe itself
    ranked_indices = scores.argsort()[::-1]
    ranked_indices = [
        i for i in ranked_indices if i != index
    ][:top_n]

    results = df.iloc[ranked_indices].copy()
    results["similarity_score"] = [
        round(float(scores[i]), 3) for i in ranked_indices
    ]

    print("\nRecommended similar recipes:")
    print(
        results[["id", "cuisine", "ingredients", "similarity_score"]]
        .to_string(index=False)
    )


if __name__ == "__main__":
    # Use the first recipe in the sample as a test
    recommend_similar(df.iloc[0]["id"])
