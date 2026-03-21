document.addEventListener('DOMContentLoaded', () => {
    const locationElement = document.getElementById('location');
    const weatherDataElement = document.getElementById('weather-data');
    const weatherChartCanvas = document.getElementById('weather-chart');
    const datePicker = document.getElementById('date-picker');
    const startTimeInput = document.getElementById('start-time');
    const windowSelect = document.getElementById('window-select');

    let chart = null;
    let allPeriods = [];

    // Initialize date picker and time inputs
    const today = new Date();
    datePicker.value = today.toISOString().split('T')[0];
    startTimeInput.value = today.toTimeString().split(' ')[0].substring(0, 5);

    function updateChartAndTable() {
        const selectedDate = datePicker.value;
        const startTime = startTimeInput.value;
        const windowHours = parseInt(windowSelect.value);

        const [startHour, startMinute] = startTime.split(':').map(Number);
        const startDate = new Date(selectedDate);
        startDate.setHours(startHour, startMinute, 0, 0);

        const endDate = new Date(startDate.getTime() + windowHours * 60 * 60 * 1000);

        const filteredPeriods = allPeriods.filter(period => {
            const periodDate = new Date(period.startTime);
            return periodDate >= startDate && periodDate < endDate;
        });

        // Update Chart
        const labels = filteredPeriods.map(period => new Date(period.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
        const temperatureData = filteredPeriods.map(period => period.temperature);
        const precipitationData = filteredPeriods.map(period => period.probabilityOfPrecipitation.value || 0);

        if (chart) {
            chart.destroy();
        }

        chart = new Chart(weatherChartCanvas, {
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
                        grid: {
                            drawOnChartArea: false,
                        },
                    },
                }
            }
        });

        // Update Table
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

        filteredPeriods.forEach(period => {
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
    }

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
                    allPeriods = forecastData.properties.periods;
                    updateChartAndTable();

                    // Add event listeners
                    datePicker.addEventListener('change', updateChartAndTable);
                    startTimeInput.addEventListener('change', updateChartAndTable);
                    endTimeInput.addEventListener('change', updateChartAndTable);
                    windowSelect.addEventListener('change', updateChartAndTable);
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
