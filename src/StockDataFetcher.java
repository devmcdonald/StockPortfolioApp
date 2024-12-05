import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StockDataFetcher {

    private static final String[] API_KEYS = {
        System.getenv("API_KEY_1"),
        System.getenv("API_KEY_2"),
        System.getenv("API_KEY_3"),
        System.getenv("API_KEY_4")
    };

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static int currentApiKeyIndex = 0;

    /**
     * Fetch stock data from the Alpha Vantage API.
     * Automatically switches to the next API key if the current one fails.
     * 
     * @param stockName The name of the stock to fetch data for (e.g., AAPL).
     * @return A JSONObject containing the stock data, or null if all keys are exhausted.
     */
    public static JSONObject fetchStockData(String stockName) {
        int attempts = 0;

        while (attempts < API_KEYS.length) {
            String apiKey = API_KEYS[currentApiKeyIndex];

            // Skip invalid or empty API keys
            if (apiKey == null || apiKey.trim().isEmpty()) {
                System.err.println("API key " + (currentApiKeyIndex + 1) + " is invalid or missing. Switching to the next key...");
                switchToNextApiKey();
                attempts++;
                continue;
            }

            try {
                // Create the API endpoint URL
                String endpoint = String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s", BASE_URL, stockName, apiKey);
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Get the response code
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }

                        String responseString = response.toString();
                        System.out.println("Response: " + responseString);  // Debugging line to see the full response

                        JSONObject jsonResponse = new JSONObject(responseString);

                        // Check for API exhaustion or rate limits
                        if (jsonResponse.has("Information") && jsonResponse.getString("Information").contains("rate limit")) {
                            System.err.println("API key " + (currentApiKeyIndex + 1) + " exhausted: " + jsonResponse.getString("Information"));
                            switchToNextApiKey();
                            attempts++;
                            continue;
                        }

                        // Check for other error messages
                        if (jsonResponse.has("Error Message")) {
                            System.err.println("Error for API key " + (currentApiKeyIndex + 1) + ": " + jsonResponse.getString("Error Message"));
                            return null; // Handle invalid stock symbol or other issues gracefully
                        }

                        // Check for valid "Time Series (Daily)" data
                        if (jsonResponse.has("Time Series (Daily)")) {
                            return jsonResponse;
                        } else {
                            System.err.println("Key 'Time Series (Daily)' not found in response for stock: " + stockName);
                            return null;
                        }
                    }
                } else {
                    System.err.println("HTTP Error: " + responseCode + " for API key " + (currentApiKeyIndex + 1));
                    switchToNextApiKey();
                    attempts++;
                }
            } catch (Exception e) {
                System.err.println("Error fetching stock data with API key " + (currentApiKeyIndex + 1) + ": " + e.getMessage());
                switchToNextApiKey();
                attempts++;
            }
        }

        System.err.println("All API keys exhausted. No data could be fetched.");
        return null;
    }



    /**
     * Switch to the next API key in the list.
     */
    private static void switchToNextApiKey() {
        currentApiKeyIndex = (currentApiKeyIndex + 1) % API_KEYS.length;
        System.out.println("Switched to API key " + (currentApiKeyIndex + 1));
    }


}