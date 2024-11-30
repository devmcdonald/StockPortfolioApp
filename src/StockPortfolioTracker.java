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

public class StockPortfolioTracker extends Application {

    private LineChart<Number, Number> stockTrendChart;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Stock Market Portfolio Tracker");

        // Portfolio Table
        TableView<String[]> portfolioTable = new TableView<>();
        TableColumn<String[], String> stockNameCol = new TableColumn<>("Stock");
        stockNameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[0]));

        TableColumn<String[], String> sharesCol = new TableColumn<>("Shares");
        sharesCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[1]));

        TableColumn<String[], String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()[2]));
        
        // Corrected: Total Value Calculation (using totValCol)
        TableColumn<String[], String> totValCol = new TableColumn<>("Total Value");
        totValCol.setCellValueFactory(data -> {
            double shares = Double.parseDouble(data.getValue()[1]);
            double price = Double.parseDouble(data.getValue()[2]);
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", shares * price));
        });

        portfolioTable.getColumns().addAll(stockNameCol, sharesCol, priceCol, totValCol);
        portfolioTable.setPrefWidth(600);  // Adjust table width if needed

        // Stock Trend Chart
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Days");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price");
        stockTrendChart = new LineChart<>(xAxis, yAxis);
        stockTrendChart.setTitle("Stock Performance");

        // Update Chart on Stock Selection
        portfolioTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String stockName = newSelection[0];
                updateStockTrendChartWithLiveData(stockName); // Fetch live data
            }
        });

        // Layouts
        VBox leftPane = new VBox(10, new Label("Your Portfolio"), portfolioTable);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(400);

        VBox rightPane = new VBox(10, new Label("Stock Trends"), stockTrendChart);
        rightPane.setPadding(new Insets(10));
        rightPane.setPrefWidth(400);

        HBox mainLayout = new HBox(10, leftPane, rightPane);
        mainLayout.setPadding(new Insets(10));

        // Main Scene
        Scene scene = new Scene(mainLayout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Populate Portfolio Table (Mock Data)
        portfolioTable.getItems().addAll(
            new String[]{"AAPL", "10", "150.00"},
            new String[]{"GOOGL", "5", "2800.00"}
        );
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