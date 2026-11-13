/**
 * CUJAMMU-PULSE Universal Theme Switcher
 * Supports: Dark Mode, Light Mode, and System Auto Mode with persistence
 */
(function () {
    'use strict';

    const STORAGE_KEY = 'cujammu-pulse-theme';

    function getStoredTheme() {
        return localStorage.getItem(STORAGE_KEY) || 'auto';
    }

    function setStoredTheme(theme) {
        localStorage.setItem(STORAGE_KEY, theme);
    }

    function getPreferredTheme() {
        const storedTheme = getStoredTheme();
        if (storedTheme !== 'auto') {
            return storedTheme;
        }
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    function setTheme(theme) {
        const effectiveTheme = theme === 'auto'
            ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
            : theme;

        document.documentElement.setAttribute('data-bs-theme', effectiveTheme);
        updateToggleIcons(theme, effectiveTheme);
    }

    function updateToggleIcons(themePreference, effectiveTheme) {
        const toggleBtns = document.querySelectorAll('[data-theme-toggle]');
        const iconNodes = document.querySelectorAll('.theme-icon-active, #darkModeIcon');

        iconNodes.forEach(icon => {
            if (themePreference === 'auto') {
                icon.className = 'bi bi-circle-half text-warning';
            } else if (effectiveTheme === 'dark') {
                icon.className = 'bi bi-sun-fill text-warning';
            } else {
                icon.className = 'bi bi-moon-stars-fill text-primary';
            }
        });

        toggleBtns.forEach(btn => {
            btn.setAttribute('title', 'Theme: ' + themePreference.toUpperCase() + ' (Click to cycle)');
        });
    }

    // Initialize immediately to prevent flash of wrong theme
    setTheme(getStoredTheme());

    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        const storedTheme = getStoredTheme();
        if (storedTheme === 'auto') {
            setTheme('auto');
        }
    });

    window.addEventListener('DOMContentLoaded', () => {
        setTheme(getStoredTheme());

        // Attach click listeners to all theme toggles
        const toggles = document.querySelectorAll('#darkModeToggle, [data-theme-toggle]');
        toggles.forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                const current = getStoredTheme();
                let next;
                if (current === 'light') next = 'dark';
                else if (current === 'dark') next = 'auto';
                else next = 'light';

                setStoredTheme(next);
                setTheme(next);
            });
        });
    });

    // Expose helper globally
    window.CUJammuTheme = {
        setTheme: (t) => { setStoredTheme(t); setTheme(t); },
        getTheme: getStoredTheme
    };
})();
