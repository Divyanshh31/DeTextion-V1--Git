package com.detextion.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class GraphConnector {
    private static Driver driver;

    public static void connect() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"));
        System.out.println("Connected to Neo4j.");
    }

    public static void close() {
        if (driver != null) driver.close();
    }
}

