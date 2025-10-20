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

// Why: Reads the product data from the non-executable JSON block in the HTML.
// This is a CSP-compliant way to pass data from the backend to the frontend,
// resolving the inline script error.
const productsDataElement = document.getElementById('products-data');
const productsData = productsDataElement ? JSON.parse(productsDataElement.textContent.trim()) : [];

/**
 * schedules.js 
 * This script handles all client-side logic for the Powerwall Schedules page.
 * - Fetches and displays schedules from the API.
 * - Handles creation, updating, and deletion of schedules via a modal form.
 * - Provides user feedback (loading states, alerts).
 */
document.addEventListener('DOMContentLoaded', () => {
    // --- DOM Element References ---
    const scheduleList = document.getElementById('schedule-list');
    const loadingSpinner = document.getElementById('loading-spinner');
    const emptyState = document.getElementById('empty-state');
    const cardTemplate = document.getElementById('schedule-card-template');

    const importBtn = document.getElementById('import-btn');
    const importFileInput = document.getElementById('import-file-input');

    // Modal and Form Elements
    const scheduleModal = new bootstrap.Modal(document.getElementById('scheduleModal'));
    const scheduleModalEl = document.getElementById('scheduleModal');
    const scheduleForm = document.getElementById('schedule-form');
    const modalTitle = document.getElementById('scheduleModalLabel');
    const scheduleIdInput = document.getElementById('scheduleId');
    const daysOfWeekContainer = document.getElementById('daysOfWeek');
    const startHourSelect = document.getElementById('startHour');
    const startMinuteSelect = document.getElementById('startMinute');
    const endHourSelect = document.getElementById('endHour');
    const endMinuteSelect = document.getElementById('endMinute');
    const startAmPmSelect = document.getElementById('startAmPm');
    const endAmPmSelect = document.getElementById('endAmPm');
    const onPeakSlider = document.getElementById('onPeakBackupPercentSlider');
    const onPeakInput = document.getElementById('onPeakBackupPercentInput');
    const offPeakSlider = document.getElementById('offPeakBackupPercentSlider');
    const offPeakInput = document.getElementById('offPeakBackupPercentInput');
    const onPeakWarning = document.getElementById('onPeakWarning');
    const offPeakWarning = document.getElementById('offPeakWarning');
    const scheduleLogicWarning = document.getElementById('scheduleLogicWarning');

    // Delete Confirmation Modal Elements
    const deleteConfirmModalEl = document.getElementById('deleteConfirmModal');
    const deleteConfirmModal = new bootstrap.Modal(deleteConfirmModalEl);
    const scheduleNameToDeleteEl = document.getElementById('schedule-name-to-delete');
    const confirmDeleteBtn = document.getElementById('confirm-delete-btn');

    const scheduleTypeSelect = document.getElementById('scheduleType');
    const weatherAwareFields = document.getElementById('weather-aware-fields');
    const weatherAwareOptions = document.getElementById('weather-aware-options');
    const weatherScalingFactorSlider = document.getElementById('weatherScalingFactor');
    const weatherScalingFactorInput = document.getElementById('weatherScalingFactorInput');
    const sunshinePercentageSlider = document.getElementById('sunshinePercentage');
    const sunshinePercentageInput = document.getElementById('sunshinePercentageInput');
    const simulatedSunshineEl = document.getElementById('simulatedSunshine');
    const predictedChargeTargetEl = document.getElementById('predictedChargeTarget');

    scheduleTypeSelect.addEventListener('change', function () {
        const isWeatherAware = this.value === 'WEATHER_AWARE';
        weatherAwareFields.classList.toggle('d-none', !isWeatherAware);
        weatherAwareOptions.classList.toggle('d-none', !isWeatherAware);
        if (isWeatherAware) {
            updatePrediction();
        }
    });

    
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
    const API_BASE_URL = '/api/schedules';
    const toastContainer = document.querySelector('.toast-container');
    const toastTemplate = document.getElementById('toast-template');
    let currentScheduleForEdit = null; // To hold the full schedule object during an edit operation

    // Create a lookup map from the products data loaded from the data island
    const productsMap = new Map();
    productsData.forEach(p => {
        if (p && p.energySiteId) { // Defensively check for product and its ID
            productsMap.set(p.energySiteId.toString(), p);
        }
    });

    // --- Helper Functions ---

    /**
     * Sanitizes a string to prevent XSS by escaping HTML characters.
     * @param {string} unsafe - The potentially unsafe string.
     * @returns {string} A safe string for embedding in HTML.
     */
    const escapeHtml = (unsafe) => {
        if (typeof unsafe !== 'string') return '';
        return unsafe
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    };

    const formatApiError = async (response, defaultMessage) => {
        let message = defaultMessage;
        try {
            const errorData = await response.json();
            message = errorData.message || defaultMessage;
            if (errorData.validationErrors) {
                // Security: Escape each error to prevent XSS if the server ever returns user-input in validation messages.
                const errorList = errorData.validationErrors.map(err => `<li>${escapeHtml(err)}</li>`).join('');
                message += `<ul class="text-start mt-2 mb-0">${errorList}</ul>`;
            }
        } catch (e) {
            // Error response was not JSON, stick with the default message.
        }
        return message;
    };
    /**
     * Displays a Bootstrap toast notification.
     * @param {string} message - The message to display.
     * @param {string} type - The toast type ('success', 'danger', 'info').
     */
    const showToast = (message, type = 'info') => {
        const toastEl = toastTemplate.content.cloneNode(true).querySelector('.toast');
        const toastBody = toastEl.querySelector('.toast-body');
        const toastHeader = toastEl.querySelector('.toast-header');

        toastBody.innerHTML = message; // Use innerHTML to support lists in validation errors

        // Customize appearance based on type
        const icon = toastHeader.querySelector('i');
        if (type === 'success') {
            toastHeader.classList.add('text-bg-success');
            icon.className = 'bi bi-check-circle-fill me-2';
        } else if (type === 'danger') {
            toastHeader.classList.add('text-bg-danger');
            icon.className = 'bi bi-exclamation-triangle-fill me-2';
        } else {
            icon.className = 'bi bi-info-circle-fill me-2';
        }

        toastContainer.appendChild(toastEl);
        const toast = new bootstrap.Toast(toastEl, { delay: 5000 });
        toast.show();

        // Clean up the DOM after the toast is hidden
        toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
    };

    /**
     * Formats a time string (HH:mm) into a 12-hour format with AM/PM.
     * @param {string} timeString - The time string to format.
     * @returns {string} - The formatted time.
     */
    const formatTime = (timeString) => {
        if (!timeString) return '';
        const [hour, minute] = timeString.split(':');
        const date = new Date(0, 0, 0, hour, minute);
        return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
    };

    /**
     * Converts 12-hour format time from the UI to 24-hour format for the API.
     * @param {string} hour - The hour (1-12).
     * @param {string} minute - The minute.
     * @param {string} ampm - 'AM' or 'PM'.
     * @returns {string} - Time in HH:mm format.
     */
    const convertTo24Hour = (hour, minute, ampm) => {
        let h = parseInt(hour, 10);
        if (ampm === 'PM' && h < 12) h += 12;
        if (ampm === 'AM' && h === 12) h = 0; // Midnight case
        return `${h.toString().padStart(2, '0')}:${minute}`;
    };

    /**
     * Converts 24-hour format time from the API to 12-hour format for the UI.
     * @param {string} timeString - Time in HH:mm format.
     * @returns {object} - An object with hour, minute, and ampm properties.
     */
    const convertTo12Hour = (timeString) => {
        if (!timeString) return { hour: '12', minute: '00', ampm: 'AM' };
        const [hour24, minute] = timeString.split(':');
        let h = parseInt(hour24, 10);
        const ampm = h >= 12 ? 'PM' : 'AM';
        h = h % 12;
        h = h ? h : 12; // The hour '0' should be '12'
        return { hour: h.toString(), minute, ampm };
    };

    // --- Core Application Logic ---

    /**
     * Fetches all schedules from the API and renders them.
     */
    const loadSchedules = async () => {
        loadingSpinner.classList.remove('d-none');
        emptyState.classList.add('d-none');
        scheduleList.innerHTML = '';

        try {
            const response = await fetch(API_BASE_URL);
            if (!response.ok) throw new Error('Failed to load schedules.');

            const schedules = await response.json();

            if (schedules.length === 0) {
                emptyState.classList.remove('d-none');
            } else {
                schedules.forEach(renderScheduleCard);
            }
        } catch (error) {
            showToast(error.message, 'danger');
        } finally {
            loadingSpinner.classList.add('d-none');
        }
    };

    /**
     * Renders a single schedule card and appends it to the list.
     * @param {object} schedule - The schedule data object.
     */
    const renderScheduleCard = (schedule) => {
        const cardFragment = cardTemplate.content.cloneNode(true);
        const cardEl = cardFragment.querySelector('.col');

        // Populate card fields
                const nameEl = cardEl.querySelector('[data-field="name"]');
        // Add a visual indicator for the schedule type
        if (schedule.scheduleType === 'WEATHER_AWARE') {
            nameEl.innerHTML = `<i class="bi bi-cloud-sun-fill me-2 text-primary" title="Weather-Aware Schedule"></i> ${escapeHtml(schedule.name)}`;
        } else {
            nameEl.innerHTML = `<i class="bi bi-clock-history me-2 text-secondary" title="Basic Time-Based Schedule"></i> ${escapeHtml(schedule.name)}`;
        }
        cardEl.querySelector('[data-field="description"]').textContent = schedule.description || 'No description';
        cardEl.querySelector('[data-field="time"]').textContent = `${formatTime(schedule.startTime)} - ${formatTime(schedule.endTime)}`;
        const days = schedule.daysOfWeek || [];
        cardEl.querySelector('[data-field="days"]').textContent = days.map(d => d.substring(0, 3)).join(', ');
        const onPeakEl = cardEl.querySelector('[data-field="percent"]');
        const offPeakEl = cardEl.querySelector('[data-field="offPeakPercent"]');

        onPeakEl.textContent = `${schedule.onPeakBackupPercent}%`;
        offPeakEl.textContent = `${schedule.offPeakBackupPercent}%`;

        if (schedule.overriddenByWeather) {
            if (schedule.onPeakBackupPercent !== schedule.permanentOnPeakBackupPercent) {
                onPeakEl.innerHTML += ` <small class="text-muted">(Normally ${schedule.permanentOnPeakBackupPercent}%)</small>`;
            }
            if (schedule.offPeakBackupPercent !== schedule.permanentOffPeakBackupPercent) {
                offPeakEl.innerHTML += ` <small class="text-muted">(Normally ${schedule.permanentOffPeakBackupPercent}%)</small>`;
            }
        }
        const siteName = document.querySelector(`#energySiteId option[value="${schedule.energySiteId}"]`)?.textContent;
        cardEl.querySelector('[data-field="siteName"]').textContent = siteName || `Site ID: ${schedule.energySiteId}`;

        // Add battery level to the card using the pre-loaded products map
        if (schedule && schedule.energySiteId) {
            const product = productsMap.get(schedule.energySiteId.toString());
            if (product && typeof product.percentageCharged === 'number') {
                const batteryLevelEl = cardEl.querySelector('[data-field="siteBatteryLevel"]');
                batteryLevelEl.textContent = `${product.percentageCharged.toFixed(0)}% 🔋`;

                // Add color based on charge
                if (product.percentageCharged > 70) {
                    batteryLevelEl.classList.add('text-success');
                } else if (product.percentageCharged > 30) {
                    batteryLevelEl.classList.add('text-warning');
                } else {
                    batteryLevelEl.classList.add('text-danger');
                }
            }
        }

        // Add weather override indicator
        if (schedule.overriddenByWeather) {
            const backupTitleEl = cardEl.querySelector('[data-field="backup-title"]');
            if (backupTitleEl) {
                backupTitleEl.innerHTML += ' <i class="bi bi-cloud-sun-fill text-primary" title="Temporarily adjusted by weather forecast"></i>';
            }
        }

        // Set up toggle switch
        const toggle = cardEl.querySelector('[data-action="toggle"]');
        toggle.checked = schedule.enabled;
        // The scheduleGroupId is used to identify the entire period
        toggle.addEventListener('change', () => handleToggle(schedule.scheduleGroupId, toggle.checked));

        // Set up action buttons
        cardEl.querySelector('[data-action="edit"]').addEventListener('click', () => handleEdit(schedule));
        cardEl.querySelector('[data-action="delete"]').addEventListener('click', () => handleDelete(schedule));
        const reconciliationStatusEl = cardEl.querySelector('[data-field="reconciliationStatus"]');
        if (schedule.reconciliationMode === 'CONTINUOUS') {
            reconciliationStatusEl.innerHTML = `<i class="bi bi-arrow-repeat text-primary"></i> Continuous Correction`;
            reconciliationStatusEl.title = 'The system will always enforce this schedule, overriding manual changes.';
        } else if (schedule.reconciliationMode === 'STARTUP_ONLY') {
            reconciliationStatusEl.innerHTML = `<i class="bi bi-box-arrow-in-right text-secondary"></i> Startup Correction`;
            reconciliationStatusEl.title = 'The system corrects state on startup, then allows manual changes.';
        } else {
            reconciliationStatusEl.innerHTML = ''; // Or some default
        }
        scheduleList.appendChild(cardFragment);
    };

    // --- Event Handlers ---

    /**
     * Handles the form submission for both creating and updating schedules.
     */
    scheduleForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const scheduleId = scheduleIdInput.value;
        const method = scheduleId ? 'PUT' : 'POST';
        const url = scheduleId ? `${API_BASE_URL}/${scheduleId}` : API_BASE_URL;

        const selectedDays = Array.from(daysOfWeekContainer.querySelectorAll('input:checked')).map(cb => cb.value);

        const body = {
            id: scheduleId || undefined,
            energySiteId: document.getElementById('energySiteId').value,
            name: document.getElementById('name').value,
            description: document.getElementById('description').value,
            daysOfWeek: selectedDays,
            startTime: convertTo24Hour(startHourSelect.value, startMinuteSelect.value, startAmPmSelect.value),
            endTime: convertTo24Hour(endHourSelect.value, endMinuteSelect.value, endAmPmSelect.value),
            timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, // Use browser's timezone
            onPeakBackupPercent: parseInt(onPeakInput.value, 10),
            offPeakBackupPercent: parseInt(offPeakInput.value, 10),
            reconciliationMode: document.querySelector('input[name="reconciliationMode"]:checked').value,
            scheduleType: scheduleTypeSelect.value,
            weatherScalingFactor: parseInt(weatherScalingFactorInput.value, 10)
        };

        if (method === 'POST') {
            // New schedules are always enabled by default.
            body.enabled = true;
        } else {
            // For updates, we must send the original 'enabled' status to satisfy backend validation.
            // The backend service is configured to ignore this field, preserving the toggle's state,
            // but the DTO validation requires it to be present.
            if (currentScheduleForEdit) {
                body.enabled = currentScheduleForEdit.enabled;
            }
        }

        const headers = {
            'Content-Type': 'application/json'
        };
        headers[csrfHeader] = csrfToken;

        try {
            const response = await fetch(url, {
                method,
                headers: headers,
                body: JSON.stringify(body)
            });

            if (!response.ok) {
                const message = await formatApiError(response, 'Failed to save schedule.');
                throw new Error(message);
            }

            scheduleModal.hide();
            showToast(`Schedule successfully ${scheduleId ? 'updated' : 'created'}!`, 'success');
            loadSchedules();
        } catch (error) {
            showToast(error.message, 'danger');
        }
    });

    /**
     * Prepares and shows the modal for editing an existing schedule.
     * @param {object} schedule - The schedule data to populate the form with.
     */
    const handleEdit = (schedule) => {
        currentScheduleForEdit = schedule; // Store the object for the submit handler
        modalTitle.textContent = 'Edit Schedule';
        scheduleForm.reset();
        scheduleIdInput.value = schedule.scheduleGroupId;

        document.getElementById('energySiteId').value = schedule.energySiteId;
        document.getElementById('name').value = schedule.name;
        document.getElementById('description').value = schedule.description;

        const start = convertTo12Hour(schedule.startTime);
        startHourSelect.value = start.hour;
        startMinuteSelect.value = start.minute;
        startAmPmSelect.value = start.ampm;

        const end = convertTo12Hour(schedule.endTime);
        endHourSelect.value = end.hour;
        endMinuteSelect.value = end.minute;
        endAmPmSelect.value = end.ampm;

        const onPeakPercent = schedule.permanentOnPeakBackupPercent || 20;
        onPeakSlider.value = onPeakPercent;
        onPeakInput.value = onPeakPercent;
        updateWarnings('onPeak', onPeakPercent);

        const offPeakPercent = schedule.permanentOffPeakBackupPercent || 80;
        offPeakSlider.value = offPeakPercent;
        offPeakInput.value = offPeakPercent;
        updateWarnings('offPeak', offPeakPercent);
        validateBackupLevels();
        const days = schedule.daysOfWeek || [];
        // Check the correct day checkboxes
        daysOfWeekContainer.querySelectorAll('input').forEach(cb => {
            cb.checked = days.includes(cb.value);
        });

        document.querySelector(`input[name="reconciliationMode"][value="${schedule.reconciliationMode}"]`).checked = true;

        // Handle weather-aware fields
        const scheduleType = schedule.scheduleType || 'BASIC';
        scheduleTypeSelect.value = scheduleType;
        const isWeatherAware = scheduleType === 'WEATHER_AWARE';
        weatherAwareFields.classList.toggle('d-none', !isWeatherAware);
        weatherAwareOptions.classList.toggle('d-none', !isWeatherAware);

        if (isWeatherAware) {
            const scalingFactor = schedule.weatherScalingFactor !== null ? schedule.weatherScalingFactor : 100;
            weatherScalingFactorSlider.value = scalingFactor;
            weatherScalingFactorInput.value = scalingFactor;
            updatePrediction();
        }

        scheduleModal.show();
    };

    /**
     * Prepares and shows the delete confirmation modal.
     * @param {object} schedule - The schedule to be deleted.
     */
    const handleDelete = (schedule) => {
        // Set the schedule name in the modal body
        scheduleNameToDeleteEl.textContent = schedule.name;
        // Store the ID on the confirm button to be used by the click handler
        confirmDeleteBtn.dataset.scheduleId = schedule.scheduleGroupId;
        deleteConfirmModal.show();
    };

    /**
     * Handles the actual deletion after confirmation in the modal.
     */
    const executeDelete = async () => {
        const scheduleId = confirmDeleteBtn.dataset.scheduleId;
        if (!scheduleId) return;

        const headers = {};
        headers[csrfHeader] = csrfToken;

        try {
            const response = await fetch(`${API_BASE_URL}/${scheduleId}`, { method: 'DELETE', headers: headers });
            if (!response.ok) {
                const message = await formatApiError(response, 'Failed to delete schedule.');
                throw new Error(message);
            }

            showToast('Schedule deleted successfully.', 'success');
            loadSchedules();
        } catch (error) {
            showToast(error.message, 'danger');
        } finally {
            deleteConfirmModal.hide();
            // Clean up the dataset to prevent accidental re-deletion
            delete confirmDeleteBtn.dataset.scheduleId;
        }
    };

    /**
     * Handles enabling or disabling a schedule using the toggle switch.
     * @param {string} scheduleId - The ID of the schedule to update.
     * @param {boolean} isEnabled - The new enabled state.
     */
    const handleToggle = async (scheduleId, isEnabled) => {
        try {
            const headers = {
                'Content-Type': 'application/json'
            };
            headers[csrfHeader] = csrfToken;

            const response = await fetch(`${API_BASE_URL}/${scheduleId}/toggle`, {
                method: 'PATCH',
                headers: headers,
                body: JSON.stringify({ enabled: isEnabled })
            });

            if (!response.ok) {
                const message = await formatApiError(response, 'Failed to update schedule status.');
                throw new Error(message);
            }

            showToast(`Schedule ${isEnabled ? 'enabled' : 'disabled'}.`, 'success');
        } catch (error) {
            showToast(error.message, 'danger');
            loadSchedules(); // Reload to revert the toggle on failure
        }
    };

    /**
     * Initializes the modal form for creating a new schedule.
     */
    const setupNewScheduleForm = () => {
        currentScheduleForEdit = null; // Ensure we're not using old data
        modalTitle.textContent = 'New Schedule';
        scheduleForm.reset(); // Resets all form fields
        scheduleIdInput.value = '';
        onPeakSlider.value = 20;
        onPeakInput.value = 20;
        updateWarnings('onPeak', 20);
        offPeakSlider.value = 80;
        offPeakInput.value = 80;
        updateWarnings('offPeak', 80);
        validateBackupLevels();
        // Set user-friendly default times
        startHourSelect.value = '7';
        startMinuteSelect.value = '00';
        startAmPmSelect.value = 'AM';

        endHourSelect.value = '9';
        endMinuteSelect.value = '00';
        endAmPmSelect.value = 'PM';
        document.getElementById('modeContinuous').checked = true;
        scheduleTypeSelect.value = 'BASIC';
        weatherAwareFields.classList.add('d-none');
    };

    // --- Initialization ---

    /**
     * Populates the time picker dropdowns with hours and minutes.
     */
    const populateTimePickers = () => {
        // Populate hours (0-23)
        for (let i = 1; i <= 12; i++) {
            const hour = i.toString();
            startHourSelect.add(new Option(hour, hour));
            endHourSelect.add(new Option(hour, hour));
        }
        // Populate minutes with 15-minute intervals for a cleaner UI
        const minuteIntervals = ['00', '15', '30', '45'];
        minuteIntervals.forEach(minute => {
            startMinuteSelect.add(new Option(minute, minute));
            endMinuteSelect.add(new Option(minute, minute));
        });
        // Populate AM/PM
        startAmPmSelect.add(new Option('AM', 'AM'));
        startAmPmSelect.add(new Option('PM', 'PM'));
        endAmPmSelect.add(new Option('AM', 'AM'));
        endAmPmSelect.add(new Option('PM', 'PM'));
    };

    // Populate day of week checkboxes
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
    days.forEach(day => {
        const div = document.createElement('div');
        div.classList.add('form-check');
        div.innerHTML = `
            <input class="form-check-input" type="checkbox" value="${day}" id="day-${day}">
            <label class="form-check-label" for="day-${day}">${day.substring(0, 3)}</label>
        `;
        daysOfWeekContainer.appendChild(div);
    });

    const updateWarnings = (type, value) => {
        const inputEl = document.getElementById(`${type}BackupPercentInput`);
        const warningEl = document.getElementById(`${type}Warning`);
        const val = parseInt(value, 10);

        if (val < 20) {
            inputEl.classList.add('text-danger');
            warningEl.classList.remove('d-none');
        } else {
            inputEl.classList.remove('text-danger');
            warningEl.classList.add('d-none');
        }
    };
    const validateBackupLevels = () => {
        const onPeak = parseInt(onPeakSlider.value, 10);
        const offPeak = parseInt(offPeakSlider.value, 10);

        if (onPeak >= offPeak) {
            scheduleLogicWarning.classList.remove('d-none');
        } else {
            scheduleLogicWarning.classList.add('d-none');
        }
    };

    const updatePrediction = () => {
        const baseBackupPercent = parseInt(offPeakInput.value, 10);
        const scalingFactor = parseInt(weatherScalingFactorInput.value, 10);
        const sunshinePercentage = parseInt(sunshinePercentageInput.value, 10);

        const solarShortfall = 100 - sunshinePercentage;
        const adjustment = (solarShortfall / 100.0) * (100.0 - baseBackupPercent) * (scalingFactor / 100.0);
        const adjustedChargeTarget = baseBackupPercent + adjustment;
        const finalChargeTarget = Math.min(90, Math.round(adjustedChargeTarget));

        predictedChargeTargetEl.textContent = `${finalChargeTarget}%`;
    };

    const syncWeatherInputs = (source, target) => {
        target.value = source.value;
        updatePrediction();
    };

    const syncBackupInputs = (source, target, type) => {
        target.value = source.value;
        updateWarnings(type, source.value);
        validateBackupLevels();
    };

    // Listeners for the backup percentage sliders
    onPeakSlider.addEventListener('input', () => syncBackupInputs(onPeakSlider, onPeakInput, 'onPeak'));
    onPeakInput.addEventListener('input', () => syncBackupInputs(onPeakInput, onPeakSlider, 'onPeak'));

    offPeakSlider.addEventListener('input', () => syncBackupInputs(offPeakSlider, offPeakInput, 'offPeak'));
    offPeakInput.addEventListener('input', () => syncBackupInputs(offPeakInput, offPeakSlider, 'offPeak'));

    // Listeners for weather simulation
    weatherScalingFactorSlider.addEventListener('input', () => syncWeatherInputs(weatherScalingFactorSlider, weatherScalingFactorInput));
    weatherScalingFactorInput.addEventListener('input', () => syncWeatherInputs(weatherScalingFactorInput, weatherScalingFactorSlider));
    sunshinePercentageSlider.addEventListener('input', () => {
        syncWeatherInputs(sunshinePercentageSlider, sunshinePercentageInput);
        simulatedSunshineEl.textContent = `${sunshinePercentageSlider.value}%`;
    });
    sunshinePercentageInput.addEventListener('input', () => {
        syncWeatherInputs(sunshinePercentageInput, sunshinePercentageSlider);
        simulatedSunshineEl.textContent = `${sunshinePercentageInput.value}%`;
    });

    offPeakSlider.addEventListener('input', updatePrediction);
    offPeakInput.addEventListener('input', updatePrediction);

    // Add listener for the final delete confirmation button
    confirmDeleteBtn.addEventListener('click', executeDelete);

    // Reset form when modal is about to be shown for a "new" schedule
    scheduleModalEl.addEventListener('show.bs.modal', (event) => {
        // The 'new-schedule-btn' is the only button that opens the modal directly.
        // The 'edit' button calls handleEdit(), which configures the modal itself.
        if (event.relatedTarget?.id === 'new-schedule-btn') {
            setupNewScheduleForm();
        }
    });

    /**
     * Handles the click on the import button by triggering the hidden file input.
     */
    importBtn.addEventListener('click', () => {
        importFileInput.click();
    });

    /**
     * Handles the file selection for import, sending the file to the backend.
     */
    importFileInput.addEventListener('change', async (event) => {
        const file = event.target.files[0];
        if (!file) return; // No file selected

        const formData = new FormData();
        formData.append('file', file);

        const headers = {};
        headers[csrfHeader] = csrfToken;

        try {
            const response = await fetch(`${API_BASE_URL}/import`, {
                method: 'POST',
                headers: headers,
                body: formData
            });

            const responseText = await response.text(); // Get text for both success and error
            if (!response.ok) throw new Error(responseText);

            showToast(responseText, 'success');
            loadSchedules();
        } catch (error) {
            showToast(error.message || 'An unexpected error occurred during import.', 'danger');
        } finally {
            // Reset the file input so the 'change' event fires even if the same file is selected again
            importFileInput.value = '';
        }
    });

    populateTimePickers();
    // Initial load of schedules when the page is ready
    loadSchedules();
});