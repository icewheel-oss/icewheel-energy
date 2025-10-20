# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Weather-Aware Scheduling:** The scheduler can now use weather forecasts to make decisions about when to charge and discharge the Powerwall.
- **Weather Provider Framework:** A new framework for integrating with different weather providers has been added. This makes it easy to add new weather providers in the future.
- **NWS Provider:** An implementation for the National Weather Service (NWS) API has been added.
- **User Preferences:** Users can now configure their weather provider and other preferences.
- New REST and web controllers for weather and user preferences.
- New integration tests for the NWS provider.

### Changed

- The `UserService` has been refactored and moved to a new package.
- The `TeslaUserService` has been renamed and moved.
- The database schema has been updated to support the new features.
- The UI has been updated to display weather information and user preferences.

### Removed

- The old `UserService` and `UserServiceImpl` have been removed.
- The `WeatherApiCache` and `WeatherApiCacheRepository` have been removed in favor of a more robust caching solution.
