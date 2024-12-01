import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.json.JSONObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

public class StockPortfolioTracker extends Application {

    private LineChart<Number, Number> stockTrendChart;
    private TableView<String[]> portfolioTable;
    private DatabaseManager dbManager;
    private ScheduledExecutorService scheduler;
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Stock Market Portfolio Tracker");

        dbManager = new DatabaseManager();

        // Portfolio Table
        portfolioTable = new TableView<>();
        TableColumn<String[], String> stockNameCol = new TableColumn<>("Stock");
        stockNameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[0]));

        TableColumn<String[], String> sharesCol = new TableColumn<>("Shares");
        sharesCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[1]));

        TableColumn<String[], String> priceCol = new TableColumn<>("Buy Price");
        priceCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[2]));
        
        TableColumn<String[], String> curPriceCol = new TableColumn<>("Current Price");
        curPriceCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[3]));

     // Corrected: Total Value Calculation (using totValCol)
        TableColumn<String[], String> totValCol = new TableColumn<>("Total Value");
        totValCol.setCellValueFactory(data -> {
            double shares = Double.parseDouble(data.getValue()[1]);
            double price = Double.parseDouble(data.getValue()[2]);
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", shares * price));
        });

        portfolioTable.getColumns().addAll(stockNameCol, sharesCol, priceCol, curPriceCol, totValCol);
        portfolioTable.setPrefWidth(600);
        portfolioTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                String selectedStockName = newValue[0]; // Stock name is in the first column
                updateStockTrendChartWithLiveData(selectedStockName);
            }
        });

        // Stock Trend Chart
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Days");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price");
        stockTrendChart = new LineChart<>(xAxis, yAxis);
        stockTrendChart.setTitle("Stock Performance");

        // Layouts
        VBox leftPane = new VBox(10, new Label("Your Portfolio"), portfolioTable);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(400);

        VBox rightPane = new VBox(10, new Label("Stock Trends"), stockTrendChart);
        rightPane.setPadding(new Insets(10));
        rightPane.setPrefWidth(400);

        HBox mainLayout = new HBox(10, leftPane, rightPane);
        mainLayout.setPadding(new Insets(10));

        // Add stock input fields and button
        TextField stockNameInput = new TextField();
        stockNameInput.setPromptText("Stock Symbol");
        TextField sharesInput = new TextField();
        sharesInput.setPromptText("Shares");
        TextField priceInput = new TextField();
        priceInput.setPromptText("Price");

        Button addStockButton = new Button("Add Stock");
        addStockButton.setOnAction(e -> {
            String stockName = stockNameInput.getText().toUpperCase();
            String sharesText = sharesInput.getText();
            String priceText = priceInput.getText();
            if (!stockName.isEmpty() && !sharesText.isEmpty() && !priceText.isEmpty()) {
                try {
                	int shares = Integer.parseInt(sharesText);
                    double price = Double.parseDouble(priceText);
                    addStockToPortfolio(stockName,shares, price);
                    
                    stockNameInput.clear();
                    sharesInput.clear();
                    priceInput.clear();
                } catch (NumberFormatException ex) {
                    showError("Please enter valid numbers for shares and price.");
                }
            } else {
                showError("All fields are required.");
            }
           
        });

        VBox inputPane = new VBox(10, stockNameInput, sharesInput, priceInput, addStockButton);
        inputPane.setPadding(new Insets(10));
        leftPane.getChildren().add(inputPane);

        // Main Scene
        Scene scene = new Scene(mainLayout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Populate Portfolio Table from Database
        loadPortfolioFromDatabase();
        
     // Start the scheduler to update stock prices
        startPriceUpdateScheduler();
    }
    private void startPriceUpdateScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> updateCurrentPrices());
        }, 0, 1, TimeUnit.DAYS); // Update every day
    }

    @Override
    public void stop() throws Exception {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        super.stop();
    }
    private void updateCurrentPrices() {
        for (String[] stock : portfolioTable.getItems()) {
            String stockName = stock[0];
            try {
                // Fetch live stock price using StockDataFetcher
                JSONObject response = StockDataFetcher.fetchStockData(stockName);
                if (response != null) {
                    JSONObject timeSeries = response.getJSONObject("Time Series (Daily)");
                    String latestDate = timeSeries.keys().next();
                    double currentPrice = timeSeries.getJSONObject(latestDate).getDouble("4. close");

                    // Update "Current Price" in stock data array
                    stock[3] = String.format("%.2f", currentPrice);

                    // Update "Total Value" based on live price
                    double shares = Double.parseDouble(stock[1]);
                    stock[4] = String.format("%.2f", shares * currentPrice);
                } else {
                    System.out.println("Unable to fetch stock data for " + stockName);
                }
            } catch (Exception e) {
                System.err.println("Error updating price for " + stockName + ": " + e.getMessage());
            }
        }

        // Refresh table to reflect updates
        portfolioTable.refresh();
    }
    
    private double fetchSpecificPrice(String stockName) {
    	double curPrice = 0.0;
            try {
                // Fetch live stock price using StockDataFetcher
                JSONObject response = StockDataFetcher.fetchStockData(stockName);
                if (response != null) {
                    JSONObject timeSeries = response.getJSONObject("Time Series (Daily)");
                    String latestDate = timeSeries.keys().next();
                    curPrice = timeSeries.getJSONObject(latestDate).getDouble("4. close");

                } else {
                    System.out.println("Unable to fetch stock data for " + stockName);
                }
            } catch (Exception e) {
                System.err.println("Error updating price for " + stockName + ": " + e.getMessage());
            }
   
        // Refresh table to reflect updates
        portfolioTable.refresh();
        
        return curPrice;
    }

    private void addStockToPortfolio(String stockName, int shares, double price) {
        portfolioTable.getItems().add(new String[]{
            stockName, 
            String.valueOf(shares), 
            String.format("%.2f", price), 
            "0.00", // Placeholder for Current Price
            String.format("%.2f", shares * price) // Placeholder for Total Value
        });
        dbManager.addStock(stockName, shares, price);  // Persist to database
    }

    private void loadPortfolioFromDatabase() {
        try {
            ResultSet rs = dbManager.getPortfolio();
            while (rs != null && rs.next()) {
                String stockName = rs.getString("stock_name");
                int shares = rs.getInt("shares");
                double price = rs.getDouble("price");
                double curPrice = fetchSpecificPrice(stockName);
                double totVal = curPrice * shares;
                portfolioTable.getItems().add(new String[]{stockName, String.valueOf(shares), String.format("%.2f", price), String.format("%.2f", curPrice), String.format("%.2f", totVal)});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    /**
     * Fetch live stock data and update the graph.
     */
    private void updateStockTrendChartWithLiveData(String stockName) {
        stockTrendChart.getData().clear(); // Clear old data

        try {
            // Fetch stock data using the StockDataFetcher class
            JSONObject response = StockDataFetcher.fetchStockData(stockName);

            if (response != null) {
                JSONObject timeSeries = response.getJSONObject("Time Series (Daily)");
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(stockName);

                int day = 1;
                for (String date : timeSeries.keySet()) {
                    double closePrice = timeSeries.getJSONObject(date).getDouble("4. close");
                    series.getData().add(new XYChart.Data<>(day++, closePrice));
                    if (day > 5) break; // Limit to last 5 days
                }

                stockTrendChart.getData().add(series);
            } else {
                System.out.println("Error: Unable to fetch stock data.");

            }
        } catch (Exception e) {
            System.err.println("Error updating stock trend chart: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
