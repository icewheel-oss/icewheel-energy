document.addEventListener('DOMContentLoaded', function () {
    const zipCodeList = document.getElementById('zip-code-list');
    const addZipCodeForm = document.getElementById('add-zip-code-form');
    const newZipCodeInput = document.getElementById('new-zip-code');
    const newNicknameInput = document.getElementById('new-nickname');

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    function fetchZipCodes() {
        fetch('/api/rest/user/zip-codes')
            .then(response => response.json())
            .then(data => {
                zipCodeList.innerHTML = '';
                if (data.length === 0) {
                    zipCodeList.innerHTML = '<p class="text-muted">No favorite zip codes yet.</p>';
                } else {
                    const listGroup = document.createElement('ul');
                    listGroup.className = 'list-group';
                    data.forEach(zipCode => {
                        const listItem = document.createElement('li');
                        listItem.className = 'list-group-item d-flex justify-content-between align-items-center';
                        listItem.innerHTML = `
                            <span>
                                <span class="fw-bold">${zipCode.zipCode}</span>
                                <small class="text-muted ms-2">${zipCode.nickname || ''}</small>
                            </span>
                            <button class="btn btn-sm btn-outline-danger delete-zip-code" data-id="${zipCode.id}">
                                <i class="bi bi-trash"></i>
                            </button>
                        `;
                        listGroup.appendChild(listItem);
                    });
                    zipCodeList.appendChild(listGroup);
                }
            });
    }

    addZipCodeForm.addEventListener('submit', function (event) {
        event.preventDefault();
        const zipCode = newZipCodeInput.value;
        const nickname = newNicknameInput.value;

        fetch('/api/rest/user/zip-codes', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify({ zipCode, nickname })
        }).then(() => {
            newZipCodeInput.value = '';
            newNicknameInput.value = '';
            fetchZipCodes();
        });
    });

    zipCodeList.addEventListener('click', function (event) {
        if (event.target.classList.contains('delete-zip-code') || event.target.parentElement.classList.contains('delete-zip-code')) {
            const button = event.target.closest('.delete-zip-code');
            const zipCodeId = button.getAttribute('data-id');

            fetch(`/api/rest/user/zip-codes/${zipCodeId}`, {
                method: 'DELETE',
                headers: {
                    [csrfHeader]: csrfToken
                }
            }).then(() => {
                fetchZipCodes();
            });
        }
    });

    fetchZipCodes();
});