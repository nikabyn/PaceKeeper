import csv
import sys
import matplotlib.pyplot as plt
from datetime import datetime, timedelta

def read_discrete_csv(file_path):
    series = {}
    with open(file_path, 'r') as f:
        reader = csv.reader(f)
        headers = next(reader)
        for header in headers:
            series[header] = []
        for row in reader:
            for i, value in enumerate(row):
                series[headers[i]].append(value)
    return headers, series

def read_irregular_csv(file_path):
    timestamps = []
    percentages = []
    with open(file_path, 'r') as f:
        reader = csv.reader(f)
        headers = next(reader)  # timestamp,percentage,validation
        for row in reader:
            if not row:
                continue
            ts = datetime.fromisoformat(row[0].replace("Z", "+00:00"))
            perc = float(row[2].replace("%", "")) / 100.0
            timestamps.append(ts)
            percentages.append(perc)
    return timestamps, percentages

def plot_mtsd_from_csv(discrete_path, irregular_path=None):
    # --- Read discrete data ---
    headers, series = read_discrete_csv(discrete_path)

    index_col_name = headers[0]
    indices = [int(i) for i in series[index_col_name]]

    # --- Read irregular data if provided ---
    irregular_timestamps = None
    irregular_percentages = None
    start_time = None

    if irregular_path is not None:
        irregular_timestamps, irregular_percentages = read_irregular_csv(irregular_path)
        if irregular_timestamps:
            start_time = min(irregular_timestamps)

    # --- Build time axis for discrete data ---
    if start_time is not None:
        # Each index step = 10 minutes
        discrete_times = [
            start_time + timedelta(minutes=10 * idx) + timedelta(days=5)# + timedelta(hours=6)
            for idx in indices
        ]
        x_discrete = discrete_times
        x_label = "Time"
    else:
        x_discrete = indices
        x_label = "Index"

    plt.figure(figsize=(15, 10))

    # --- Plot discrete series ---
    for series_name in headers[1:]:
        values = []
        plot_x = []
        for x_val, value in zip(x_discrete, series[series_name]):
            if value:
                val = float(value)
                if series_name == "HEART_RATE":
                    val = val#val / 100.0 + 0.4
                plot_x.append(x_val)
                values.append(val)

        if values:
            plt.plot(plot_x, values, marker='o', markersize=3, linestyle='-', label=series_name)

    # --- Plot irregular series ---
    if irregular_timestamps is not None:
        plt.plot(
            irregular_timestamps,
            irregular_percentages,
            marker='x',
            markersize=4,
            linestyle='--',
            color='red',
            label='percentage (irregular)'
        )

    plt.xlabel(x_label)
    plt.ylabel("Value")
    plt.title("Multi-Time-Series Discrete Data with Irregular Series")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) == 2:
        plot_mtsd_from_csv(sys.argv[1])
    elif len(sys.argv) == 3:
        plot_mtsd_from_csv(sys.argv[1], sys.argv[2])
    else:
        print("Usage:")
        print("  python script.py discrete.csv")
        print("  python script.py discrete.csv irregular.csv")