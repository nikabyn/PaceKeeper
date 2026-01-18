import csv
import sys
import matplotlib.pyplot as plt

def plot_mtsd_from_csv(file_path):
    series = {}
    with open(file_path, 'r') as f:
        reader = csv.reader(f)
        headers = next(reader)
        for header in headers:
            series[header] = []
        for row in reader:
            for i, value in enumerate(row):
                series[headers[i]].append(value)

    plt.figure(figsize=(15, 10))

    index_col_name = headers[0]
    indices = [int(i) for i in series[index_col_name]]

    for series_name in headers[1:]:
        values = []
        plot_indices = []
        # Using zip to pair indices with values and filter those with empty value strings
        for index, value in zip(indices, series[series_name]):
            if value:
                plot_indices.append(index)
                values.append(float(value))

        if values:
            plt.plot(plot_indices, values, marker='o', markersize=3, linestyle='-', label=series_name)

    plt.xlabel("Index")
    plt.ylabel("Value")
    plt.title("Multi-Time-Series Discrete Data")
    plt.legend()
    plt.grid(True)
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) > 1:
        file_path = sys.argv[1]
        plot_mtsd_from_csv(file_path)
    else:
        print("No data file provided.")
