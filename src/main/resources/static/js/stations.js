document.addEventListener('DOMContentLoaded', () => {
    const stationsContainer = document.getElementById('stations-container');
    const stationsSpinner = document.getElementById('stations-spinner');
    const stationsData = JSON.parse(document.getElementById('stations-data').textContent);

    function hideSpinner() {
        stationsSpinner.style.display = 'none';
        stationsContainer.style.display = 'block';
    }

    function renderStations(stations) {
        if (stations && stations.features && stations.features.length > 0) {
            const stationsHtml = stations.features.map(station => {
                return `
                    <div class="card mb-3">
                        <div class="card-header">
                            <h5>${station.properties.name}</h5>
                        </div>
                        <div class="card-body">
                            <p class="card-text"><strong>Station Identifier:</strong> ${station.properties.stationIdentifier}</p>
                            <p class="card-text"><strong>Time Zone:</strong> ${station.properties.timeZone}</p>
                            <a href="/weather/stations/${station.properties.stationIdentifier}" class="btn btn-primary">View Details</a>
                        </div>
                    </div>
                `;
            }).join('');
            stationsContainer.innerHTML = stationsHtml;
        } else {
            stationsContainer.innerHTML = '<p>No stations found.</p>';
        }
    }

    hideSpinner();
    renderStations(stationsData);
});
