
import json
from pathlib import Path

# Locate the dataset
file_path = Path(__file__).parent / "data" / "train.json"

# Load the JSON dataset
with open(file_path, "r", encoding="utf-8") as file:
    data = json.load(file)

print("Dataset loaded successfully!")

if isinstance(data, list):
    print("Total records:", len(data))

    if data:
        print("\nAvailable columns:")
        print(list(data[0].keys()))

        print("\nFirst 3 records:")
        for record in data[:3]:
            print(record)

elif isinstance(data, dict):
    print("Dataset contains a JSON object.")
    print("Available keys:", list(data.keys()))
