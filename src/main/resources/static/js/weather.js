document.addEventListener('DOMContentLoaded', function () {
    const zipCodeInput = document.getElementById('zip-code-input');
    const zipCodeSubmit = document.getElementById('zip-code-submit');

    if (zipCodeSubmit) {
        zipCodeSubmit.addEventListener('click', function () {
            const zipCode = zipCodeInput.value;
            if (zipCode) {
                fetch('/api/rest/weather/zip-to-lat-lon-enabled')
                    .then(response => response.json())
                    .then(enabled => {
                        if (enabled) {
                            fetch(`/api/rest/weather/lat-lon?zip=${zipCode}`)
                                .then(response => response.json())
                                .then(data => {
                                    const lat = data.places[0].latitude;
                                    const lon = data.places[0].longitude;
                                    updateWeatherPreferences(lat, lon, zipCode);
                                });
                        } else {
                            fetch(`https://api.zippopotam.us/us/${zipCode}`)
                                .then(response => response.json())
                                .then(data => {
                                    const lat = data.places[0].latitude;
                                    const lon = data.places[0].longitude;
                                    updateWeatherPreferences(lat, lon, zipCode);
                                });
                        }
                    });
            }
        });
    }

    function updateWeatherPreferences(latitude, longitude, zipCode) {
        fetch('/api/rest/weather/preferences', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ latitude, longitude, zipCode })
        }).then(() => {
            // Optionally, hide the zip code input and refresh the weather widget
            document.getElementById('weather-zip-code').classList.add('d-none');
            // You might need to reload the weather data or the whole page
            location.reload();
        });
    }
});
