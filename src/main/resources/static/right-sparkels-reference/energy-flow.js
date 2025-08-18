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

document.addEventListener('DOMContentLoaded', () => {
    const energyFlowContainer = document.getElementById('energy-flow-container');
    if (!energyFlowContainer) {
        return;
    }

    const siteId = energyFlowContainer.dataset.siteId;
    const refreshInterval = 5000;

    const colorMap = {
        'bg-warning': 'var(--bs-warning)',
        'bg-danger': 'var(--bs-danger)',
        'bg-success': 'var(--bs-success)',
        'bg-info': 'var(--bs-info)',
        'bg-primary': 'var(--bs-primary)',
        'bg-purple': 'var(--bs-purple, #6f42c1)'
    };

    const createSparkles = (lineId, count) => {
        const line = document.getElementById(lineId);
        if (!line) return;
        line.innerHTML = ''; // Clear any old sparkles
        for (let i = 0; i < count; i++) {
            const sparkle = document.createElement('span');
            sparkle.className = 'sparkle';
            if (line.clientHeight > line.clientWidth) { // Vertical line
                sparkle.style.left = Math.random() * (line.clientWidth - 5) + 'px';
                sparkle.style.animationName = 'sparkle-flow-vertical';
                sparkle.style.animationDuration = (Math.random() * 1 + 1) + 's';
            } else { // Horizontal line
                sparkle.style.top = Math.random() * (line.clientHeight - 5) + 'px';
                sparkle.style.animationName = 'sparkle-flow-horizontal';
                sparkle.style.animationDuration = (Math.random() * 1 + 1) + 's';
            }
            sparkle.style.animationDelay = Math.random() * 2 + 's';
            line.appendChild(sparkle);
        }
    };

    // Initialize sparkles for each line
    createSparkles('line-solar-to-junction', 25);
    createSparkles('line-grid-to-junction', 30);
    createSparkles('line-junction-to-house', 30);
    createSparkles('line-junction-to-powerwall', 25);

    const updateEnergyValue = (selector, value, unit = 'kW', useAbs = false, precision = 2) => {
        const element = document.getElementById(selector);
        if (element) {
            const power = useAbs ? Math.abs(value) : value;
            const formattedValue = (power / 1000).toFixed(precision);
            const newText = `${formattedValue} ${unit}`;
            if (element.textContent !== newText) {
                element.style.opacity = '0.5';
                setTimeout(() => {
                    element.textContent = newText;
                    element.style.opacity = '1';
                }, 150);
            }
        }
    };

    const updatePercentageValue = (selector, value, unit = '%', precision = 1) => {
        const element = document.getElementById(selector);
        if (element) {
            const formattedValue = value.toFixed(precision);
            const newText = `${formattedValue} ${unit}`;
            if (element.textContent !== newText) {
                element.style.opacity = '0.5';
                setTimeout(() => {
                    element.textContent = newText;
                    element.style.opacity = '1';
                }, 150);
            }
        }
    };

    const updateFlowDescription = (selector, power, posText, negText, idleText) => {
        const element = document.getElementById(selector);
        if (element) {
            if (power > 50) element.textContent = posText;
            else if (power < -50) element.textContent = negText;
            else element.textContent = idleText;
        }
    };

    const updateFlowLine = (lineId, power, options) => {
        const {threshold = 50, reverseCondition, colors} = options;
        const line = document.getElementById(lineId);
        if (!line) return;

        const isActive = Math.abs(power) > threshold;
        line.classList.toggle('flow-active', isActive);

        const isReverse = reverseCondition ? reverseCondition(power) : false;
        line.classList.toggle('flow-reverse', isReverse);

        const colorName = isReverse ? colors.reverse : colors.forward;
        const colorValue = colorMap[colorName] || 'transparent';
        line.style.setProperty('--flow-color', colorValue);
    };

    const fetchData = async () => {
        try {
            const response = await fetch(`/api/energy/sites/${siteId}/live_status`);
            if (!response.ok) {
                return;
            }
            const liveStatus = await response.json();

            updateEnergyValue('solar-value', liveStatus.solar_power, 'kW', true);
            updateEnergyValue('grid-value', liveStatus.grid_power, 'kW', true);
            updateEnergyValue('home-value', liveStatus.load_power, 'kW', true);
            updateEnergyValue('powerwall-value', liveStatus.battery_power, 'kW', true);
            updatePercentageValue('powerwall-percentage', liveStatus.percentage_charged);
            updateEnergyValue('summary-solar', liveStatus.solar_power, 'kW', true);
            updateEnergyValue('summary-home', liveStatus.load_power, 'kW', true);
            updateEnergyValue('summary-grid', liveStatus.grid_power, 'kW', true);
            updateFlowDescription('grid-flow-text', liveStatus.grid_power, 'Grid Import', 'Grid Export', 'Grid Idle');
            updateEnergyValue('summary-powerwall-flow', liveStatus.battery_power, 'kW', true);
            updateFlowDescription('powerwall-flow-text', liveStatus.battery_power, 'Discharging', 'Charging', 'Idle');
            updatePercentageValue('summary-powerwall-charge', liveStatus.percentage_charged);
            const energyLeft = parseFloat(energyFlowContainer.dataset.totalCapacity) * (liveStatus.percentage_charged / 100.0);
            updateEnergyValue('summary-powerwall-energy', energyLeft, 'kWh', false, 1);

            const {solar_power, grid_power, load_power, battery_power} = liveStatus;
            updateFlowLine('line-solar-to-junction', solar_power, {colors: {forward: 'bg-warning'}});
            updateFlowLine('line-grid-to-junction', grid_power, {
                reverseCondition: p => p < 0,
                colors: {forward: 'bg-danger', reverse: 'bg-success'}
            });
            updateFlowLine('line-junction-to-house', load_power, {threshold: 10, colors: {forward: 'bg-info'}});
            updateFlowLine('line-junction-to-powerwall', battery_power, {
                reverseCondition: p => p > 0,
                colors: {forward: 'bg-primary', reverse: 'bg-purple'}
            });

        } catch (error) {
            console.error('Error fetching energy data:', error);
        }
    };

    fetchData();
    setInterval(fetchData, refreshInterval);
});