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

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.copy-btn').forEach(button => { // NOSONAR
        button.addEventListener('click', function () {
            const parentContainer = this.parentElement;
            const pre = parentContainer ? parentContainer.querySelector('pre') : null;

            if (!pre) {
                console.error('Could not find the <pre> element to copy from.');
                return;
            }

            const code = pre.querySelector('code');
            if (!code) {
                console.error('Could not find the <code> element to copy from.');
                return;
            }

            navigator.clipboard.writeText(code.innerText).then(() => {
                const originalIcon = this.innerHTML;
                this.innerHTML = '<i class="bi bi-check-lg"></i> Copied!';
                this.classList.add('btn-success');
                this.classList.remove('btn-outline-secondary');
                setTimeout(() => {
                    this.innerHTML = originalIcon;
                    this.classList.remove('btn-success');
                    this.classList.add('btn-outline-secondary');
                }, 2000);
            }).catch(err => {
                console.error('Failed to copy text: ', err);
            });
        });
    });

    /**
     * Finds all raw API response blocks and pretty-prints the JSON content for better readability.
     */
    document.querySelectorAll('#rawResponsesAccordion pre code').forEach(codeElement => { // NOSONAR
        try {
            const jsonText = codeElement.textContent.trim();
            // Why: Check if the text is not empty and looks like a JSON object before parsing.
            if (jsonText && jsonText.startsWith('{')) {
                const jsonObj = JSON.parse(jsonText);
                // Why: Beautify the JSON with 2-space indentation for readability.
                codeElement.textContent = JSON.stringify(jsonObj, null, 2);
            }
        } catch (e) {
            console.error('Could not parse and beautify JSON content. Displaying raw text.', e);
            // If parsing fails, the original text remains, which is a safe fallback.
        }
    });

    // Why: Initialize highlighting on all code blocks after they have been processed.
    hljs.highlightAll();
});