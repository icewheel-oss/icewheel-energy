document.addEventListener('DOMContentLoaded', () => {
    const locationElement = document.getElementById('location');
    const weatherDataElement = document.getElementById('weather-data');

    // Fetch user location
    fetch('/api/rest/user/location')
        .then(response => response.json())
        .then(location => {
            locationElement.textContent = `Weather for ${location.locationName}`;
            const { latitude, longitude } = location;

            // Fetch weather data from NWS API
            const pointsUrl = `https://api.weather.gov/points/${latitude},${longitude}`;

            fetch(pointsUrl)
                .then(response => response.json())
                .then(pointsData => {
                    const forecastUrl = pointsData.properties.forecastHourly;
                    return fetch(forecastUrl);
                })
                .then(response => response.json())
                .then(forecastData => {
                    const periods = forecastData.properties.periods;
                    let tableHtml = `
                        <table class="table table-striped table-hover">
                            <thead>
                                <tr>
                                    <th>Time</th>
                                    <th></th>
                                    <th>Temperature</th>
                                    <th>Wind</th>
                                    <th>Forecast</th>
                                </tr>
                            </thead>
                            <tbody>
                    `;

                    periods.forEach(period => {
                        tableHtml += `
                            <tr>
                                <td>${new Date(period.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</td>
                                <td><img src="${period.icon}" alt="${period.shortForecast}" width="30"></td>
                                <td>${period.temperature} ${period.temperatureUnit}</td>
                                <td>${period.windSpeed} ${period.windDirection}</td>
                                <td>${period.shortForecast}</td>
                            </tr>
                        `;
                    });

                    tableHtml += `
                            </tbody>
                        </table>
                    `;

                    weatherDataElement.innerHTML = tableHtml;
                })
                .catch(error => {
                    console.error('Error fetching weather data:', error);
                    weatherDataElement.innerHTML = '<p>Could not fetch weather data.</p>';
                });
        })
        .catch(error => {
            console.error('Error fetching user location:', error);
            locationElement.textContent = 'Could not fetch user location.';
        });
});
