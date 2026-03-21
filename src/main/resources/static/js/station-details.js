document.addEventListener('DOMContentLoaded', () => {
    const stationDetailsContainer = document.getElementById('station-details-container');
    const stationDetailsSpinner = document.getElementById('station-details-spinner');
    const stationId = window.location.pathname.split('/').pop();

    function showSpinner() {
        stationDetailsSpinner.style.display = 'block';
        stationDetailsContainer.style.display = 'none';
    }

    function hideSpinner() {
        stationDetailsSpinner.style.display = 'none';
        stationDetailsContainer.style.display = 'block';
    }

    function loadStationDetails() {
        showSpinner();
        fetch(`/api/weather/stations/${stationId}`)
            .then(response => response.json())
            .then(data => {
                hideSpinner();
                if (data) {
                    stationDetailsContainer.innerHTML = `
                        <div class="card">
                            <div class="card-header">
                                <h5>${data.properties.name}</h5>
                            </div>
                            <div class="card-body">
                                <p class="card-text"><strong>Station Identifier:</strong> ${data.properties.stationIdentifier}</p>
                                <p class="card-text"><strong>Time Zone:</strong> ${data.properties.timeZone}</p>
                                <p class="card-text"><strong>Elevation:</strong> ${data.properties.elevation.value} ${data.properties.elevation.unitCode}</p>
                            </div>
                        </div>
                    `;
                } else {
                    stationDetailsContainer.innerHTML = '<p>Station not found.</p>';
                }
            })
            .catch(error => {
                hideSpinner();
                console.error('Error fetching station details:', error);
                stationDetailsContainer.innerHTML = '<p>Error fetching station details.</p>';
            });
    }

    loadStationDetails();
});