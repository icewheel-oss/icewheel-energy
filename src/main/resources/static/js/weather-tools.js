document.addEventListener('DOMContentLoaded', () => {
    fetch('/api/nws/alerts')
        .then(response => response.json())
        .then(data => {
            if (data['@graph'] && data['@graph'].length > 0) {
                document.getElementById('alert-banner').classList.remove('d-none');
            }
        });
    document.getElementById('fetch-alerts').addEventListener('click', () => {
        fetch('/api/nws/alerts')
            .then(response => response.json())
            .then(data => {
                const container = document.getElementById('alerts-container');
                container.innerHTML = ''; // Clear previous results

                if (data['@graph'] && data['@graph'].length > 0) {
                    data['@graph'].forEach(alert => {
                        const alertDiv = document.createElement('div');
                        alertDiv.className = 'card mb-3';

                        const severity = alert.severity || 'Unknown';
                        let alertClass = 'bg-light';
                        switch (severity) {
                            case 'Minor':
                                alertClass = 'bg-warning text-dark';
                                break;
                            case 'Moderate':
                                alertClass = 'bg-warning text-dark';
                                break;
                            case 'Severe':
                                alertClass = 'bg-danger text-white';
                                break;
                            case 'Extreme':
                                alertClass = 'bg-danger text-white';
                                break;
                        }

                        alertDiv.innerHTML = `
                            <div class="card-header ${alertClass}">
                                ${alert.event}
                            </div>
                            <div class="card-body">
                                <h5 class="card-title">${alert.headline}</h5>
                                <p class="card-text">${alert.description}</p>
                                <button class="btn btn-primary btn-sm" data-bs-toggle="modal" data-bs-target="#alert-modal" data-alert='${JSON.stringify(alert)}'>
                                    View Details
                                </button>
                            </div>
                        `;
                        container.appendChild(alertDiv);
                    });
                } else {
                    container.innerHTML = '<p>No active alerts.</p>';
                }
            });
    });

    document.getElementById('fetch-glossary').addEventListener('click', () => {
        fetch('/api/nws/glossary')
            .then(response => response.json())
            .then(data => {
                const container = document.getElementById('glossary-container');
                container.innerHTML = ''; // Clear previous results

                const glossary = data.glossary;
                if (glossary && glossary.length > 0) {
                    const dl = document.createElement('dl');
                    glossary.forEach(item => {
                        if (item.term && item.definition) {
                            const dt = document.createElement('dt');
                            dt.className = 'fw-bold text-body';
                            dt.textContent = item.term;
                            const dd = document.createElement('dd');
                            dd.className = 'text-body';
                            dd.innerHTML = item.definition;
                            dl.appendChild(dt);
                            dl.appendChild(dd);
                        }
                    });
                    container.appendChild(dl);
                } else {
                    container.innerHTML = '<p>No glossary terms found.</p>';
                }
            });
    });

    document.getElementById('fetch-stations').addEventListener('click', () => {
        fetch('/api/nws/stations')
            .then(response => response.json())
            .then(data => {
                const container = document.getElementById('stations-container');
                container.innerHTML = ''; // Clear previous results

                if (data['@graph'] && data['@graph'].length > 0) {
                    data['@graph'].forEach(station => {
                        const card = document.createElement('div');
                        card.className = 'card mb-3';

                        const cardBody = document.createElement('div');
                        cardBody.className = 'card-body';

                        const title = document.createElement('h5');
                        title.className = 'card-title';
                        title.textContent = station.name;

                        const stationId = document.createElement('p');
                        stationId.className = 'card-text';
                        stationId.innerHTML = `<strong>Station Identifier:</strong> ${station.stationIdentifier}`;

                        const timeZone = document.createElement('p');
                        timeZone.className = 'card-text';
                        timeZone.innerHTML = `<strong>Time Zone:</strong> ${station.timeZone}`;

                        cardBody.appendChild(title);
                        cardBody.appendChild(stationId);
                        cardBody.appendChild(timeZone);
                        card.appendChild(cardBody);
                        container.appendChild(card);
                    });
                } else {
                    container.innerHTML = '<p>No stations found.</p>';
                }
            });
    });

    document.getElementById('fetch-products').addEventListener('click', () => {
        fetch('/api/nws/products')
            .then(response => response.json())
            .then(data => {
                const container = document.getElementById('products-container');
                container.innerHTML = ''; // Clear previous results

                if (data['@graph'] && data['@graph'].length > 0) {
                    const list = document.createElement('ul');
                    list.className = 'list-group';
                    data['@graph'].forEach(product => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item';
                        li.textContent = product.productName;
                        list.appendChild(li);
                    });
                    container.appendChild(list);
                } else {
                    container.innerHTML = '<p>No products found.</p>';
                }
            });
    });

    document.getElementById('copy-alerts').addEventListener('click', () => {
        fetch('/api/nws/alerts')
            .then(response => response.json())
            .then(data => {
                navigator.clipboard.writeText(JSON.stringify(data, null, 2));
                const container = document.getElementById('alerts-container');
                container.innerHTML = `<pre>${JSON.stringify(data, null, 2)}</pre>`;
            });
    });

    document.getElementById('copy-glossary').addEventListener('click', () => {
        fetch('/api/nws/glossary')
            .then(response => response.json())
            .then(data => {
                navigator.clipboard.writeText(JSON.stringify(data, null, 2));
                const container = document.getElementById('glossary-container');
                container.innerHTML = `<pre>${JSON.stringify(data, null, 2)}</pre>`;
            });
    });

    document.getElementById('copy-stations').addEventListener('click', () => {
        fetch('/api/nws/stations')
            .then(response => response.json())
            .then(data => {
                navigator.clipboard.writeText(JSON.stringify(data, null, 2));
                const container = document.getElementById('stations-container');
                container.innerHTML = `<pre>${JSON.stringify(data, null, 2)}</pre>`;
            });
    });

    document.getElementById('copy-products').addEventListener('click', () => {
        fetch('/api/nws/products')
            .then(response => response.json())
            .then(data => {
                navigator.clipboard.writeText(JSON.stringify(data, null, 2));
                const container = document.getElementById('products-container');
                container.innerHTML = `<pre>${JSON.stringify(data, null, 2)}</pre>`;
            });
    });
});