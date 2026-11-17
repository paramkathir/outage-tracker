'use strict';

/* Public status page: "Is it down where I am?" */

(() => {
    const { SERVICE, api, esc, time, hourLabel, ago, joinWords, toast } = OT;
    const DEFAULT_ZIP = '75074';
    const ALERT_RADIUS_KM = 5;

    const els = {
        lookup: document.getElementById('lookup'),
        zip: document.getElementById('zip'),
        zipOptions: document.getElementById('zip-options'),
        locate: document.getElementById('locate'),
        placeLine: document.getElementById('place-line'),
        hero: document.getElementById('hero'),
        chart: document.getElementById('chart'),
        axis: document.getElementById('chart-axis'),
        mapLegend: document.getElementById('map-legend'),
        mapCanvas: document.getElementById('map-canvas'),
        updatesSection: document.getElementById('updates-section'),
        updates: document.getElementById('updates'),
        reportForm: document.getElementById('report'),
        reportTitle: document.getElementById('report-title'),
        reportCopy: document.getElementById('report-copy'),
        reportService: document.getElementById('report-service'),
        alertForm: document.getElementById('alerts'),
        alertEmail: document.getElementById('alert-email'),
    };

    /** What we're showing: { zip } or { lat, lng }. */
    let query = null;
    let data = null;

    // ---------- Map ----------

    const map = L.map(els.mapCanvas, { zoomControl: true, scrollWheelZoom: false, attributionControl: true })
        .setView([33.0, -96.75], 12);
    L.tileLayer(OT.TILE_URL, { attribution: OT.TILE_ATTRIBUTION, subdomains: 'abcd', maxZoom: 19 }).addTo(map);
    const mapLayer = L.layerGroup().addTo(map);

    // ---------- Loading ----------

    async function load() {
        if (!query) return;
        try {
            const path = query.zip
                ? `/api/locations/${encodeURIComponent(query.zip)}`
                : `/api/locations?lat=${query.lat}&lng=${query.lng}`;
            data = await api(path);
            render();
        } catch (err) {
            if (err.status === 404 || err.status === 400) {
                toast(err.message, true);
            } else {
                console.error(err);
            }
        }
    }

    function lookupZip(zip) {
        query = { zip };
        els.zip.value = zip;
        OT.storage('ot.zip', zip);
        const url = new URL(window.location.href);
        url.searchParams.set('zip', zip);
        window.history.replaceState(null, '', url);
        load();
    }

    async function loadZipOptions() {
        try {
            const zips = await api('/api/zips');
            els.zipOptions.innerHTML = zips
                .map((z) => `<option value="${esc(z.zip)}">${esc(z.place)}</option>`)
                .join('');
        } catch (e) {
            // Suggestions are optional.
        }
    }

    // ---------- Rendering ----------

    function render() {
        renderPlaceLine();
        renderHero();
        renderChart();
        renderMap();
        renderUpdates();
        renderReportForm();
    }

    function placeName() {
        if (data.place) return data.place.replace(/, [A-Z]{2}$/, '');
        return 'your location';
    }

    function renderPlaceLine() {
        const where = data.place || 'Your location';
        els.placeLine.textContent = `${where} · Updated ${ago(data.generatedAt)}`;
    }

    function affectedServices() {
        return data.services.filter((s) => s.state !== 'OPERATIONAL');
    }

    function renderHero() {
        const primary = data.primary;
        const working = data.services.filter((s) => s.state === 'OPERATIONAL').map((s) => SERVICE[s.serviceType].noun);

        if (!primary) {
            els.hero.innerHTML = `
                <div class="hero-copy">
                    <span class="eyebrow ok"><span class="dot dot-ok" aria-hidden="true"></span>All services working</span>
                    <h1 id="hero-title">Everything's working in your area.</h1>
                    <p class="lede">No outages have been reported near ${esc(placeName())}. Still having trouble? Try restarting your equipment, or report the problem below so we can look into it.</p>
                </div>`;
            return;
        }

        const inc = primary.incident;
        const svc = SERVICE[inc.serviceType];
        const confirmed = inc.status === 'CONFIRMED';
        const across = Math.max(0.5, inc.radiusKm * 2).toFixed(1);
        const affected = affectedServices().map((s) => SERVICE[s.serviceType].label);
        const latest = primary.updates.length ? time(primary.updates[0].createdAt) : '—';

        const eyebrow = confirmed
            ? '<span class="eyebrow outage"><span class="dot dot-outage" aria-hidden="true"></span>Confirmed outage</span>'
            : '<span class="eyebrow suspect"><span class="dot dot-suspect" aria-hidden="true"></span>Investigating</span>';
        const headline = confirmed
            ? `${svc.subject} is down in your area.`
            : `We're looking into ${svc.noun} problems in your area.`;
        const lede = confirmed
            ? `We've confirmed an outage covering about ${across} km around ${esc(placeName())}. It isn't your equipment, so there's no need to restart anything. We'll post updates here as soon as we have them.`
            : `${inc.reportCount} customers nearby reported ${svc.noun} problems in the last few minutes. If yours is out too, reporting it below helps us confirm the outage faster.`;

        els.hero.innerHTML = `
            <div class="hero-copy">
                ${eyebrow}
                <h1 id="hero-title">${esc(headline)}</h1>
                <p class="lede">${lede}</p>
            </div>
            <dl class="facts">
                <div><dt>Started</dt><dd>${esc(time(inc.firstReportedAt))}</dd></div>
                <div><dt>Customer reports</dt><dd>${inc.reportCount}</dd></div>
                <div><dt>Affected ${affected.length > 1 ? 'services' : 'service'}</dt><dd>${esc(affected.join(', '))}</dd></div>
                <div><dt>Latest update</dt><dd>${esc(latest)}</dd></div>
            </dl>
            ${working.length ? `
            <p class="others">
                <span class="dot dot-ok" aria-hidden="true"></span>
                ${esc(capitalize(joinWords(working)))} ${working.length === 1 ? 'is' : 'are'} working normally in your area.
            </p>` : ''}`;
    }

    function capitalize(s) {
        return s.charAt(0).toUpperCase() + s.slice(1);
    }

    function renderChart() {
        const end = new Date(data.generatedAt).getTime();
        const hour = 3600 * 1000;
        const primary = data.primary && data.primary.incident;
        const since = primary ? new Date(primary.firstReportedAt).getTime() : Infinity;
        const hotClass = primary && primary.status === 'CONFIRMED' ? 'hot' : 'warm';

        const bars = data.hourlyReports.map((value, i) => {
            const bucketEnd = end - (23 - i) * hour;
            const bucketStart = bucketEnd - hour;
            const label = `${value} ${value === 1 ? 'report' : 'reports'}, ${hourLabel(new Date(bucketStart))}–${hourLabel(new Date(bucketEnd))}`;
            return { value, cls: bucketEnd > since ? hotClass : '', title: label };
        });
        OT.renderBars(els.chart, bars, 180);

        const total = data.hourlyReports.reduce((a, b) => a + b, 0);
        const lastTwo = data.hourlyReports.slice(-2).reduce((a, b) => a + b, 0);
        els.chart.setAttribute('aria-label',
            `Reports per hour near you over the last 24 hours: ${total} in total, ${lastTwo} in the last two hours.`);

        const ticks = [24, 18, 12, 6].map((h) => hourLabel(new Date(end - h * hour)));
        els.axis.innerHTML = [...ticks, 'Now'].map((t) => `<span>${esc(t)}</span>`).join('');
    }

    function renderMap() {
        mapLayer.clearLayers();
        const you = [data.latitude, data.longitude];
        const bounds = L.latLngBounds([you]);

        data.affecting.forEach((inc, i) => {
            const circle = L.circle([inc.latitude, inc.longitude], {
                radius: Math.max(inc.radiusKm * 1000, 500),
                ...OT.circleStyle(inc, i === 0),
            }).bindTooltip(`${SERVICE[inc.serviceType].label} · ${inc.reportCount} reports`);
            circle.addTo(mapLayer);
            bounds.extend(circle.getBounds());
        });

        L.circleMarker(you, {
            radius: 8, color: '#FFFFFF', weight: 3, fillColor: getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#1F4FD8', fillOpacity: 1,
        }).bindTooltip('Your address').addTo(mapLayer);

        if (data.affecting.length) {
            map.fitBounds(bounds, { padding: [24, 24], maxZoom: 14 });
        } else {
            map.setView(you, 13);
        }

        const hasOutage = data.affecting.some((i) => i.status === 'CONFIRMED');
        const hasSuspect = data.affecting.some((i) => i.status !== 'CONFIRMED');
        els.mapLegend.innerHTML = [
            hasOutage ? '<span><i class="swatch outage" aria-hidden="true"></i>Confirmed outage area</span>' : '',
            hasSuspect ? '<span><i class="swatch suspect" aria-hidden="true"></i>Area under investigation</span>' : '',
            '<span><i class="swatch you" aria-hidden="true"></i>Your address</span>',
        ].join('');
        els.mapCanvas.setAttribute('aria-label', data.affecting.length
            ? `Map showing ${data.affecting.length} affected area(s) around your address.`
            : 'Map of your area. No affected areas.');
    }

    function renderUpdates() {
        const updates = data.primary ? data.primary.updates : [];
        els.updatesSection.hidden = updates.length === 0;
        els.updates.innerHTML = updates.map((u) => `
            <li>
                <time datetime="${esc(u.createdAt)}">${esc(time(u.createdAt))}</time>
                <div class="entry">
                    <span class="title">${esc(u.title)}</span>
                    ${u.body ? `<span class="body">${esc(u.body)}</span>` : ''}
                    <span class="source">${u.kind === 'OPERATOR' ? 'Update from our network team' : 'Automatic'}</span>
                </div>
            </li>`).join('');
    }

    function renderReportForm() {
        const confirmed = data.services.filter((s) => s.state === 'OUTAGE').map((s) => s.serviceType);
        // Default the dropdown to a service that is NOT already confirmed down.
        const firstOther = ['INTERNET', 'TV', 'PHONE', 'MOBILE'].find((t) => !confirmed.includes(t));
        if (confirmed.length && firstOther && confirmed.includes(els.reportService.value)) {
            els.reportService.value = firstOther;
        }
        if (confirmed.length) {
            els.reportTitle.textContent = 'Having a different problem?';
            els.reportCopy.textContent = `No need to report the ${joinWords(confirmed.map((t) => SERVICE[t].noun))} outage, it's already confirmed. For anything else, let us know.`;
        } else {
            els.reportTitle.textContent = 'Something not working?';
            els.reportCopy.textContent = "Tell us what's down. Reports from your neighbors help us find outages faster.";
        }
    }

    // ---------- Actions ----------

    els.lookup.addEventListener('submit', (e) => {
        e.preventDefault();
        const zip = els.zip.value.trim();
        if (!/^\d{5}$/.test(zip)) {
            toast('Enter a 5-digit ZIP code.', true);
            return;
        }
        lookupZip(zip);
    });

    els.locate.addEventListener('click', () => {
        if (!navigator.geolocation) {
            toast("Your browser can't share your location. Enter a ZIP code instead.", true);
            return;
        }
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                query = { lat: pos.coords.latitude.toFixed(5), lng: pos.coords.longitude.toFixed(5) };
                els.zip.value = '';
                load();
            },
            () => toast("We couldn't get your location. Enter a ZIP code instead.", true),
            { timeout: 10_000 },
        );
    });

    els.reportForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!data) return;
        const button = els.reportForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const result = await api('/api/reports', {
                method: 'POST',
                body: {
                    serviceType: els.reportService.value,
                    latitude: data.latitude,
                    longitude: data.longitude,
                    zipCode: data.zip || null,
                },
            });
            const inc = result.incident;
            toast(inc
                ? `Thanks. Your report was added to an ${inc.status === 'CONFIRMED' ? 'outage we\'ve confirmed' : 'issue we\'re investigating'} (${inc.reportCount} reports).`
                : "Thanks, we've logged your report. We'll open an investigation if neighbors report the same thing.");
            load();
        } catch (err) {
            toast(err.message, true);
        } finally {
            button.disabled = false;
        }
    });

    els.alertForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!data) return;
        try {
            await api('/api/subscriptions', {
                method: 'POST',
                body: {
                    email: els.alertEmail.value,
                    latitude: data.latitude,
                    longitude: data.longitude,
                    radiusKm: ALERT_RADIUS_KM,
                    serviceType: null,
                },
            });
            toast(`You're set. We'll email ${els.alertEmail.value} about outages near ${data.place || 'this location'}.`);
            els.alertForm.reset();
        } catch (err) {
            toast(err.message, true);
        }
    });

    // ---------- Start ----------

    setInterval(() => data && renderPlaceLine(), 30_000);
    loadZipOptions();
    const initialZip = new URLSearchParams(window.location.search).get('zip') || OT.storage('ot.zip') || DEFAULT_ZIP;
    lookupZip(/^\d{5}$/.test(initialZip) ? initialZip : DEFAULT_ZIP);
    OT.liveUpdates(load, null);
})();
