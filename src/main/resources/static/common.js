'use strict';

/* Helpers shared by the status page and the operator dashboard. */

const OT = (() => {
    const SERVICE = {
        INTERNET: { label: 'Internet', noun: 'internet', subject: 'Internet' },
        TV: { label: 'TV', noun: 'TV', subject: 'TV' },
        PHONE: { label: 'Home phone', noun: 'home phone', subject: 'Home phone service' },
        MOBILE: { label: 'Mobile', noun: 'mobile', subject: 'Mobile service' },
    };

    /**
     * Standard OpenStreetMap tiles (no API key needed). base.css renders them in grayscale so
     * outage areas are the only color on the map.
     */
    const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    const TILE_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

    async function api(path, { method = 'GET', body, headers = {} } = {}) {
        const res = await fetch(path, {
            method,
            headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers },
            body: body ? JSON.stringify(body) : undefined,
        });
        const isJson = (res.headers.get('content-type') || '').includes('json');
        const data = isJson ? await res.json() : null;
        if (!res.ok) {
            const fieldErrors = data && data.errors
                && Object.entries(data.errors).map(([field, msg]) => `${field} ${msg}`).join('; ');
            const err = new Error(fieldErrors || (data && data.detail) || `Request failed (${res.status})`);
            err.status = res.status;
            throw err;
        }
        return data;
    }

    function esc(value) {
        return String(value ?? '').replace(/[&<>"']/g, (c) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
        }[c]));
    }

    function time(iso) {
        return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    }

    function hourLabel(date) {
        return date.toLocaleTimeString([], { hour: 'numeric' });
    }

    function ago(iso) {
        const secs = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
        if (secs < 60) return 'just now';
        if (secs < 3600) return `${Math.floor(secs / 60)} min ago`;
        if (secs < 86400) return `${Math.floor(secs / 3600)} hr ago`;
        return `${Math.floor(secs / 86400)} d ago`;
    }

    /** "TV, home phone and mobile" */
    function joinWords(words) {
        if (words.length <= 1) return words.join('');
        return `${words.slice(0, -1).join(', ')} and ${words[words.length - 1]}`;
    }

    let toastTimer;
    function toast(message, isError = false) {
        const el = document.getElementById('toast');
        el.textContent = message;
        el.classList.toggle('error', isError);
        el.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => el.classList.remove('show'), 5000);
    }

    /** Subscribes to live incident changes; calls onChange (debounced) and keeps a "Live" badge in sync. */
    function liveUpdates(onChange, badge) {
        let timer;
        const debounced = () => {
            clearTimeout(timer);
            timer = setTimeout(onChange, 300);
        };
        const source = new EventSource('/api/incidents/stream');
        source.addEventListener('connected', () => badge && badge.classList.add('on'));
        source.addEventListener('incident', debounced);
        source.onerror = () => badge && badge.classList.remove('on');
        setInterval(onChange, 60_000); // safety net if the stream drops
    }

    function storage(key, value) {
        try {
            if (value === undefined) return window.sessionStorage.getItem(key);
            if (value === null) window.sessionStorage.removeItem(key);
            else window.sessionStorage.setItem(key, value);
        } catch (e) {
            // Storage can be unavailable (privacy modes); the page still works without it.
        }
        return null;
    }

    function haversineKm(lat1, lng1, lat2, lng2) {
        const toRad = (d) => (d * Math.PI) / 180;
        const dLat = toRad(lat2 - lat1);
        const dLng = toRad(lng2 - lng1);
        const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
        return 2 * 6371.0088 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Renders a bar chart into `el`. bars: [{value, cls, title}] */
    function renderBars(el, bars, heightPx) {
        const max = Math.max(4, ...bars.map((b) => b.value));
        el.innerHTML = bars.map((b) => {
            const h = Math.max(3, Math.round((b.value / max) * (heightPx - 12)));
            return `<div class="bar ${b.cls || ''}" style="height:${h}px" title="${esc(b.title)}"></div>`;
        }).join('');
    }

    function circleStyle(incident, selected) {
        const confirmed = incident.status === 'CONFIRMED';
        const color = confirmed ? '#C2410C' : '#64748B';
        return {
            color,
            weight: selected ? 3 : 1.5,
            dashArray: confirmed ? null : '6 5',
            fillColor: color,
            fillOpacity: confirmed ? 0.16 : (selected ? 0.12 : 0.05),
        };
    }

    return {
        SERVICE, TILE_URL, TILE_ATTRIBUTION,
        api, esc, time, hourLabel, ago, joinWords, toast, liveUpdates, storage, haversineKm, renderBars, circleStyle,
    };
})();
