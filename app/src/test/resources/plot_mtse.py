import sys
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.dates import DayLocator, DateFormatter

def plot_mtsd_from_long_csv(file_path):
    # --- Read data using pandas, which is perfect for this format ---
    try:
        # The ISO 8601 format with 'Z' is automatically handled by pandas
        df = pd.read_csv(file_path, parse_dates=['time'])
    except Exception as e:
        print(f"Error reading or parsing CSV: {e}")
        return

    # Create the plot
    plt.figure(figsize=(18, 10))

    # --- Plot each series by grouping the data ---
    for series_name, group in df.groupby('series_name'):
        # Sort values by time to ensure lines are drawn correctly
        group = group.sort_values(by='time')

        plt.plot(group['time'], group['value'], marker='o', markersize=2, linestyle='-', label=series_name)

    # --- Formatting the plot ---
    plt.xlabel("Time")
    plt.ylabel("Value")
    plt.title("Multi-Time-Series Data")
    plt.legend(loc='upper left', bbox_to_anchor=(1, 1)) # Move legend outside plot

    # Set major ticks to represent days
    plt.gca().xaxis.set_major_locator(DayLocator())
    # Format the tick labels to be readable dates
    plt.gca().xaxis.set_major_formatter(DateFormatter('%Y-%m-%d %H:%M'))

    # Rotate date labels for better readability
    plt.gcf().autofmt_xdate()

    plt.grid(True, which='both', linestyle='--', linewidth=0.5)
    plt.tight_layout(rect=[0, 0, 0.85, 1]) # Adjust layout to make space for legend
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) == 2:
        plot_mtsd_from_long_csv(sys.argv[1])
    else:
        print("Usage: python script.py data.csv")

