
import json
import re
from pathlib import Path

import pandas as pd

BASE_DIR = Path(__file__).parent
INPUT_FILE = BASE_DIR / "data" / "train.json"
OUTPUT_FILE = BASE_DIR / "data" / "processed_foods.csv"

# Load dataset
with open(INPUT_FILE, "r", encoding="utf-8") as file:
    recipes = json.load(file)

df = pd.DataFrame(recipes)

print("Original records:", len(df))

# Remove missing values and duplicate recipe IDs
df = df.dropna(subset=["id", "cuisine", "ingredients"])
df = df.drop_duplicates(subset=["id"])

# Clean cuisine names
df["cuisine"] = (
    df["cuisine"]
    .astype(str)
    .str.strip()
    .str.lower()
)

# Clean and standardize ingredient lists
def clean_ingredients(ingredients):
    if not isinstance(ingredients, list):
        return []

    cleaned = []
    for ingredient in ingredients:
        if isinstance(ingredient, str):
            ingredient = ingredient.strip().lower()
            ingredient = re.sub(r"\s+", " ", ingredient)

            if ingredient:
                cleaned.append(ingredient)

    # Remove repeated ingredients while preserving order
    return list(dict.fromkeys(cleaned))

df["ingredients"] = df["ingredients"].apply(clean_ingredients)

# Remove recipes with no usable ingredients
df = df[df["ingredients"].map(len) > 0].copy()

# Convert ingredient lists into text for ML
df["ingredient_text"] = df["ingredients"].apply(
    lambda items: " ".join(items)
)

# Select a reproducible sample of up to 600 records
sample_size = min(600, len(df))
sample = df.sample(n=sample_size, random_state=42).copy()

# Store ingredients as readable text in the CSV
sample["ingredients"] = sample["ingredients"].apply(
    lambda items: ", ".join(items)
)

sample.to_csv(OUTPUT_FILE, index=False, encoding="utf-8")

print("\nData processing completed!")
print("Clean records available:", len(df))
print("Sample records saved:", len(sample))
print("Cuisine categories:", sample["cuisine"].nunique())
print("Output file:", OUTPUT_FILE)
print("\nFirst 5 processed records:")
print(sample[["id", "cuisine", "ingredients"]].head().to_string(index=False))
