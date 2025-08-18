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
 * This script handles the SVG-based energy flow animation.
 * It depends on energy-flow-calculator.js, which must be included first.
 */
document.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById('energy-flow-container');
    if (!container) return;

    const siteId = container.dataset.siteId;
    const refreshInterval = 5000; // 5 seconds
    const POWER_THRESHOLD = 50;   // Watts to consider a flow active

    /**
     * Updates the text content of an SVG text element.
     */
    const updateText = (id, value, unit = 'kW') => {
        const element = document.getElementById(id);
        if (element) {
            const displayValue = Math.abs(value / 1000).toFixed(2);
            element.textContent = `${displayValue} ${unit}`;
        }
    };

    /**
     * Updates the percentage text content of an SVG text element.
     */
    const updatePercentage = (id, value) => {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = `${value.toFixed(1)}%`;
        }
    };

    /**
     * Activates or deactivates an SVG path animation by toggling a CSS class.
     */
    const updateFlowState = (id, isActive) => {
        const element = document.getElementById(id);
        if (element) {
            element.classList.toggle('active', isActive);
        }
    };

    /**
     * The main function to fetch data and update the UI.
     */
    const fetchData = async () => {
        try {
            const response = await fetch(`/api/energy/sites/${siteId}/live_status`);
            if (!response.ok) {
                console.error('Failed to fetch live status:', response.statusText);
                return;
            }
            const liveStatus = await response.json();

            // Update text values in the SVG
            updateText('grid-value', liveStatus.grid_power);
            updateText('home-value', liveStatus.load_power);
            updateText('solar-value', liveStatus.solar_power);
            updateText('powerwall-value', liveStatus.battery_power);
            updatePercentage('powerwall-percentage', liveStatus.percentage_charged);

            // Calculate the detailed power flows using the shared calculator.
            const flows = calculatePowerFlows(liveStatus);

            // Update flow line animations based on the accurate flow calculations.
            updateFlowState('solar-to-house', flows.from_solar_to_home > POWER_THRESHOLD);
            updateFlowState('solar-to-powerwall', flows.from_solar_to_battery > POWER_THRESHOLD);
            updateFlowState('grid-to-house', flows.from_grid_to_home > POWER_THRESHOLD);
            updateFlowState('grid-to-powerwall', flows.from_grid_to_battery > POWER_THRESHOLD);
            updateFlowState('powerwall-to-house', flows.from_battery_to_home > POWER_THRESHOLD);

            // Grid export is a combination of surplus from solar and battery.
            const grid_export_total = flows.from_solar_to_grid + flows.from_battery_to_grid;
            updateFlowState('house-to-grid', grid_export_total > POWER_THRESHOLD);

        } catch (error) {
            console.error('Error fetching or processing energy data:', error);
        }
    };

    setInterval(fetchData, refreshInterval);
    fetchData(); // Initial fetch for immediate data
});
