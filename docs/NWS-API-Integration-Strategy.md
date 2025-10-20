### NWS API Integration and Data Persistence Strategy (v2)

This report provides a comprehensive strategy for integrating the National Weather Service (NWS) API with the IceWheel Energy application. It addresses the challenges of the current implementation, recommends a more robust client architecture using Spring HATEOAS, and details an efficient data persistence model that includes storing raw API responses and versioning forecasts over time.

#### 1. NWS API Analysis

The NWS API is a modern, RESTful service with several key characteristics that influence the integration strategy:

*   **HATEOAS-based Navigation:** The API uses HATEOAS (Hypermedia as the Engine of Application State). To get a forecast, you must perform two steps:
    1.  **Request Point Metadata:** First, a `GET` request is made to the `/points/{latitude},{longitude}` endpoint.
    2.  **Follow Hypermedia Links:** The response from the points endpoint does not contain the weather data itself, but rather a `properties` object with hypermedia links. The client must extract the URL from the `forecastHourly` field and make a second request to that URL to get the actual forecast.
*   **Rich Data Formats:** The API uses standard, well-defined formats. Responses are primarily `application/geo+json` or `application/ld+json`, which embed geographic and linked data context directly into the JSON.
*   **Authentication:** All API requests must include a `User-Agent` header for identification.

#### 2. Recommended API Integration Strategy

The manual handling of the API's HATEOAS flow by casting the response to a `java.util.Map` is brittle. To build a resilient client, I strongly recommend adopting **Spring HATEOAS**.

##### The Spring HATEOAS Solution

Spring HATEOAS is designed specifically for this scenario. It allows your client to understand hypermedia and navigate API responses programmatically. Instead of manually digging through a `Map`, you can simply ask for a link by its *relation type* (its name, like "forecastHourly").

##### Recommended Client: `RestClient` with Virtual Threads

Per your requirement, using `RestClient` with Virtual Threads is an excellent choice. It provides high-concurrency I/O for a familiar imperative coding style. To make this work with HATEOAS, the `RestClient` bean must be configured with a HATEOAS-aware `RestTemplate` under the hood.

#### 3. Data Persistence Strategy (Revised for Versioning)

The requirement to compare how a forecast for a specific hour evolves necessitates a more sophisticated data model that captures **forecast snapshots**. Each time we fetch data from the NWS, we will store it as a new, distinct "report".

##### New Database Schema

**1. `zip_code_cache` Table (Existing)**
*This existing entity is used to translate a ZIP code into the latitude/longitude needed to query the NWS API and our internal `locations` table.*

**2. `locations` Table**
*This table maps a lat/lon to the NWS grid information, acting as a cache to avoid redundant calls to the `/points` endpoint.*

| Column | Data Type | Description |
| :--- | :--- | :--- |
| `id` | `BIGINT` (PK) | Unique identifier for the location. |
| `latitude` | `DECIMAL(10, 6)` | Latitude of the location. |
| `longitude` | `DECIMAL(10, 6)` | Longitude of the location. |
| `grid_id` | `VARCHAR(255)` | The forecast grid ID (e.g., "PHI"). |
| `grid_x` | `INTEGER` | The forecast grid X coordinate. |
| `grid_y` | `INTEGER` | The forecast grid Y coordinate. |

**3. `forecast_reports` Table (New)**
*This is the new parent table. Each row represents a single, complete forecast fetched from the API at a specific moment in time.*

| Column | Data Type | Description |
| :--- | :--- | :--- |
| `id` | `BIGINT` (PK) | Unique ID for this specific forecast report. |
| `location_id` | `BIGINT` (FK to `locations`) | The location this forecast is for. |
| `fetched_at` | `TIMESTAMP WITH TIME ZONE` | **Crucially**, the timestamp when this forecast was fetched from the API. |
| `raw_response` | `JSONB` or `TEXT` | The complete raw JSON for the entire multi-hour forecast. |

**4. `hourly_predictions` Table (Revised)**
*This table now stores individual hourly data points, linking back to a specific `forecast_report`.*

| Column | Data Type | Description |
| :--- | :--- | :--- |
| `id` | `BIGINT` (PK) | Unique ID for this hourly prediction. |
| `report_id` | `BIGINT` (FK to `forecast_reports`) | Links this prediction to a specific fetch. |
| `start_time` | `TIMESTAMP WITH TIME ZONE` | The time the forecast period *starts* (e.g., 1 PM). |
| `end_time` | `TIMESTAMP WITH TIME ZONE` | The time the forecast period *ends* (e.g., 2 PM). |
| `temperature` | `SMALLINT` | The predicted temperature for that hour. |
| ... | ... | *Other normalized fields as before...* |

##### How This Schema Addresses Your Needs

*   **Querying for a Specific Hour:** To get the forecast for `2025-08-26 13:00:00`, you would query `hourly_predictions` where `start_time` matches. This will return multiple rows, one for each time a forecast was fetched for that specific hour.
*   **Comparing Forecast Changes:** By joining `hourly_predictions` to `forecast_reports`, you can order the results by `fetched_at`. This allows you to see the full history of predictions for a single hour and analyze how it changed.
*   **Getting the Most Recent Forecast:** To get the single latest prediction for a given hour, you simply take the top result after ordering by `fetched_at` in descending order.
*   **ZIP Code Queries:** The application service layer will use the `zip_code_cache` to translate a ZIP code to a lat/lon, find the corresponding `location_id`, and then use that ID to query the reports and predictions tables.
