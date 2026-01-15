import json
import sys
import matplotlib.pyplot as plt
from datetime import datetime
import numpy as np

def plot_mtse(file_path):
    with open(file_path, 'r') as f:
        data = json.load(f)

    plt.figure(figsize=(15, 10))

    for series_name, series_data in data.items():
        if not series_data:
            continue

        timestamps = [datetime.fromisoformat(point['time']) for point in series_data]
        values = np.array([float(point['value']) for point in series_data])

        # --- idiomatic z-normalization ---
        z_values = (values - values.mean()) / values.std() if values.std() != 0 else np.zeros_like(values)

        plt.plot(timestamps, z_values, marker='o', linestyle='-', label=series_name)

    plt.xlabel("Time")
    plt.ylabel("Z‑Normalized Value")
    plt.title("Z‑Normalized Multi-Time-Series Data")
    plt.legend()
    plt.grid(True)
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) > 1:
        plot_mtse(sys.argv[1])
    else:
        print("No data file provided.")
