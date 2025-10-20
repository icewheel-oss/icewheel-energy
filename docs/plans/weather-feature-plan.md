# Weather-Aware Scheduling Feature: Phased Implementation Plan

This document outlines the phased implementation plan for the new weather-aware scheduling feature.

## Phase 1: Backend Foundation

**Goal:** Create the backend foundation for managing user-specific weather provider settings and auditing.

**Checklist:**
*   [x] **Entities:**
    *   [x] `WeatherProviderCredential.java`: Stores user's API keys for weather providers.
    *   [x] `WeatherProviderType.java`: Enum for supported weather providers.
    *   [x] `WeatherApiCache.java`: Stores raw API responses from weather providers.
    *   [x] `WeatherAuditEvent.java`: Logs weather-related decisions and actions.
    *   [x] `WeatherAuditEventType.java`: Enum for audit event types.
*   [x] **Repositories:**
    *   [x] `WeatherProviderCredentialRepository.java`
    *   [x] `WeatherApiCacheRepository.java`
    *   [x] `WeatherAuditEventRepository.java`
*   [x] **Encryption:**
    *   [x] Add `jasypt-spring-boot-starter` dependency to `pom.xml`.
    *   [x] Configure Jasypt in `application.yml`.
    *   [x] Annotate the `apiKey` and `apiSecret` fields in `WeatherProviderCredential`.
*   [x] **API (`WeatherProviderController.java`):**
    *   [x] `GET /api/weather/providers`
    *   [x] `POST /api/user/weather-provider-credentials`
    *   [x] `GET /api/user/weather-provider-credentials`
    *   [x] `DELETE /api/user/weather-provider-credentials/{id}`
*   [ ] **UI (Placeholder `weather-settings.html`):** (Skipped for now)

---

## Phase 2: Weather Data Fetching and Caching (Parallel Work)

**Goal:** Implement the logic for fetching weather data from external providers and caching it.

**Prerequisites:** Phase 1 (Entities and Repositories) must be complete.

**Checklist:**
*   [x] **Abstraction:**
    *   [x] `WeatherProvider.java` interface.
    
    *   [x] `WeatherProviderFactory.java`.
*   [x] **NWS Provider:**
    *   [x] Create `NWSProvider.java` implementation of `WeatherProvider`.
    *   [x] This will involve:
        *   [x] Reading the `nws-openapi.json` file to understand the API.
        *   [x] Implementing the logic to first get the gridpoint from a lat/lon, and then get the hourly forecast for that gridpoint.
        *   [x] Normalizing the NWS API response into a `NormalizedForecast`.
*   [x] **Service (`WeatherService.java`):**
    *   [x] Injects `WeatherProviderFactory` and `ForecastCacheService`.
    *   [x] `getForecast(userId, siteId)` method that:
        *   [x] Checks the cache first.
        *   [x] If not in cache, gets the user's enabled provider, fetches the data, and caches it.
*   [x] **Caching (`ForecastCacheService.java`):**
    *   [x] `get(userId, siteId)` and `put(userId, siteId, forecast)` methods.
    *   [x] Uses the `WeatherApiCacheRepository`.

---

## Phase 3: Weather-Aware Scheduling Logic (Parallel Work)

**Goal:** Implement the core scheduling logic that uses the weather data.

**Prerequisites:** Phase 2 must be complete.

**Checklist:**
*   [x] **Domain (`Schedule.java`):**
    *   [x] Add `ScheduleType` enum with `WEATHER_AWARE`.
    *   [x] Add weather-related fields to the `Schedule` entity (e.g., `cloudCoverThreshold`).
*   [x] **Scheduler (`WeatherAwareScheduler.java`):**
    *   [x] A new `@Scheduled` task.
    *   [x] Fetches `WEATHER_AWARE` schedules.
    *   [x] Calls `WeatherService` to get the forecast.
    *   [x] Calls `WeatherForecastEvaluator` to make a decision.
    *   [x] If "bad weather", creates a temporary "forced charge" schedule.
*   [x] **Evaluator (`WeatherForecastEvaluator.java`):**
    *   [x] An interface and a default implementation.
    *   [x] Contains the logic to decide if the weather is "good" or "bad".
*   [x] **Auditing:**
    *   [x] Log events to `WeatherAuditEventRepository` from the scheduler and evaluator.

---

## Phase 4: UI/UX and Refinements (Parallel Work)

**Goal:** Build the user interface for managing weather settings and viewing forecasts.

**Prerequisites:** Phase 1 and Phase 3 (API endpoints) must be complete.

**Checklist:**
*   [x] **UI (`weather-settings.html`):**
    *   [x] A user-friendly form to manage weather provider credentials.
*   [x] **UI (Schedule Form):**
    *   [x] Add options to create/edit `WEATHER_AWARE` schedules.
*   [x] **UI (Dashboard):**
    *   [x] Display the weather forecast.
    *   [ ] Show an indicator for weather-based forced charging.
*   [ ] **Refinements:**
    *   [ ] Provider chain logic for failover.
    *   [ ] Rate limiting.