document.addEventListener('DOMContentLoaded', () => {
    const alertsContainer = document.getElementById('alerts-container');
    const alertsSpinner = document.getElementById('alerts-spinner');
    const alertsData = JSON.parse(document.getElementById('alerts-data').textContent);

    function hideSpinner() {
        alertsSpinner.style.display = 'none';
        alertsContainer.style.display = 'block';
    }

    function renderAlerts(alerts) {
        if (alerts && alerts.features && alerts.features.length > 0) {
            const alertsHtml = alerts.features.map(alert => {
                return `
                    <div class="card mb-3">
                        <div class="card-header">
                            <h5>${alert.properties.headline}</h5>
                        </div>
                        <div class="card-body">
                            <p class="card-text">${alert.properties.description}</p>
                            <a href="/weather/alerts/${alert.properties.id}" class="btn btn-primary">View Details</a>
                        </div>
                    </div>
                `;
            }).join('');
            alertsContainer.innerHTML = alertsHtml;
        } else {
            alertsContainer.innerHTML = '<p>No active alerts.</p>';
        }
    }

    hideSpinner();
    renderAlerts(alertsData);
});