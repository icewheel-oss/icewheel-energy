/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

/*
 * Site-wide application-specific JavaScript.
 * This file contains handlers for various UI elements across the application.
 * It is designed to be included on pages that use it.
 */
document.addEventListener('DOMContentLoaded', () => {

    // Handler for the "Go Back" button, typically on the error page.
    const goBackBtn = document.getElementById('go-back-btn');
    if (goBackBtn) {
        goBackBtn.addEventListener('click', () => {
            window.history.back();
        });
    }

    // Generic handler for all copy buttons.
    // It looks for any button with the class 'copy-btn' and a 'data-copy-text' attribute.
    const copyBtns = document.querySelectorAll('.copy-btn');
    copyBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const textToCopy = btn.getAttribute('data-copy-text');
            if (textToCopy) {
                navigator.clipboard.writeText(textToCopy).then(() => {
                    const originalHtml = btn.innerHTML;
                    btn.innerHTML = '<i class="bi bi-check-lg me-2"></i>Copied!';
                    btn.disabled = true;
                    setTimeout(() => {
                        btn.innerHTML = originalHtml;
                        btn.disabled = false;
                    }, 2000);
                });
            }
        });
    });

    // Handler for the "Download" button on the application-info page.
    const downloadKeyBtn = document.getElementById('download-btn');
    const publicKeyPemEl = document.getElementById('publicKeyPem');
    if (downloadKeyBtn && publicKeyPemEl) {
        const publicKeyPem = publicKeyPemEl.textContent;
        downloadKeyBtn.addEventListener('click', (e) => {
            const blob = new Blob([publicKeyPem], { type: 'application/x-pem-file' });
            const url = URL.createObjectURL(blob);
            e.currentTarget.href = url;
            e.currentTarget.download = 'public-key.pem';
        });
    }
});