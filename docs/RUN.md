# How to Run

This guide shows two easy ways to run Icewheel Energy with Docker. The recommended approach for development is Docker
Compose. For production, you can run the application as a standalone container with your own managed PostgreSQL
database.

## With Docker Compose (Recommended for Development)

This method runs the application with a persistent H2 file-based database, which is simple and requires no external
database setup.

1. **Build the Docker image:**

   ```sh
   ./mvnw spring-boot:build-image
   ```

2. **Create a data and configuration directory:**

   ```sh
   mkdir data
   ```

3. **Create the configuration file:**

   Copy the provided template to `data/application.yml` and fill in your secrets (e.g., Google/Tesla client IDs). You
   can remove the `datasource` and `jpa` sections from the template, as they will be handled by the `h2` profile
   activated in `docker-compose.yml`.

   ```sh
   cp application.yaml.template data/application.yml
   nano data/application.yml
   ```

4. **Launch the application:**

   ```sh
   docker-compose up
   ```

   Your application will be available at http://localhost:8081. The H2 database file (`tesladb.mv.db`) will be stored in
   the `data` directory on your host machine.

## As a Standalone Container (for Production)

For production, it is recommended to use an external configuration file to manage your secrets and settings.

1. **Create a data directory:**

   ```sh
   mkdir /data
   ```

2. **Create the configuration file:**

       Copy `src/main/resources/application.yml` to `/data/application.yml` and fill in the values for your production environment.

   ```sh
       cp src/main/resources/application.yml /data/application.yml
       nano /data/application.yml
   ```

3. **Build the Docker image (if you haven't already):**

   ```sh
   ./mvnw spring-boot:build-image
   ```

4. **Run the container:**

   Run the container, mounting the `/data` directory and specifying the location of the external configuration file.

   ```sh
   docker run -p 8081:8081 \
     -v /data:/config \
     -e SPRING_CONFIG_LOCATION=/config/application.yml \
     iceservicesenergy:0.0.1-SNAPSHOT
   ```

This setup provides a simple, secure, and flexible way to run and distribute your application.
