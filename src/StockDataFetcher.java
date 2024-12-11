import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StockDataFetcher {

    private static final String API_KEY = System.getenv("ALPHA_VANTAGE_KEY"); 
    private static final String BASE_URL = "https://www.alphavantage.co/query";

    /**
     * Fetch stock data from the Alpha Vantage API.
     * @param stockName The name of the stock to fetch data for (e.g., AAPL).
     * @return A JSONObject containing the stock data.
     */
    public static JSONObject fetchStockData(String stockName) {
        try {
            // Create the API endpoint URL
            String endpoint = String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s", BASE_URL, stockName, API_KEY);
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Get the response code
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                return new JSONObject(response.toString());
            } else {
                System.err.println("HTTP Error: " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("Error fetching stock data: " + e.getMessage());
        }
        return null;
    }
}