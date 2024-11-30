import java.sql.*;

public class DatabaseManager {
    private static final String DATABASE_URL = "jdbc:sqlite:portfolio.db";

    // Constructor to initialize the database and create tables if they don't exist
    public DatabaseManager() {
        createTables();
    }

    // Connect to the database
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // Create portfolio and stock_history tables if they don't exist
    private void createTables() {
        String createPortfolioTable = """
                CREATE TABLE IF NOT EXISTS portfolio (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stock_name TEXT NOT NULL,
                    shares INTEGER NOT NULL,
                    price REAL NOT NULL
                );
                """;

        String createHistoryTable = """
                CREATE TABLE IF NOT EXISTS stock_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stock_name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    closing_price REAL NOT NULL
                );
                """;

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createPortfolioTable);
            stmt.execute(createHistoryTable);
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
        }
    }

    // Retrieve all stocks from portfolio
    public ResultSet getPortfolio() {
        String sql = "SELECT * FROM portfolio";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            return stmt.executeQuery(sql);
        } catch (SQLException e) {
            System.err.println("Error retrieving portfolio: " + e.getMessage());
        }
        return null;
    }

    // Add stock to portfolio (including shares and price)
    public void addStock(String symbol, int shares, double price) {
        String sql = "INSERT INTO portfolio (stock_name, shares, price) VALUES (?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
        	stmt.setString(1, symbol);
            stmt.setInt(2, shares);  
            stmt.setDouble(3, price);  
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add historical price for a stock
    public void addHistoricalPrice(String stockName, String date, double closingPrice) {
        String insertQuery = "INSERT INTO stock_history (stock_name, date, closing_price) VALUES (?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
            pstmt.setString(1, stockName);
            pstmt.setString(2, date);
            pstmt.setDouble(3, closingPrice);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding historical price: " + e.getMessage());
        }
    }

    // Remove stock from portfolio
    public void removeStock(String stockName) {
        String sql = "DELETE FROM portfolio WHERE stock_name = ?";
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stockName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error removing stock from portfolio: " + e.getMessage());
        }
    }

    // Retrieve last 5 days of prices for a stock
    public ResultSet getLast5DaysPrices(String stockName) {
        String selectQuery = """
                SELECT date, closing_price
                FROM stock_history
                WHERE stock_name = ?
                ORDER BY date DESC
                LIMIT 5;
                """;
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(selectQuery)) {
            pstmt.setString(1, stockName);
            return pstmt.executeQuery();
        } catch (SQLException e) {
            System.err.println("Error retrieving historical prices: " + e.getMessage());
        }
        return null;
    }
}
