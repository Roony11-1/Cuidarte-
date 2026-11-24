package io.github.roony11_1.db;

import io.github.roony11_1.config.ConfigReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection 
{
    private static final ConfigReader CONFIG = ConfigReader.getInstance();

    private static final String URL = String.format(
        "jdbc:postgresql://%s:%s/%s",
        CONFIG.db("host"),
        CONFIG.db("port"),
        CONFIG.db("name")
    );

    private static final String USER     = CONFIG.db("user");
    private static final String PASSWORD = CONFIG.db("password");

    private DatabaseConnection() {}

    public static Connection getConexion() throws SQLException 
    {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}