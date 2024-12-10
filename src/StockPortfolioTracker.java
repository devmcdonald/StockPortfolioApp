import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import org.json.JSONObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.chart.CategoryAxis;

public class StockPortfolioTracker extends Application {

    private LineChart<String, Number> stockTrendChart;
    private TableView<String[]> portfolioTable;
    private DatabaseManager dbManager;
    private ScheduledExecutorService scheduler;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Stock Market Portfolio Tracker");
        dbManager = new DatabaseManager();

        // Initialize UI components
        portfolioTable = setupPortfolioTable();
        stockTrendChart = setupStockTrendChart();
        VBox inputPane = setupStockInputPane();

        Button showPortfolioButton = new Button("Show Overall Portfolio");
        showPortfolioButton.setOnAction(event -> updateChartData("Portfolio", null));

        // Layouts
        VBox leftPane = new VBox(10, new Label("Your Portfolio"), portfolioTable, inputPane);
        leftPane.setPadding(new Insets(10));

        VBox rightPane = new VBox(10, new Label("Stock Trends"), stockTrendChart, showPortfolioButton);
        rightPane.setPadding(new Insets(10));
        rightPane.setAlignment(Pos.CENTER);

        HBox mainLayout = new HBox(10, leftPane, rightPane);
        mainLayout.setPadding(new Insets(10));

        // Scene setup
        Scene scene = new Scene(mainLayout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Load initial data and start updates
        loadPortfolioFromDatabase();
        startPriceUpdateScheduler();
    }

