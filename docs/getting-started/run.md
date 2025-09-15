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
   all the configuration options, see the [Configuration Guide](./configuration.md).

4. **Run with Docker Compose**

   Once the configuration is complete, you can start the application using Docker Compose:

   ```bash
   docker-compose up
   ```

   The application will be accessible at `http://localhost:8081`.

## Connecting to Tesla from a Local Network

If you are running the IceWheel Energy application on a local network (e.g., a home PC, Raspberry Pi, or other personal devices not directly accessible from the public internet), you must complete an extra step to connect your Tesla account.

Tesla's API requires a public key to be hosted on a publicly accessible domain to validate ownership and secure communications. The application generates this key for you, but you need to make it available online.

1.  **Find Your Public Key**

    Once the application is running, you can find the public key by navigating to the developer information page at:
    `http://localhost:8081/developer/application-info`

2.  **Host the Public Key**

    You must host this public key at the following path on a public domain:
    `/.well-known/appspecific/com.tesla.3p.public-key.pem`

    You have a couple of options to do this:

    *   **Easy Method (Recommended):** Use the free Google Cloud Run Function we provide. It's a simple, free-tier solution to host your key. Follow the instructions at:
        [icewheel-energy-key-beacon-function on GitHub](https://github.com/icewheel-oss/icewheel-energy-key-beacon-function)

    *   **Advanced Method:** Host the public key with your preferred hosting provider. You can follow Tesla's official developer documentation for instructions on how to generate and host the key yourself.

After successfully hosting your public key, you can proceed with configuring the OAuth flow to connect your Tesla account.

## Running without Docker

If you prefer to run the application without Docker, you can do so using Maven.

1. **Build the Application**

   ```bash
   ./mvnw clean install
   ```

2. **Set Environment Variables**

   The application requires several environment variables to be set. These are the same variables found in the
   `application.yml` file. You can either set them in your shell or create a script to do so.

   See the [Configuration Guide](./configuration.md) for a complete list of required variables.

3. **Run the Application**

   ```bash
   java -jar target/icewheel-energy-0.0.1-SNAPSHOT.jar
   ```
