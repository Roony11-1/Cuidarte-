package io.github.roony11_1.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection 
{
    private static final String URL = "jdbc:postgresql://localhost:15442/cuidarteplus";

    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    private DatabaseConnection()
    {

    }

    public static Connection getConexion() throws SQLException
    {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
