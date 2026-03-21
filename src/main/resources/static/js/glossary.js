document.addEventListener('DOMContentLoaded', () => {
    const glossaryContainer = document.getElementById('glossary-container');
    const glossarySpinner = document.getElementById('glossary-spinner');
    const glossaryData = JSON.parse(document.getElementById('glossary-data').textContent);

    function hideSpinner() {
        glossarySpinner.style.display = 'none';
        glossaryContainer.style.display = 'block';
    }

    function renderGlossary(glossary) {
        if (glossary && glossary.glossary) {
            const terms = glossary.glossary.map(term => {
                return `
                    <div class="card mb-3">
                        <div class="card-header">
                            <h5>${term.term}</h5>
                        </div>
                        <div class="card-body">
                            <p class="card-text">${term.definition}</p>
                        </div>
                    </div>
                `;
            }).join('');
            glossaryContainer.innerHTML = terms;
        } else {
            glossaryContainer.innerHTML = '<p>No glossary terms found.</p>';
        }
    }

    hideSpinner();
    renderGlossary(glossaryData);
});