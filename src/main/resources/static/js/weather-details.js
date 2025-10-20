document.addEventListener('DOMContentLoaded', () => {
    function escapeHTML(str) {
        const p = document.createElement('p');
        p.appendChild(document.createTextNode(str));
        return p.innerHTML;
    }

    const zipCodeSelectionForm = document.getElementById('zip-code-selection-form');
    const favoriteZipCodesSelect = document.getElementById('favorite-zip-codes');
    const newZipCodeInput = document.getElementById('new-zip-code-input');

    function fetchFavoriteZipCodes() {
        fetch('/api/rest/user/zip-codes')
            .then(response => response.json())
            .then(data => {
                data.forEach(zipCode => {
                    const option = document.createElement('option');
                    option.value = zipCode.zipCode;
                    option.textContent = `${zipCode.nickname} (${zipCode.zipCode})`;
                    favoriteZipCodesSelect.appendChild(option);
                });
            });
    }

    zipCodeSelectionForm.addEventListener('submit', function (event) {
        event.preventDefault();
        let zipCode = newZipCodeInput.value;
        if (favoriteZipCodesSelect.value) {
            zipCode = favoriteZipCodesSelect.value;
        }

        if (zipCode) {
            window.location.href = `/weather?zipCode=${zipCode}&t=${new Date().getTime()}`;
        }
    });

    fetchFavoriteZipCodes();

    // Enable Day.js plugins for timezone support
    dayjs.extend(window.dayjs_plugin_utc);
    dayjs.extend(window.dayjs_plugin_timezone);
    dayjs.extend(window.dayjs_plugin_customParseFormat);

    const dataContainer = document.getElementById('weather-data-container');

    // Check if the data container element exists, which holds our data
    if (!dataContainer) {
        return; // Do nothing if data is not available
    }

    // Read the forecast data from the data-* attributes
    const rawHourlyForecastJson = dataContainer.dataset.hourlyForecastJson;
    const rawDailyForecastJson = dataContainer.dataset.dailyForecastJson;
    const rawObservationJson = dataContainer.dataset.rawObservationJson;
    const rawObservationStationsJson = dataContainer.dataset.observationStationsJson;
    const rawPointsJson = dataContainer.dataset.pointsJson;
    const locationTimeZone = dataContainer.dataset.locationTimeZone;
    const locationName = dataContainer.dataset.locationName;

    // Get all DOM elements needed for rendering
    const datePicker = document.getElementById('date-picker');
    const startTimeSelect = document.getElementById('start-time-select');
    const windowSelect = document.getElementById('window-select');
    const currentWeatherCard = document.getElementById('current-weather-card');
    const liveObservationCard = document.getElementById('live-observation-card');
    const staticHourlyChartCanvas = document.getElementById('hourly-chart-static');
    const dynamicHourlyChartCanvas = document.getElementById('hourly-chart-dynamic');
    const hourlyForecastDetails = document.getElementById('hourly-forecast-details');
    const dailyForecastDetails = document.getElementById('daily-forecast-details');
    const moreInfoDetails = document.getElementById('more-info-details');

    // Parse the main forecast data
    const hourlyData = JSON.parse(rawHourlyForecastJson); // The hourly forecast has 'periods' at the top level
    const allPeriods = hourlyData.periods;
    const dailyData = JSON.parse(rawDailyForecastJson); // The daily forecast also has 'periods' at the top level for ld+json
    const dailyPeriods = dailyData.periods;

    const stationsData = JSON.parse(rawObservationStationsJson);
    const pointsData = JSON.parse(rawPointsJson);

    // Chart instance variable for the dynamic chart
    let dynamicHourlyChart = null;

    // Initialize date picker and time inputs for the custom view
    const todayInLocation = dayjs().tz(locationTimeZone);
    datePicker.value = todayInLocation.format('YYYY-MM-DD');

    // Limit the date picker to the available forecast range from the daily forecast data.
    // This prevents users from selecting dates for which there is no data.
    if (dailyPeriods && dailyPeriods.length > 0) {
        const firstDay = dayjs(dailyPeriods[0].startTime).tz(locationTimeZone);
        const lastDay = dayjs(dailyPeriods[dailyPeriods.length - 1].endTime).tz(locationTimeZone);
        datePicker.min = firstDay.format('YYYY-MM-DD');
        datePicker.max = lastDay.format('YYYY-MM-DD');
    }

    /**
     * Populates the start time dropdown based on the available hours for the selected date.
     */
    function populateStartTimeOptions() {
        const selectedDateStr = datePicker.value;
        const selectedDate = dayjs(selectedDateStr);

        // Find all unique hours available for the selected date
        const availableHours = allPeriods
            .map(p => dayjs(p.startTime).tz(locationTimeZone))
            .filter(pDate => pDate.isSame(selectedDate, 'day'))
            .map(pDate => pDate.hour())
            .filter((hour, index, self) => self.indexOf(hour) === index) // Get unique hours
            .sort((a, b) => a - b);

        const currentSelection = startTimeSelect.value;
        startTimeSelect.innerHTML = '';

        // Populate new options
        availableHours.forEach(hour => {
            const option = document.createElement('option');
            option.value = hour; // Use 24-hour format for value
            option.textContent = dayjs().hour(hour).format('h A'); // Display in 12-hour format
            startTimeSelect.appendChild(option);
        });

        // Try to preserve the previous selection if it's still valid
        if (availableHours.includes(parseInt(currentSelection))) {
            startTimeSelect.value = currentSelection;
        } else {
            // Set a sensible default value
            const nowInLocation = dayjs().tz(locationTimeZone);
            if (selectedDate.isSame(nowInLocation, 'day') && availableHours.includes(nowInLocation.hour())) {
                startTimeSelect.value = nowInLocation.hour();
            } else if (availableHours.length > 0) {
                startTimeSelect.value = availableHours[0];
            }
        }
    }

    /**
     * Updates the dynamic hourly forecast chart based on user selections.
     */
    function updateHourlyForecast() {
        // --- Get the final values from the controls ---
        const selectedDate = datePicker.value;
        const startHour = parseInt(startTimeSelect.value);
        const startDate = dayjs.tz(selectedDate, locationTimeZone).hour(startHour).minute(0).second(0);

        // --- Adjust the time window options based on the selected start time ---
        const lastPeriod = allPeriods[allPeriods.length - 1];
        const forecastEndDate = dayjs(lastPeriod.endTime);
        const hoursRemaining = Math.floor(forecastEndDate.diff(startDate, 'hour'));

        Array.from(windowSelect.options).forEach(option => { option.disabled = parseInt(option.value) > hoursRemaining; });

        // If the currently selected window is now disabled, select the largest valid one.
        if (windowSelect.options[windowSelect.selectedIndex].disabled) {
            const largestAvailable = Array.from(windowSelect.options).reverse().find(opt => !opt.disabled);
            if (largestAvailable) {
                windowSelect.value = largestAvailable.value;
            }
        }

        const windowHours = parseInt(windowSelect.value);
        const endDate = startDate.add(windowHours, 'hour');

        const filteredPeriods = allPeriods.filter(period => {
            const periodDate = dayjs(period.startTime);
            return !periodDate.isBefore(startDate) && periodDate.isBefore(endDate);
        });

        // Dynamic Hourly Chart
        const labels = filteredPeriods.map(period => dayjs(period.startTime).tz(locationTimeZone).format('h:mm A'));
        const temperatureData = filteredPeriods.map(period => period.temperature);
        const precipitationData = filteredPeriods.map(period => period.probabilityOfPrecipitation.value || 0);

        if (dynamicHourlyChart) {
            dynamicHourlyChart.destroy();
        }

        dynamicHourlyChart = new Chart(dynamicHourlyChartCanvas, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Temperature (°F)',
                    data: temperatureData,
                    borderColor: '#0d6efd',
                    backgroundColor: 'rgba(13, 110, 253, 0.1)',
                    fill: true,
                    yAxisID: 'y'
                }, {
                    label: 'Precipitation Probability (%)',
                    data: precipitationData,
                    borderColor: '#ff6384',
                    backgroundColor: '#ff6384',
                    yAxisID: 'y1'
                }]
            },
            options: {
                scales: {
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left'
                    },
                    y1: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        grid: {
                            drawOnChartArea: false
                        }
                    }
                }
            }
        });

        // --- Render Detailed Hourly Table for Custom View ---
        const dynamicDetailsContainer = document.getElementById('dynamic-hourly-details');
        let detailsHtml = `
            <table class="table table-sm table-hover">
                <thead>
                    <tr>
                        <th>Time</th>
                        <th></th>
                        <th>Forecast</th>
                        <th>Temp.</th>
                        <th>Precip.</th>
                        <th>Wind</th>
                    </tr>
                </thead>
                <tbody>
        `;

        if (filteredPeriods.length > 0) {
            filteredPeriods.forEach(period => {
                detailsHtml += `
                    <tr>
                        <td>${dayjs(period.startTime).tz(locationTimeZone).format('h A')}</td>
                        <td><img src="${period.icon}" alt="${period.shortForecast}" width="30"></td>
                        <td>${period.shortForecast}</td>
                        <td>${period.temperature}°${period.temperatureUnit}</td>
                        <td>${period.probabilityOfPrecipitation.value || 0}%</td>
                        <td>${period.windSpeed} ${period.windDirection}</td>
                    </tr>
                `;
            });
        } else {
            detailsHtml += '<tr><td colspan="6" class="text-center text-muted">No data available for the selected time.</td></tr>';
        }

        detailsHtml += '</tbody></table>';
        dynamicDetailsContainer.innerHTML = detailsHtml;
    }

    /**
     * Renders all the static weather components on the page.
     */
    function renderPageComponents() {
        renderLiveObservation();
        renderCurrentForecast();
        renderStaticHourlyChart();
        renderHourlyDetails();
        renderDailySummaries();
        renderMoreInfo();
    }

    function renderLiveObservation() {
        if (!rawObservationJson) {
            liveObservationCard.innerHTML = `<div class="card-body"><p class="text-muted">No live observation data available.</p></div>`;
            return;
        }

        const obs = JSON.parse(rawObservationJson);
        const stationName = stationsData['@graph'][0].name;

        // Use a default icon if the icon from the API is null
        const iconUrl = obs.icon ? escapeHTML(obs.icon) : 'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/icons/cloud.svg';
        liveObservationCard.innerHTML = `
            <div class="card-body">
                <h5 class="card-title">
                    Live at ${escapeHTML(stationName)}
                    <small class="text-muted">(${dayjs(obs.timestamp).tz(locationTimeZone).format('h:mm A')})</small>
                </h5>
                <div class="d-flex align-items-center">
                    <img src="${iconUrl}" alt="${escapeHTML(obs.textDescription || 'Weather icon')}" class="me-3">
                    <div>
                        <p class="card-text fs-4">${Math.round(obs.temperature.value * 9/5 + 32)}°F</p>
                        <p class="card-text">${escapeHTML(obs.textDescription)}</p>
                    </div>
                    <div class="ms-auto text-end">
                         <p class="mb-1"><strong>Wind:</strong> ${Math.round(obs.windSpeed.value * 0.621371)} mph ${escapeHTML(obs.windDirection.value)}°</p>
                         <p class="mb-1"><strong>Humidity:</strong> ${Math.round(obs.relativeHumidity.value)}%</p>
                         <p class="mb-0"><strong>Pressure:</strong> ${Math.round(obs.barometricPressure.value / 3386.39)} inHg</p>
                    </div>
                </div>
            </div>
        `;
    }

    function renderCurrentForecast() {
        const nowInLocation = dayjs().tz(locationTimeZone);
        let currentPeriod = allPeriods.find(p => {
            const start = dayjs(p.startTime);
            const end = dayjs(p.endTime);
            return nowInLocation.isAfter(start) && nowInLocation.isBefore(end);
        }) || allPeriods[0];

        currentWeatherCard.innerHTML = `
            <div class="card-body">
                <h5 class="card-title"></h5>
                <div class="d-flex align-items-center">
                    <img src="${currentPeriod.icon}" alt="${currentPeriod.shortForecast}" class="me-3">
                    <div>
                        <p class="card-text fs-4">${currentPeriod.temperature}°${currentPeriod.temperatureUnit}</p>
                        <p class="card-text">${currentPeriod.shortForecast}</p>
                    </div>
                    <div class="ms-auto text-end">
                        <p class="mb-1"><strong>Wind:</strong> ${currentPeriod.windSpeed} ${currentPeriod.windDirection}</p>
                        <p class="mb-1"><strong>Humidity:</strong> ${currentPeriod.relativeHumidity.value}%</p>
                        <p class="mb-0"><strong>Dewpoint:</strong> ${Math.round(currentPeriod.dewpoint.value * 9/5 + 32)}°F</p>
                    </div>
                </div>
            </div>
        `;
        const cardTitle = currentWeatherCard.querySelector('.card-title');
        cardTitle.textContent = `Forecast for ${locationName} at ${dayjs(currentPeriod.startTime).tz(locationTimeZone).format('h A')}`;
    }

    function renderStaticHourlyChart() {
        const staticLabels = allPeriods.slice(0, 24).map(period => dayjs(period.startTime).tz(locationTimeZone).format('h A'));
        const staticTemperatureData = allPeriods.slice(0, 24).map(period => period.temperature);
        const staticPrecipitationData = allPeriods.slice(0, 24).map(period => period.probabilityOfPrecipitation.value || 0);
        new Chart(staticHourlyChartCanvas, {
            type: 'line',
            data: {
                labels: staticLabels,
                datasets: [{
                    label: 'Temperature (°F)',
                    data: staticTemperatureData,
                    borderColor: '#0d6efd',
                    backgroundColor: 'rgba(13, 110, 253, 0.1)',
                    fill: true,
                    yAxisID: 'y'
                }, {
                    label: 'Precipitation Probability (%)',
                    data: staticPrecipitationData,
                    borderColor: '#ff6384',
                    backgroundColor: '#ff6384',
                    yAxisID: 'y1'
                }]
            },
            options: {
                scales: {
                    y: { type: 'linear', display: true, position: 'left' },
                    y1: { type: 'linear', display: true, position: 'right', grid: { drawOnChartArea: false } }
                }
            }
        });
    }

    function renderHourlyDetails() {
        let hourlyHtml = '';
        allPeriods.slice(0, 24).forEach(period => {
            hourlyHtml += `
                <div class="col">
                    <div class="card h-100">
                        <div class="card-body text-center">
                            <h6 class="card-title">${escapeHTML(dayjs(period.startTime).tz(locationTimeZone).format('h A'))}</h6>
                            <img src="${escapeHTML(period.icon)}" alt="${escapeHTML(period.shortForecast)}" title="${escapeHTML(period.shortForecast)}" width="50">
                            <p class="card-text">${escapeHTML(period.temperature)}°${escapeHTML(period.temperatureUnit)}</p>
                        </div>
                    </div>
                </div>
            `;
        });
        hourlyForecastDetails.innerHTML = hourlyHtml;
    }

    function renderDailySummaries() {
        const dailySummaries = {};
        dailyPeriods.slice(0, 14).forEach(period => {
            const day = dayjs(period.startTime).tz(locationTimeZone).format('dddd');
            if (!dailySummaries[day]) {
                dailySummaries[day] = { name: day, high: -Infinity, low: Infinity, icon: period.icon, forecast: period.shortForecast, wind: `${period.windSpeed} ${period.windDirection}` };
            }
            dailySummaries[day].high = Math.max(dailySummaries[day].high, period.temperature);
            dailySummaries[day].low = Math.min(dailySummaries[day].low, period.temperature);
            if (period.isDaytime) {
                dailySummaries[day].icon = period.icon;
                dailySummaries[day].forecast = period.shortForecast;
                dailySummaries[day].wind = `${period.windSpeed} ${period.windDirection}`;
            }
        });

        let dailyHtml = '';
        Object.values(dailySummaries).slice(0, 7).forEach(day => {
            dailyHtml += `
                <div class="d-flex justify-content-between align-items-center border-bottom py-2">
                    <span class="fw-bold" style="flex-basis: 120px;">${escapeHTML(day.name)}</span>
                    <img src="${escapeHTML(day.icon)}" alt="${escapeHTML(day.forecast)}" width="50" class="mx-3">
                    <span class="text-muted" style="flex-basis: 150px;">${escapeHTML(day.forecast)}</span>
                    <span class="text-muted" style="flex-basis: 100px;"><i class="bi bi-wind me-2"></i>${escapeHTML(day.wind)}</span>
                    <span class="ms-auto fw-bold">${escapeHTML(day.high)}° / ${escapeHTML(day.low)}°</span>
                </div>
            `;
        });
        dailyForecastDetails.innerHTML = dailyHtml;
    }

    function renderMoreInfo() {
        const officePromise = fetch(pointsData.forecastOffice).then(res => res.json());
        const zonePromise = fetch(pointsData.forecastZone).then(res => res.json());
        const countyPromise = fetch(pointsData.county).then(res => res.json());

        Promise.all([officePromise, zonePromise, countyPromise])
            .then(([officeData, zoneData, countyData]) => {
                moreInfoDetails.innerHTML = `
                    <ul class="list-group list-group-flush">
                        <li class="list-group-item d-flex justify-content-between align-items-center">
                            <span>Forecast Office</span>
                            <span class="text-end">
                                ${escapeHTML(officeData.name)}
                                <a href="${escapeHTML(pointsData.forecastOffice)}" target="_blank" rel="noopener noreferrer" class="ms-2 text-decoration-none">(${escapeHTML(officeData.id)})</a>
                            </span>
                        </li>
                        <li class="list-group-item d-flex justify-content-between align-items-center">
                            <span>Forecast Zone</span>
                            <span class="text-end">
                                ${escapeHTML(zoneData.properties.name)}
                                <a href="${escapeHTML(pointsData.forecastZone)}" target="_blank" rel="noopener noreferrer" class="ms-2 text-decoration-none">(${escapeHTML(zoneData.properties.id)})</a>
                            </span>
                        </li>
                        <li class="list-group-item d-flex justify-content-between align-items-center">
                            <span>County</span>
                            <span class="text-end">
                                ${escapeHTML(countyData.properties.name)}
                                <a href="${escapeHTML(pointsData.county)}" target="_blank" rel="noopener noreferrer" class="ms-2 text-decoration-none">(${escapeHTML(countyData.properties.id)})</a>
                            </span>
                        </li>
                        <li class="list-group-item d-flex justify-content-between align-items-center">
                            <span>Radar Station</span>
                            <span>${escapeHTML(pointsData.radarStation)}</span>
                        </li>
                    </ul>
                `;
            })
            .catch(error => {
                console.error('Error fetching more info details:', error);
                moreInfoDetails.innerHTML = '<p class="text-muted">Could not load additional forecast details.</p>';
            });
    }

    // --- Main Execution ---

    // Render all the static components first.
    renderPageComponents();

    // Then, populate the time options and render the initial state of the custom view.
    populateStartTimeOptions();
    updateHourlyForecast();

    // Finally, attach event listeners to update the dynamic chart.
    datePicker.addEventListener('change', () => {
        populateStartTimeOptions();
        updateHourlyForecast();
    });
    startTimeSelect.addEventListener('change', updateHourlyForecast);
    windowSelect.addEventListener('change', updateHourlyForecast);
});
