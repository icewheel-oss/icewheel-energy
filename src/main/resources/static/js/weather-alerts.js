document.addEventListener('DOMContentLoaded', () => {
    const alertModalBody = document.getElementById('alert-modal-body');
    if (!alertModalBody) {
        return; // Don't run if the modal isn't on the page
    }

    const WEATHER_ALERT_CHECKED_FLAG = 'weatherAlertChecked';
    const WEATHER_ALERT_DATA_KEY = 'weatherAlertData';

    const showLoading = () => {
        alertModalBody.innerHTML = '<div class="text-center"><div class="spinner-border" role="status"><span class="visually-hidden">Loading...</span></div><p class="mt-2">Fetching weather alerts...</p></div>';
    };

    const showError = (message) => {
        alertModalBody.innerHTML = `<div class="alert alert-warning" role="alert">${message}</div>`;
    };

    const renderAlert = (alert) => {
        alertModalBody.innerHTML = `
            <h5>${alert.event}</h5>
            <p><strong>Headline:</strong> ${alert.headline}</p>
            <p><strong>Description:</strong> ${alert.description}</p>
            <p><strong>Severity:</strong> ${alert.severity}</p>
            <p><strong>Urgency:</strong> ${alert.urgency}</p>
            <p><strong>Certainty:</strong> ${alert.certainty}</p>
        `;
    };
    
    const noAlerts = () => {
        alertModalBody.innerHTML = `<div class="alert alert-info" role="alert">No active weather alerts for your location.</div>`;
    };

    // Try to load from sessionStorage first
    const storedAlertData = sessionStorage.getItem(WEATHER_ALERT_DATA_KEY);
    if (storedAlertData) {
        const data = JSON.parse(storedAlertData);
        if (data['@graph'] && data['@graph'].length > 0) {
            renderAlert(data['@graph'][0]);
        } else {
            noAlerts();
        }
        return; // Data found and rendered, no need to proceed
    }

    // If no stored data, check if we've already tried to fetch this session
    if (sessionStorage.getItem(WEATHER_ALERT_CHECKED_FLAG)) {
        return; // Already checked this session, and no data was stored (e.g., no alerts found last time)
    }

    // Mark that we are performing the check this session
    sessionStorage.setItem(WEATHER_ALERT_CHECKED_FLAG, 'true');

    if (navigator.geolocation) {
        showLoading();
        navigator.geolocation.getCurrentPosition(
            (position) => {
                const lat = position.coords.latitude;
                const lon = position.coords.longitude;
                fetch(`/api/nws/alerts?point=${lat},${lon}`)
                    .then(response => {
                        if (!response.ok) {
                            throw new Error(`Network response was not ok: ${response.statusText}`);
                        }
                        return response.json();
                    })
                    .then(data => {
                        // Store data in sessionStorage before rendering
                        sessionStorage.setItem(WEATHER_ALERT_DATA_KEY, JSON.stringify(data));
                        if (data['@graph'] && data['@graph'].length > 0) {
                            const alert = data['@graph'][0];
                            renderAlert(alert);
                        } else {
                            noAlerts();
                        }
                    })
                    .catch(error => {
                        console.error('Error fetching weather alerts:', error);
                        showError('Could not retrieve weather alerts. Please try again later.');
                    });
            },
            (error) => {
                let message = 'Could not retrieve location.';
                switch (error.code) {
                    case error.PERMISSION_DENIED:
                        message = 'Location access was denied. Please enable it in your browser settings to see weather alerts.';
                        break;
                    case error.POSITION_UNAVAILABLE:
                        message = 'Location information is unavailable.';
                        break;
                    case error.TIMEOUT:
                        message = 'The request to get user location timed out.';
                        break;
                }
                console.error('Geolocation error:', error.message);
                showError(message);
            },
            {
                timeout: 10000 // 10-second timeout
            }
        );
    } else {
        showError('Geolocation is not supported by this browser.');
    }
});