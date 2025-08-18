# Running the Application

This guide provides detailed instructions on how to run the IceWheel Energy application.

## Prerequisites

* **Java 24 or higher**: The application is built with Java 24.
* **Maven**: Required for building the application from source.
* **Docker and Docker Compose**: The recommended way to run the application.

## Running with Docker (Recommended)

Using Docker is the easiest and recommended way to run the application.

1. **Build the Docker Image**

   From the root of the project, run the following command to build the application and create a Docker image:

   ```bash
   ./mvnw spring-boot:build-image
   ```

2. **Create a Data Directory**

   Create a directory to store the application's data, including the H2 database file and the configuration file.

   ```bash
   mkdir ./data
   ```

3. **Configure the Application**

   Copy the `application.yml` file from `src/main/resources/application.yml` to the newly created `data` directory.

   ```bash
   cp src/main/resources/application.yml data/application.yml
   ```

   Now, edit `data/application.yml` and fill in the required secrets for Google and Tesla. For a detailed explanation of
   all the configuration options, see the [Configuration Guide](./docs/getting-started/configuration.md).

4. **Run with Docker Compose**

   Once the configuration is complete, you can start the application using Docker Compose:

   ```bash
   docker-compose up
   ```

   The application will be accessible at `http://localhost:8081`.

## Running without Docker

If you prefer to run the application without Docker, you can do so using Maven.

1. **Build the Application**

   ```bash
   ./mvnw clean install
   ```

2. **Set Environment Variables**

   The application requires several environment variables to be set. These are the same variables found in the
   `application.yml` file. You can either set them in your shell or create a script to do so.

   See the [Configuration Guide](./docs/getting-started/configuration.md) for a complete list of required variables.

3. **Run the Application**

   ```bash
   java -jar target/icewheel-energy-0.0.1-SNAPSHOT.jar
   ```
