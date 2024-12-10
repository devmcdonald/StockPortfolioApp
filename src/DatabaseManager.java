import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DATABASE_URL = "jdbc:sqlite:portfolio.db";

    public DatabaseManager() {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            // Create the portfolio table if it doesn't exist
            String createTableQuery = """
                    CREATE TABLE IF NOT EXISTS portfolio (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        stock_name TEXT NOT NULL,
                        shares INTEGER NOT NULL,
                        price REAL NOT NULL
                    )
                    """;
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTableQuery);
            }
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    // add new stock
    public void addStock(String stockName, int shares, double price) {
        String insertQuery = "INSERT INTO portfolio (stock_name, shares, price) VALUES (?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
            preparedStatement.setString(1, stockName);
            preparedStatement.setInt(2, shares);
            preparedStatement.setDouble(3, price);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding stock to portfolio: " + e.getMessage());
        }
    }

    // fetch portfolio from database
    public ResultSet getPortfolio() {
        String selectQuery = "SELECT stock_name, shares, price FROM portfolio";
        try {
            Connection connection = DriverManager.getConnection(DATABASE_URL);
            Statement statement = connection.createStatement();
            return statement.executeQuery(selectQuery);
        } catch (SQLException e) {
            System.err.println("Error fetching portfolio: " + e.getMessage());
            return null;
        }
    }

}