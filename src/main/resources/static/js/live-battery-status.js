/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This script provides live updates for battery status indicators across the application.
 * It periodically fetches live data for any element with a 'data-site-id' attribute
 * and updates the child elements that have 'data-field' attributes.
 */
document.addEventListener('DOMContentLoaded', () => {
    const SITES_POLL_INTERVAL = 15000; // 15 seconds

    const updateAllStatuses = () => {
        const siteContainers = document.querySelectorAll('[data-site-id]');
        if (siteContainers.length === 0) {
            return; // No sites to update on this page
        }
        siteContainers.forEach(updateStatusForSite);
    };

    const updateStatusForSite = async (siteContainer) => {
        const siteId = siteContainer.dataset.siteId;
        if (!siteId) return;

        try {
            const response = await fetch(`/api/energy/sites/${siteId}/live_status`);
            if (!response.ok) {
                console.error(`Failed to fetch live status for site ${siteId}: ${response.statusText}`);
                return;
            }
            const data = await response.json();

            // --- Update Percentage Charged (Products Page) ---
            const percentageChargedEl = siteContainer.querySelector('[data-field="percentage-charged"]');
            if (percentageChargedEl) {
                percentageChargedEl.textContent = `${data.percentage_charged.toFixed(1)}%`;
            }

            // --- Update Percentage Charged Badge (Schedules Page Top Bar) ---
            const percentageChargedBadgeEl = siteContainer.querySelector('[data-field="percentage-charged-badge"]');
            if (percentageChargedBadgeEl) {
                percentageChargedBadgeEl.textContent = `${Math.round(data.percentage_charged)}%`;
                percentageChargedBadgeEl.className = 'badge rounded-pill'; // Reset classes
                if (data.percentage_charged > 70) {
                    percentageChargedBadgeEl.classList.add('bg-success-subtle', 'text-success-emphasis');
                } else if (data.percentage_charged > 30) {
                    percentageChargedBadgeEl.classList.add('bg-warning-subtle', 'text-warning-emphasis');
                } else {
                    percentageChargedBadgeEl.classList.add('bg-danger-subtle', 'text-danger-emphasis');
                }
            }
            
            // --- Update Battery Level in Schedule Cards ---
            const siteBatteryLevelEl = siteContainer.querySelector('[data-field="siteBatteryLevel"]');
            if (siteBatteryLevelEl) {
                siteBatteryLevelEl.textContent = `(${Math.round(data.percentage_charged)}%)`;
                siteBatteryLevelEl.classList.remove('text-success', 'text-warning', 'text-danger');
                if (data.percentage_charged > 70) {
                    siteBatteryLevelEl.classList.add('text-success');
                } else if (data.percentage_charged > 30) {
                    siteBatteryLevelEl.classList.add('text-warning');
                } else {
                    siteBatteryLevelEl.classList.add('text-danger');
                }
            }

            // --- Update Progress Bar (Products Page) ---
            const progressBarEl = siteContainer.querySelector('[data-field="percentage-charged-bar"]');
            if (progressBarEl) {
                progressBarEl.style.width = `${data.percentage_charged}%`;
                progressBarEl.parentElement.setAttribute('aria-valuenow', data.percentage_charged);
                progressBarEl.classList.remove('bg-success', 'bg-warning', 'bg-danger', 'progress-bar-striped', 'progress-bar-animated');
                if (data.percentage_charged > 70) {
                    progressBarEl.classList.add('bg-success');
                } else if (data.percentage_charged > 30) {
                    progressBarEl.classList.add('bg-warning');
                } else {
                    progressBarEl.classList.add('bg-danger');
                }
                if (data.battery_power < -50 || data.battery_power > 50) {
                    progressBarEl.classList.add('progress-bar-striped', 'progress-bar-animated');
                }
            }

            // --- Update Power Flow Status (Products Page) ---
            const powerFlowStatusEl = siteContainer.querySelector('[data-field="power-flow-status"]');
            if (powerFlowStatusEl) {
                const power = data.battery_power / 1000;
                let statusHtml = '';
                if (power > 0.05) { // Discharging
                    statusHtml = `<div class="d-flex align-items-center text-primary"><i class="bi bi-arrow-up-circle-fill fs-2 me-3"></i><div><div class="fw-bold">Discharging</div><div class="small text-muted">Supplying power to home</div></div></div>`;
                } else if (power < -0.05) { // Charging
                    statusHtml = `<div class="d-flex align-items-center text-success"><i class="bi bi-arrow-down-circle-fill fs-2 me-3"></i><div><div class="fw-bold">Charging</div><div class="small text-muted">Storing energy</div></div></div>`;
                } else { // Idle
                    statusHtml = `<div class="d-flex align-items-center text-secondary"><i class="bi bi-pause-circle-fill fs-2 me-3"></i><div><div class="fw-bold">Idle</div><div class="small text-muted">Battery is standing by</div></div></div>`;
                }
                const powerDisplay = powerFlowStatusEl.querySelector('[data-field="battery-power"]');
                powerFlowStatusEl.innerHTML = statusHtml;
                if(powerDisplay) {
                    powerDisplay.textContent = `${Math.abs(power).toFixed(2)} kW`;
                    powerFlowStatusEl.appendChild(powerDisplay);
                }
            }
            
            const batteryPowerEl = siteContainer.querySelector('[data-field="battery-power"]');
            if (batteryPowerEl && !powerFlowStatusEl) {
                 batteryPowerEl.textContent = `${Math.abs(data.battery_power / 1000).toFixed(2)} kW`;
            }

            // --- Update Energy Left (Products Page) ---
            const energyLeftEl = siteContainer.querySelector('[data-field="energy-left"]');
            if (energyLeftEl) {
                energyLeftEl.textContent = `${(data.energy_left / 1000).toFixed(1)} kWh`;
            }

        } catch (error) {
            console.error(`Error updating status for site ${siteId}:`, error);
        }
    };

    // Initial update for elements present on page load
    updateAllStatuses();

    // Listen for the custom event that fires after dynamic schedule cards are rendered
    document.addEventListener('schedulesRendered', () => {
        updateAllStatuses();
    });

    // Set up periodic updates
    setInterval(updateAllStatuses, SITES_POLL_INTERVAL);
});
