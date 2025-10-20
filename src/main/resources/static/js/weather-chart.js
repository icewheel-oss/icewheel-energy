document.addEventListener('DOMContentLoaded', () => {
    const locationElement = document.getElementById('location');
    const weatherChartCanvas = document.getElementById('weather-chart');

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

                    const labels = periods.map(period => new Date(period.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
                    const temperatureData = periods.map(period => period.temperature);
                    const precipitationData = periods.map(period => period.probabilityOfPrecipitation.value || 0);

                    new Chart(weatherChartCanvas, {
                        type: 'line',
                        data: {
                            labels: labels,
                            datasets: [
                                {
                                    label: 'Temperature (°F)',
                                    data: temperatureData,
                                    borderColor: '#ff6384',
                                    backgroundColor: '#ff6384',
                                    yAxisID: 'y',
                                },
                                {
                                    label: 'Precipitation Probability (%)',
                                    data: precipitationData,
                                    borderColor: '#36a2eb',
                                    backgroundColor: '#36a2eb',
                                    yAxisID: 'y1',
                                }
                            ]
                        },
                        options: {
                            scales: {
                                y: {
                                    type: 'linear',
                                    display: true,
                                    position: 'left',
                                },
                                y1: {
                                    type: 'linear',
                                    display: true,
                                    position: 'right',

                                    // grid line settings
                                    grid: {
                                        drawOnChartArea: false, // only want the grid lines for one axis to show up
                                    },
                                },
                            }
                        }
                    });
                })
                .catch(error => {
                    console.error('Error fetching weather data:', error);
                    weatherChartCanvas.innerHTML = '<p>Could not fetch weather data.</p>';
                });
        })
        .catch(error => {
            console.error('Error fetching user location:', error);
            locationElement.textContent = 'Could not fetch user location.';
        });
});
