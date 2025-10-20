document.addEventListener('DOMContentLoaded', () => {
    const productsContainer = document.getElementById('products-container');
    const productsSpinner = document.getElementById('products-spinner');
    const productsData = JSON.parse(document.getElementById('products-data').textContent);

    function hideSpinner() {
        productsSpinner.style.display = 'none';
        productsContainer.style.display = 'block';
    }

    function renderProducts(products) {
        if (products && products["@graph"] && products["@graph"].length > 0) {
            const productsHtml = products["@graph"].map(product => {
                return `
                    <div class="card mb-3">
                        <div class="card-header">
                            <h5>${product.productName}</h5>
                        </div>
                        <div class="card-body">
                            <p class="card-text"><strong>Product Code:</strong> ${product.productCode}</p>
                            <p class="card-text"><strong>WMO Collective ID:</strong> ${product.wmoCollectiveId}</p>
                            <p class="card-text"><strong>Issuing Office:</strong> ${product.issuingOffice}</p>
                        </div>
                    </div>
                `;
            }).join('');
            productsContainer.innerHTML = productsHtml;
        } else {
            productsContainer.innerHTML = '<p>No products found.</p>';
        }
    }

    hideSpinner();
    renderProducts(productsData);
});