document.addEventListener('DOMContentLoaded', () => {
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(position => {
            const lat = position.coords.latitude;
            const lon = position.coords.longitude;
            fetch(`/api/nws/alerts?point=${lat},${lon}`)
                .then(response => response.json())
                .then(data => {
                    if (data['@graph'] && data['@graph'].length > 0) {
                        const alert = data['@graph'][0];
                        const alertModalBody = document.getElementById('alert-modal-body');
                        alertModalBody.innerHTML = `
                            <h5>${alert.event}</h5>
                            <p><strong>Headline:</strong> ${alert.headline}</p>
                            <p><strong>Description:</strong> ${alert.description}</p>
                            <p><strong>Severity:</strong> ${alert.severity}</p>
                            <p><strong>Urgency:</strong> ${alert.urgency}</p>
                            <p><strong>Certainty:</strong> ${alert.certainty}</p>
                        `;
                    }
                });
        });
    }
});