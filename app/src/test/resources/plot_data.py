import sys
import pandas as pd
import matplotlib.pyplot as plt

def plot_data(series_path, extrapolation_path=None):
    """
    Reads time series data and optional extrapolation data from CSV files and plots them.
    """
    try:
        series_data = pd.read_csv(series_path)
    except FileNotFoundError:
        print(f"Error: The series data file was not found at {series_path}")
        return
    except Exception as e:
        print(f"An error occurred while reading {series_path}: {e}")
        return

    if 'index' not in series_data.columns or 'value' not in series_data.columns:
        print("Error: Series CSV must contain 'index' and 'value' columns.")
        return

    plt.figure(figsize=(18, 10))
    # Plot the main time series
    plt.plot(series_data['index'], series_data['value'], marker='.', linestyle='-', markersize=4, label='Time Series')

    # Handle extrapolation data if provided
    if extrapolation_path:
        try:
            extrapolation_data = pd.read_csv(extrapolation_path)
            
            # The x-coordinates for extrapolation points are offsets from the end of the time series.
            # Convert them to absolute indices for plotting.
            series_last_index = series_data['index'].max()
            extrapolation_data['x1_abs'] = series_last_index - extrapolation_data['x1']
            extrapolation_data['x2_abs'] = series_last_index - extrapolation_data['x2']

            # Plot each extrapolation line
            for i, row in extrapolation_data.iterrows():
                res_x = row['x_res']
                res_y = row['y_res']

                # Plot the two points used for the trend
                plt.plot([row['x1_abs'], row['x2_abs']], [row['y1'], row['y2']], 'o', 
                         markersize=6, label=f"Points for {row['name']}")
                
                # Plot the extrapolation line itself
                plt.plot([row['x2_abs'], res_x], [row['y2'], res_y], '--', 
                         linewidth=1.5, label=f"Trend for {row['name']}")

                # Plot the final extrapolated point
                plt.plot(res_x, res_y, '*', markersize=12, 
                         label=f"Result for {row['name']}")

        except FileNotFoundError:
            print(f"Warning: The extrapolation file was not found at {extrapolation_path}")
        except Exception as e:
            print(f"An error occurred while processing {extrapolation_path}: {e}")

    plt.title('Time Series Data and Extrapolations')
    plt.xlabel('Time Step (Index)')
    plt.ylabel('Value')
    plt.grid(True)
    # Using a legend that doesn't overlap too much
    plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout(rect=[0, 0, 0.85, 1]) # Adjust layout to make room for legend
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python plot_data.py <path_to_series_csv> [path_to_extrapolation_csv]")
    else:
        series_file = sys.argv[1]
        extrapolation_file = sys.argv[2] if len(sys.argv) > 2 else None
        print(f"Plotting series from: {series_file}")
        if extrapolation_file:
            print(f"Adding extrapolations from: {extrapolation_file}")
        plot_data(series_file, extrapolation_file)