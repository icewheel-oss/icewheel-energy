# IceWheel Energy

IceWheel Energy is a self-hosted application that gives you full control over your Tesla Powerwall charging schedule. By
running your own instance, you can manage your Powerwall's behavior without relying on third-party services, ensuring
your energy data remains private.

This application allows you to create sophisticated schedules to optimize your Powerwall's charging and discharging
cycles based on your electricity tariff (e.g., on-peak and off-peak hours).

**For full documentation, please see the [docs](./docs/index.md) directory.**

## Quick Start

The quickest way to get started is with Docker Compose.

1. **Build the application image:**
   ```bash
   ./mvnw spring-boot:build-image
   ```

2. **Create a data directory:**
   ```bash
   mkdir ./data
   ```

3. **Configure the application:**
   Copy the `src/main/resources/application.yml` file to `./data/application.yml` and fill in your secrets for Google
   and Tesla APIs. Detailed instructions are in the [configuration guide](./docs/getting-started/configuration.md).

4. **Run the application:**
   ```bash
   docker-compose up
   ```

The application will be available at `http://localhost:8081`.

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](./LICENSE) file for details.