    private TableView<String[]> setupPortfolioTable() {
        TableView<String[]> table = new TableView<>();
        table.getColumns().addAll(
            createTableColumn("Stock", 0),
            createTableColumn("Shares", 1),
            createTableColumn("Buy Price", 2),
            createTableColumn("Current Price", 3),
            createTotalValueColumn()
        );
        table.setPrefWidth(600);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateChartData("Stock", newValue != null ? newValue[0] : null);
        });
        return table;
    }

    private TableColumn<String[], String> createTableColumn(String title, int index) {
        TableColumn<String[], String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue()[index])
        );
        return column;
    }

    private TableColumn<String[], String> createTotalValueColumn() {
        TableColumn<String[], String> column = new TableColumn<>("Total Value");
        column.setCellValueFactory(data -> {
            double shares = Double.parseDouble(data.getValue()[1]);
            double price = Double.parseDouble(data.getValue()[3]);
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", shares * price));
        });
        return column;
    }

    private LineChart<String, Number> setupStockTrendChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Days");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price");

        // Dynamically adjust Y-axis based on the data
        yAxis.setAutoRanging(false); // Disable auto-ranging to manually set bounds
        yAxis.setLowerBound(0); // Set a lower bound (or a dynamic value if needed)
        yAxis.setUpperBound(100); // Placeholder for the upper bound, which can be adjusted dynamically

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        return chart;
    }


    private VBox setupStockInputPane() {
        TextField stockNameInput = new TextField();
        stockNameInput.setPromptText("Stock Symbol");

        TextField sharesInput = new TextField();
        sharesInput.setPromptText("Shares");

        TextField priceInput = new TextField();
        priceInput.setPromptText("Buy Price");

        Button addStockButton = new Button("Add Stock");
        addStockButton.setOnAction(event -> addStock(
            stockNameInput.getText().toUpperCase(),
            sharesInput.getText(),
            priceInput.getText(),
            stockNameInput,
            sharesInput,
            priceInput
        ));

        VBox inputPane = new VBox(10, stockNameInput, sharesInput, priceInput, addStockButton);
        inputPane.setPadding(new Insets(10));
        inputPane.setAlignment(Pos.CENTER);
        return inputPane;
    }

    private void addStock(String stockName, String sharesText, String priceText, 
                          TextField... inputs) {
        try {
            int shares = Integer.parseInt(sharesText);
            double price = Double.parseDouble(priceText);
            portfolioTable.getItems().add(new String[]{
                stockName, String.valueOf(shares), 
                String.format("%.2f", price), "0.00", 
                String.format("%.2f", shares * price)
            });
            dbManager.addStock(stockName, shares, price);
            Arrays.stream(inputs).forEach(TextField::clear);
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for shares and price.");
        }
    }

    private void updateChartData(String type, String stockName) {
        stockTrendChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(type.equals("Stock") ? stockName : "Portfolio Value");

        try {
            if (type.equals("Stock")) {
                populateStockSeries(series, stockName);
            } else {
                populatePortfolioSeries(series);
            }
            stockTrendChart.getData().add(series);
        } catch (Exception e) {
            System.err.println("Error updating chart data: " + e.getMessage());
        }
    }

    private void populateStockSeries(XYChart.Series<String, Number> series, String stockName) throws Exception {
        JSONObject response = StockDataFetcher.fetchStockData(stockName);
        if (response != null) {
            JSONObject timeSeries = response.getJSONObject("Time Series (Daily)");
            double minValue = Double.MAX_VALUE;
            double maxValue = Double.MIN_VALUE;

            for (String date : timeSeries.keySet()) {
                double closePrice = timeSeries.getJSONObject(date).getDouble("4. close");
                series.getData().add(new XYChart.Data<>(date, closePrice));

                minValue = Math.min(minValue, closePrice);
                maxValue = Math.max(maxValue, closePrice);
            }

            // Adjust Y-axis bounds based on the data
            updateYAxisBounds(minValue, maxValue);
        }
    }

    private void populatePortfolioSeries(XYChart.Series<String, Number> series) throws Exception {
        Map<String, Double> portfolioData = new TreeMap<>();
        double minValue = Double.MAX_VALUE;
        double maxValue = Double.MIN_VALUE;

        for (String[] stock : portfolioTable.getItems()) {
            String stockName = stock[0];
            double shares = Double.parseDouble(stock[1]);
            JSONObject response = StockDataFetcher.fetchStockData(stockName);
            if (response != null) {
                JSONObject timeSeries = response.getJSONObject("Time Series (Daily)");
                for (String date : timeSeries.keySet()) {
                    double closePrice = timeSeries.getJSONObject(date).getDouble("4. close");
                    portfolioData.put(date, portfolioData.getOrDefault(date, 0.0) + shares * closePrice);

                    minValue = Math.min(minValue, closePrice);
                    maxValue = Math.max(maxValue, closePrice);
                }
            }
        }

        portfolioData.forEach((date, value) -> series.getData().add(new XYChart.Data<>(date, value)));

        // Adjust Y-axis bounds based on the data
        updateYAxisBounds(minValue, maxValue);
    }
    
 // Method to update Y-axis bounds dynamically
    private void updateYAxisBounds(double minValue, double maxValue) {
        Platform.runLater(() -> {
            NumberAxis yAxis = (NumberAxis) stockTrendChart.getYAxis();

            double range = maxValue - minValue;
            double padding = range * 0.1; // Add 10% padding
            double lowerBound = Math.max(0, minValue - padding); // Ensure non-negative lower bound
            double upperBound = maxValue + padding;

            // Dynamically calculate the tick unit
            double tickUnit = calculateDynamicTickUnit(range);

            // Update Y-Axis properties
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(lowerBound);
            yAxis.setUpperBound(upperBound);
            yAxis.setTickUnit(tickUnit);

            // Limit minor tick marks for performance
            yAxis.setMinorTickCount(4); // Reasonable number of minor ticks
        });
    }


    private double calculateDynamicTickUnit(double range) {
        // Define a maximum number of major ticks
        int maxMajorTicks = 10;
        return Math.ceil(range / maxMajorTicks);
    }






    private void loadPortfolioFromDatabase() {
        try (ResultSet rs = dbManager.getPortfolio()) {
            while (rs.next()) {
                String stockName = rs.getString("stock_name");
                int shares = rs.getInt("shares");
                double price = rs.getDouble("price");
                portfolioTable.getItems().add(new String[]{
                    stockName, String.valueOf(shares), 
                    String.format("%.2f", price), "0.00", "0.00"
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void startPriceUpdateScheduler() {
        scheduler = Executors.newScheduledThreadPool(portfolioTable.getItems().size());
        portfolioTable.getItems().forEach(stock -> {
            String stockName = stock[0];
            scheduler.scheduleAtFixedRate(() -> updateStockPrice(stockName), 0, 1, TimeUnit.DAYS);
        });
    }

    private void updateStockPrice(String stockName) {
        try {
            JSONObject response = StockDataFetcher.fetchStockData(stockName);
            if (response != null) {
                String latestDate = response.getJSONObject("Time Series (Daily)").keys().next();
                double currentPrice = response.getJSONObject("Time Series (Daily)").getJSONObject(latestDate).getDouble("4. close");
                Platform.runLater(() -> portfolioTable.getItems().stream()
                    .filter(stock -> stock[0].equals(stockName))
                    .forEach(stock -> {
                        stock[3] = String.format("%.2f", currentPrice);
                        stock[4] = String.format("%.2f", currentPrice * Double.parseDouble(stock[1]));
                        portfolioTable.refresh();
                    })
                );
            }
        } catch (Exception e) {
            System.err.println("Error updating stock price: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        if (scheduler != null) scheduler.shutdown();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
