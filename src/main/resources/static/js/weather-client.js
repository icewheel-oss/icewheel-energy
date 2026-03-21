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
                    let weatherHtml = '';

                    periods.forEach(period => {
                        weatherHtml += `
                            <div class="weather-period">
                                <h3>${period.startTime}</h3>
                                <img src="${period.icon}" alt="${period.shortForecast}">
                                <p><strong>Temperature:</strong> ${period.temperature} ${period.temperatureUnit}</p>
                                <p><strong>Wind:</strong> ${period.windSpeed} ${period.windDirection}</p>
                                <p><strong>Forecast:</strong> ${period.shortForecast}</p>
                            </div>
                        `;
                    });

                    weatherDataElement.innerHTML = weatherHtml;
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
