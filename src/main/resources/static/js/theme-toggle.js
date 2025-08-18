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

/**
 * This script handles theme toggling using Bootstrap's recommended data-bs-theme attribute.
 * It persists the user's choice in localStorage and defaults to their OS preference.
 * It also handles the "copy to clipboard" functionality for buttons.
 */

// Self-invoking function to encapsulate theme logic and avoid polluting the global scope
(() => {
    const themeToggleButton = document.getElementById('theme-toggle-btn');
    const themeToggleIcon = document.getElementById('theme-toggle-icon');

    // Icons for the toggle button
    const sunIcon = 'bi-sun-fill';
    const moonIcon = 'bi-moon-stars-fill';

    const getStoredTheme = () => localStorage.getItem('theme');
    const setStoredTheme = theme => localStorage.setItem('theme', theme);

    const getPreferredTheme = () => {
        const storedTheme = getStoredTheme();
        if (storedTheme) {
            return storedTheme;
        }
        // Fallback to user's OS preference
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    };

    const setTheme = theme => {
        document.documentElement.setAttribute('data-bs-theme', theme);
        // Update the icon to reflect the new theme
        if (themeToggleIcon) {
            themeToggleIcon.className = 'bi ' + (theme === 'dark' ? sunIcon : moonIcon);
        }
    };

    // Set the initial theme and icon on page load
    const initialTheme = getPreferredTheme();
    setTheme(initialTheme);

    // Add a listener to the button to toggle the theme on user interaction
    if (themeToggleButton) {
        themeToggleButton.addEventListener('click', () => {
            const newTheme = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
            setStoredTheme(newTheme);
            setTheme(newTheme);
        });
    }

    // Add copy-to-clipboard functionality for buttons with .copy-btn class
    document.querySelectorAll('.copy-btn').forEach(button => {
        button.addEventListener('click', () => {
            const textToCopy = button.getAttribute('data-copy-text');
            navigator.clipboard.writeText(textToCopy).then(() => {
                const originalText = button.textContent;
                const originalIcon = button.innerHTML;
                button.innerHTML = `<i class="bi bi-check-lg"></i> Copied!`;
                button.classList.add('btn-success');
                button.classList.remove('btn-outline-secondary');

                setTimeout(() => {
                    button.innerHTML = originalIcon;
                    button.classList.remove('btn-success');
                    button.classList.add('btn-outline-secondary');
                }, 2000); // Revert back after 2 seconds

            }).catch(err => {
                console.error('Failed to copy text: ', err);
                alert('Failed to copy text.');
            });
        });
    });
// --- Client-Side Date Formatting ---

    // This function finds all elements with the class 'local-datetime' and a 'data-timestamp' attribute.
    // It then reformats the timestamp into the user's local timezone using the browser's Intl API.
    // This provides a better user experience than showing all times in UTC.
    function formatLocalDates() {
        document.querySelectorAll('.local-datetime').forEach(el => {
            const timestamp = parseInt(el.dataset.timestamp, 10);
            if (isNaN(timestamp)) {
                console.error('Could not parse timestamp from data attribute:', el.dataset.timestamp);
                return; // The original server-rendered text will remain.
            }

            // Timestamps from the server are in seconds, convert to milliseconds for the JS Date object.
            const date = new Date(timestamp * 1000);

            // Use Intl.DateTimeFormat for robust, locale-aware formatting. It automatically uses the browser's timezone.
            const options = {
                year: 'numeric', month: 'long', day: 'numeric',
                hour: 'numeric', minute: 'numeric', second: 'numeric', timeZoneName: 'short'
            };
            el.textContent = new Intl.DateTimeFormat(undefined, options).format(date);
        });
    }

    // It's crucial to wait for the DOM to be fully loaded before trying to manipulate it.
    // This ensures that all the elements we want to format are available.
    document.addEventListener('DOMContentLoaded', formatLocalDates);
})();
