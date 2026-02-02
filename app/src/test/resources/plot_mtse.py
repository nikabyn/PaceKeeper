import sys
import os
import glob
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.dates import DayLocator, DateFormatter

def plot_mtsd_from_directory(dir_path):
    # Find all CSV files in the directory
    csv_files = glob.glob(os.path.join(dir_path, "*.csv"))

    if not csv_files:
        print(f"No CSV files found in {dir_path}")
        return

    plt.figure(figsize=(18, 10))

    # Iterate through each file and plot it independently
    for file_path in csv_files:
        try:
            # Use filename as series name (e.g. "HEART_RATE.csv" -> "HEART_RATE")
            series_name = os.path.splitext(os.path.basename(file_path))[0]

            # Read and sort
            df = pd.read_csv(file_path, parse_dates=['time'])
            if df.empty:
                continue
            df = df.sort_values(by='time')

            # Plot
            plt.plot(df['time'], df['value'], marker='o', markersize=2, linestyle='-', label=series_name)

        except Exception as e:
            print(f"Error processing {file_path}: {e}")

    # --- Formatting ---
    plt.xlabel("Time")
    plt.ylabel("Value")
    plt.title("Multi-Time-Series Data (Separate Files)")
    plt.legend(loc='upper left', bbox_to_anchor=(1, 1))

    # X-Axis formatting
    ax = plt.gca()
    ax.xaxis.set_major_locator(DayLocator())
    ax.xaxis.set_major_formatter(DateFormatter('%Y-%m-%d %H:%M'))
    plt.gcf().autofmt_xdate()

    plt.grid(True, which='both', linestyle='--', linewidth=0.5)
    plt.tight_layout(rect=[0, 0, 0.85, 1])
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) == 2:
        # sys.argv[1] is now the directory path
        plot_mtsd_from_directory(sys.argv[1])
    else:
        print("Usage: python script.py <data_directory_path>")