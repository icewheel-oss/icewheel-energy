# Connecting to the Database for Troubleshooting

This document outlines the process used to connect to the PostgreSQL database running in a Docker container to resolve a `PSQLException` without exposing the database port to the host machine.

## The Problem

A `PSQLException` occurred during application startup, indicating a schema mismatch in the `weather_api_cache` table. Hibernate, the JPA provider, was unable to automatically alter the `raw_payload` column from its previous type to the new `oid` type required for Large Object (LOB) storage.

The error message was:
```
ERROR: column "raw_payload" cannot be cast automatically to type oid
Hint: You might need to specify "USING raw_payload::oid".]
```

This typically happens in a development environment when an entity definition changes and the existing data in the table is incompatible with the new column type.

## The Solution

For a development environment, the most straightforward solution is to drop the problematic table and allow Hibernate to recreate it with the correct schema on the next application startup. The data in the `weather_api_cache` table is transient and can be safely discarded.

### Steps to Connect and Fix

1.  **Identify the Database Service:** First, I inspected the `docker-compose.yml` file to find the name of the PostgreSQL service, which was `icewheel-db`.

2.  **Ensure the Service is Running:** I started the database service independently to ensure it was available:
    ```bash
    docker-compose up -d icewheel-db
    ```

3.  **Connect and Execute SQL without Opening Ports:** Instead of exposing the database port (5432) in the `docker-compose.yml` file, which can be a security risk, I used `docker-compose exec`. This command allows you to run commands directly inside a running container.

    I executed the `psql` command-line client inside the `icewheel-db` container to drop the table:
    ```bash
    docker-compose exec icewheel-db psql -U postgres -d icewheel-energy -c "DROP TABLE IF EXISTS weather_api_cache;"
    ```

    -   `docker-compose exec icewheel-db`: Executes a command in the `icewheel-db` service container.
    -   `psql`: The PostgreSQL command-line client.
    -   `-U postgres`: Specifies the user to connect as. I found the correct username (`postgres`) in the `.env.example` file.
    -   `-d icewheel-energy`: Specifies the database name to connect to, also found in `.env.example`.
    -   `-c "..."`: Executes the specified SQL command and then exits.

This approach is secure and efficient for development and troubleshooting, as it avoids modifying the container's network configuration.

4.  **Rebuild the Application:** After dropping the table, a full Maven build ensures that the application starts with a clean, correct schema:
    ```bash
    ./mvnw clean install
    ```

## Production Considerations

In a production environment, dropping tables is not a viable solution. A proper database migration tool like [Flyway](https://flywaydb.org/) or [Liquibase](https://www.liquibase.org/) should be used to manage schema changes in a controlled and versioned manner. These tools can handle complex data migrations and ensure that no data is lost during schema evolution.
