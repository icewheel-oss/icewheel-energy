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
 * This script handles the dynamic energy flow visualization.
 * It initializes a pool of "sparkle" elements and then updates their state
 * based on live data fetched from the API, using a "fire-and-forget" model
 * where CSS handles the animation lifecycle.
 * It depends on energy-flow-calculator.js, which must be included first.
 */
document.addEventListener('DOMContentLoaded', () => {
    const energyFlowContainer = document.getElementById('energy-flow-container');
    if (!energyFlowContainer) {
        return; // Exit if the main container is not found
    }

    const siteId = energyFlowContainer.dataset.siteId;
    const refreshInterval = 5000;
    const POWER_THRESHOLD = 50; // Watts
    const MAX_SPARKLES = 40;

    const colorMap = {
        solar: 'var(--bs-warning)',
        gridImport: 'var(--bs-danger)',
        gridExport: 'var(--bs-success)',
        powerwallDischarge: 'var(--bs-success)',
        powerwallCharge: 'var(--bs-primary)',
        home: 'var(--bs-info)'
    };

    // --- Function Definitions ---
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

    /**
     * Manages the visual state of an energy flow line, including mixed-source sparkle coloring.
     */
    const manageFlow = (lineId, totalPower, sources, sourceColors, isReverse = false, baseColor) => {
        const line = document.getElementById(lineId);
        if (!line) return;

        const isActive = Math.abs(totalPower) > POWER_THRESHOLD;
        line.classList.toggle('flow-active', isActive);
        line.classList.toggle('flow-reverse', isReverse);

        const sparkles = line.querySelectorAll('.sparkle');
        const sparkleCount = isActive ? Math.min(MAX_SPARKLES, Math.max(5, Math.floor(Math.abs(totalPower) / 150))) : 0;

        const totalSourcePower = Object.values(sources).reduce((a, b) => a + b, 0);
        const baseColorChance = 0.4; // 40% of sparkles will be the base color.

        sparkles.forEach((sparkle, index) => {
            if (index < sparkleCount) {
                let colorName = baseColor;

                if (Math.random() > baseColorChance && totalSourcePower > 0) {
                    const rand = Math.random();
                    let cumulativeRatio = 0;
                    for (const source in sources) {
                        if (sources[source] > 0) {
                            const ratio = sources[source] / totalSourcePower;
                            cumulativeRatio += ratio;
                            if (rand < cumulativeRatio) {
                                colorName = sourceColors[source];
                                break;
                            }
                        }
                    }
                }

                const finalColor = colorMap[colorName] || 'transparent';
                sparkle.style.backgroundColor = finalColor;
                sparkle.style.boxShadow = `0 0 6px ${finalColor}, 0 0 10px ${finalColor}`;
                sparkle.style.animationPlayState = 'running';
                sparkle.style.opacity = 1;
            } else {
                sparkle.style.animationPlayState = 'paused';
                sparkle.style.opacity = 0;
            }
        });
    };


    /**
     * The main function to fetch data from the API and update the UI.
     */
    const fetchData = async () => {
        try {
            const response = await fetch(`/api/energy/sites/${siteId}/live_status`);
            if (!response.ok) return;
            const liveStatus = await response.json();

            // --- Update UI Text Values ---
            updateEnergyValue('solar-value', liveStatus.solar_power, 'kW', true);
            updateEnergyValue('grid-value', liveStatus.grid_power, 'kW', true);
            updateEnergyValue('home-value', liveStatus.load_power, 'kW', true);
            updateEnergyValue('powerwall-value', liveStatus.battery_power, 'kW', true);
            updatePercentageValue('powerwall-percentage', liveStatus.percentage_charged, '%');

            // --- Calculate Power Flow Distribution ---
            const flows = calculatePowerFlows(liveStatus);
            const {solar_power, grid_power, load_power, battery_power} = liveStatus;

            // --- Update Flow Animations ---
            manageFlow('line-solar-to-junction', solar_power, {solar: solar_power}, {solar: 'solar'}, false, 'solar');

            manageFlow('line-grid-to-junction', grid_power,
                {solar: flows.from_solar_to_grid, battery: flows.from_battery_to_grid, grid: Math.max(0, grid_power)},
                {solar: 'solar', battery: 'powerwallDischarge', grid: 'gridImport'},
                grid_power < 0, grid_power < 0 ? 'gridExport' : 'gridImport'
            );

            manageFlow('line-junction-to-house', load_power,
                {solar: flows.from_solar_to_home, battery: flows.from_battery_to_home, grid: flows.from_grid_to_home},
                {solar: 'solar', battery: 'powerwallDischarge', grid: 'gridImport'},
                false, 'home'
            );

            manageFlow('line-junction-to-powerwall', battery_power,
                {
                    solar: flows.from_solar_to_battery,
                    grid: flows.from_grid_to_battery,
                    battery: Math.max(0, battery_power)
                },
                {solar: 'solar', grid: 'gridImport', battery: 'powerwallDischarge'},
                battery_power > 0, battery_power > 0 ? 'powerwallDischarge' : 'powerwallCharge'
            );

        } catch (error) {
            console.error('Error fetching energy data:', error);
        }
    };

    /**
     * Initializes the visualization by creating a pool of sparkle elements for each line.
     */
    const init = () => {
        const lines = document.querySelectorAll('.energy-line');
        lines.forEach(line => {
            const isVertical = line.offsetHeight > line.offsetWidth;
            const sparkleCount = isVertical ? 25 : 40;
            for (let i = 0; i < sparkleCount; i++) {
                const sparkle = document.createElement('span');
                sparkle.className = 'sparkle';
                sparkle.style.animationDelay = `${Math.random() * 3}s`;
                sparkle.style.animationDuration = `${1 + Math.random() * 2}s`;
                if (isVertical) {
                    sparkle.style.left = `${Math.random() * (line.clientWidth - 5)}px`;
                    sparkle.style.animationName = 'sparkle-flow-vertical';
                } else {
                    sparkle.style.top = `${Math.random() * (line.clientHeight - 5)}px`;
                    sparkle.style.animationName = 'sparkle-flow-horizontal';
                }
                line.appendChild(sparkle);
            }
        });

        fetchData();
        setInterval(fetchData, refreshInterval);
    };

    init();
});
