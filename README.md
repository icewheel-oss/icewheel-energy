# IceWheel Energy

IceWheel Energy is a self-hosted application that gives you full control over your Tesla Powerwall charging schedule. By
running your own instance, you can manage your Powerwall's behavior without relying on third-party services, ensuring
your energy data remains private.

This application allows you to create sophisticated schedules to optimize your Powerwall's charging and discharging
cycles based on your electricity tariff (e.g., on-peak and off-peak hours).

**For full documentation, please see the [docs](./docs/index.md) directory.**

## Quick Start

The quickest way to get started is with Docker Compose.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/icewheel/icewheel-energy.git
   cd icewheel-energy
   ```

2. **Create your environment file:**
   Copy the provided example file to `.env`.
   ```bash
   cp .env.example .env
   ```

3. **Configure your environment:**
   Open the `.env` file in a text editor and fill in the required secrets for Google and Tesla APIs. Detailed instructions for obtaining these credentials are in the [configuration guide](./docs/getting-started/configuration.md).

   **Note:** When setting up your Tesla and Google developer accounts, you can skip adding a payment method. The application uses free-tier APIs, so your account will still work without payment information. However, please be aware that both Google and Tesla may change their API pricing and policies at any time, and the developers of this application have no control over these changes.

4. **Run the application:**
   ```bash
   docker-compose up -d
   ```

The application will be available at `http://localhost:8081` (or the port you specified in your `.env` file).

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](./LICENSE) file for details.