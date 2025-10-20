
document.addEventListener('DOMContentLoaded', () => {
    const alertDetailsContainer = document.getElementById('alert-details-container');
    const alertDetailsSpinner = document.getElementById('alert-details-spinner');
    const alertId = window.location.pathname.split('/').pop();

    function showSpinner() {
        alertDetailsSpinner.style.display = 'block';
        alertDetailsContainer.style.display = 'none';
    }

    function hideSpinner() {
        alertDetailsSpinner.style.display = 'none';
        alertDetailsContainer.style.display = 'block';
    }

    function loadAlertDetails() {
        showSpinner();
        fetch(`/api/rest/alerts/${alertId}`)
            .then(response => response.json())
            .then(data => {
                hideSpinner();
                if (data) {
                    alertDetailsContainer.innerHTML = `
                        <div class="card">
                            <div class="card-header">
                                <h5>${data.properties.headline}</h5>
                            </div>
                            <div class="card-body">
                                <p class="card-text">${data.properties.description}</p>
                                <p class="card-text"><strong>Area:</strong> ${data.properties.areaDesc}</p>
                                <p class="card-text"><strong>Severity:</strong> ${data.properties.severity}</p>
                                <p class="card-text"><strong>Certainty:</strong> ${data.properties.certainty}</p>
                                <p class="card-text"><strong>Urgency:</strong> ${data.properties.urgency}</p>
                                <p class="card-text"><strong>Effective:</strong> ${new Date(data.properties.effective).toLocaleString()}</p>
                                <p class="card-text"><strong>Expires:</strong> ${new Date(data.properties.expires).toLocaleString()}</p>
                                <p class="card-text"><strong>Instruction:</strong> ${data.properties.instruction}</p>
                            </div>
                        </div>
                    `;
                } else {
                    alertDetailsContainer.innerHTML = '<p>Alert not found.</p>';
                }
            })
            .catch(error => {
                hideSpinner();
                console.error('Error fetching alert details:', error);
                alertDetailsContainer.innerHTML = '<p>Error fetching alert details.</p>';
            });
    }

    loadAlertDetails();
});
