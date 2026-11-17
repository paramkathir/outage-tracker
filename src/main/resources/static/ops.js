'use strict';

/* Operator dashboard: every incident, live, with resolve and post-update actions. */

(() => {
    const { SERVICE, api, esc, time, ago, toast } = OT;
    const KEY_STORAGE = 'ot.adminKey';

    const els = {
        kpis: document.getElementById('kpis'),
        rows: document.getElementById('incident-rows'),
        detail: document.getElementById('detail'),
        live: document.getElementById('live'),
        scopeButtons: [...document.querySelectorAll('[data-scope]')],
    };

    const state = {
        scope: 'ACTIVE',
        incidents: [],
        selectedId: null,
        zips: [],
        fitMap: true,
    };

    // ---------- Map ----------

    const map = L.map('ops-map', { scrollWheelZoom: false }).setView([32.9, -96.95], 9);
    L.tileLayer(OT.TILE_URL, { attribution: OT.TILE_ATTRIBUTION, subdomains: 'abcd', maxZoom: 19 }).addTo(map);
    const mapLayer = L.layerGroup().addTo(map);

    // ---------- Data ----------

    async function refresh() {
        try {
            const [status, incidents] = await Promise.all([
                api('/api/status'),
                api(`/api/incidents?scope=${state.scope}`),
            ]);
            state.incidents = incidents;
            if (!incidents.some((i) => i.id === state.selectedId)) {
                state.selectedId = incidents.length ? incidents[0].id : null;
            }
            renderKpis(status);
            renderTable();
            renderMap();
            await loadDetail();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadDetail() {
        if (state.selectedId == null) {
            els.detail.hidden = true;
            return;
        }
        try {
            const detail = await api(`/api/incidents/${state.selectedId}?reports=8`);
            renderDetail(detail);
        } catch (err) {
            console.error(err);
        }
    }

    function nearestPlace(lat, lng) {
        let best = null;
        let bestKm = Infinity;
        for (const z of state.zips) {
            const km = OT.haversineKm(lat, lng, z.latitude, z.longitude);
            if (km < bestKm) {
                best = z;
                bestKm = km;
            }
        }
        if (best && bestKm <= 12) return best.place.replace(/, [A-Z]{2}$/, '');
        return `${lat.toFixed(3)}, ${lng.toFixed(3)}`;
    }

    // ---------- Rendering ----------

    function renderKpis(status) {
        const suspected = status.services.reduce((n, s) => n + s.suspectedIncidents, 0);
        const confirmed = status.services.reduce((n, s) => n + s.confirmedIncidents, 0);
        const lastHour = status.services.reduce((n, s) => n + s.reportsLastHour, 0);
        const affected = status.services.filter((s) => s.state !== 'OPERATIONAL').length;
        els.kpis.innerHTML = `
            <div><dt>Open incidents</dt><dd>${suspected + confirmed}</dd></div>
            <div><dt>Confirmed</dt><dd class="${confirmed ? 'outage' : ''}">${confirmed}</dd></div>
            <div><dt>Reports, last hour</dt><dd>${lastHour}</dd></div>
            <div><dt>Services affected</dt><dd>${affected} <small>of ${status.services.length}</small></dd></div>`;
    }

    function statusCell(status) {
        const dot = status === 'CONFIRMED' ? 'dot-outage' : status === 'SUSPECTED' ? 'dot-suspect' : 'dot-resolved';
        const label = status.charAt(0) + status.slice(1).toLowerCase();
        return `<span class="status-cell ${status}"><span class="dot ${dot}" aria-hidden="true"></span>${label}</span>`;
    }

    function renderTable() {
        if (!state.incidents.length) {
            const what = state.scope === 'RESOLVED' ? 'No resolved incidents yet.' : 'No open incidents. All quiet.';
            els.rows.innerHTML = `<tr class="empty-row"><td colspan="7">${what}</td></tr>`;
            return;
        }
        els.rows.innerHTML = state.incidents.map((i) => {
            const selected = i.id === state.selectedId;
            return `
            <tr class="${selected ? 'selected' : ''}">
                <td class="first">
                    <button class="row-button" type="button" data-id="${i.id}" aria-pressed="${selected}">
                        <strong>#${i.id}</strong> ${esc(nearestPlace(i.latitude, i.longitude))}
                    </button>
                </td>
                <td>${esc(SERVICE[i.serviceType].label)}</td>
                <td>${statusCell(i.status)}</td>
                <td class="num"><strong>${i.reportCount}</strong></td>
                <td class="num">${i.radiusKm.toFixed(1)} km</td>
                <td>${esc(time(i.firstReportedAt))}</td>
                <td>${esc(i.status === 'RESOLVED' ? 'Resolved ' + ago(i.resolvedAt) : ago(i.lastReportedAt))}</td>
            </tr>`;
        }).join('');
    }

    function renderMap() {
        mapLayer.clearLayers();
        const points = [];
        state.incidents.filter((i) => i.status !== 'RESOLVED').forEach((i) => {
            // At metro zoom a 1 km circle is a speck, so the overview uses screen-sized markers
            // scaled by report count. (The public page, zoomed in, draws the real area.)
            const selected = i.id === state.selectedId;
            const marker = L.circleMarker([i.latitude, i.longitude], {
                radius: 7 + Math.sqrt(i.reportCount) * 2.5,
                ...OT.circleStyle(i, selected),
                fillOpacity: i.status === 'CONFIRMED' ? 0.35 : 0.15,
            })
                .bindTooltip(`#${i.id} ${nearestPlace(i.latitude, i.longitude)} · ${SERVICE[i.serviceType].label} · ${i.reportCount} reports`)
                .on('click', () => select(i.id));
            marker.addTo(mapLayer);
            if (selected) marker.bringToFront();
            points.push([i.latitude, i.longitude]);
        });
        if (state.fitMap && points.length) {
            map.fitBounds(points, { padding: [40, 40], maxZoom: 12 });
            state.fitMap = false;
        }
    }

    function renderDetail(d) {
        const i = d.incident;
        const open = i.status !== 'RESOLVED';
        const place = nearestPlace(i.latitude, i.longitude);
        const bars = d.activity.map((value, idx) => ({
            value,
            cls: i.status === 'CONFIRMED' ? 'hot' : 'warm',
            title: `${value} ${value === 1 ? 'report' : 'reports'}, ${(6 - idx) * 10}–${(5 - idx) * 10} min ago`,
        }));
        const savedKey = OT.storage(KEY_STORAGE);

        // Live refreshes re-render this section; keep whatever the operator is typing.
        const draft = d.incident.id === els.detail.dataset.incidentId * 1 ? {
            title: valueOf('update-title'),
            body: valueOf('update-body'),
            key: valueOf('admin-key'),
            focused: document.activeElement && document.activeElement.id,
        } : null;

        els.detail.hidden = false;
        els.detail.dataset.incidentId = String(i.id);
        els.detail.innerHTML = `
            <div class="detail-head">
                <div>
                    <h2>#${i.id} · ${esc(SERVICE[i.serviceType].label)} ${open ? 'outage' : 'incident'} near ${esc(place)}</h2>
                    <p>${statusCell(i.status)} · ${i.reportCount} reports · started ${esc(time(i.firstReportedAt))} · centre ${i.latitude.toFixed(3)}, ${i.longitude.toFixed(3)}</p>
                </div>
            </div>

            <div class="detail-grid">
                <figure class="figure">
                    <h3>Reports per 10 minutes</h3>
                    <div id="activity" class="bars" role="img" aria-label="Reports per 10 minutes over the last hour: ${d.activity.join(', ')}"></div>
                    <div class="axis" aria-hidden="true"><span>60 min ago</span><span>Now</span></div>
                </figure>

                <div>
                    <h3>Latest reports</h3>
                    <ul class="report-list">
                        ${d.recentReports.map((r) => `
                        <li>
                            <time datetime="${esc(r.reportedAt)}">${esc(time(r.reportedAt))}</time>
                            <span>${esc(r.description || 'No details given')}${r.zipCode ? ` <span class="meta">· ${esc(r.zipCode)}</span>` : ''}</span>
                        </li>`).join('') || '<li><span></span><span class="meta">No reports.</span></li>'}
                    </ul>
                </div>

                ${open ? `
                <div class="ops-form">
                    <h3>Actions</h3>
                    ${savedKey ? `
                    <p class="hint">Admin key saved for this session. <button class="btn btn-text" type="button" id="forget-key">Forget</button></p>` : `
                    <label class="field">Admin key
                        <input class="input" type="password" id="admin-key" autocomplete="off" placeholder="X-Admin-Key">
                    </label>`}
                    <form id="update-form" class="ops-form">
                        <label class="field">Title (optional)
                            <input class="input" id="update-title" maxlength="120" placeholder="Technician on the way">
                        </label>
                        <label class="field">Customer update
                            <textarea class="textarea" id="update-body" maxlength="1000" required
                                      placeholder="What's happening and what customers should expect."></textarea>
                        </label>
                        <p class="hint">Shown on the public status page for this area.</p>
                        <div class="row">
                            <button class="btn btn-primary" type="submit">Post update</button>
                            <button class="btn" type="button" id="resolve">Resolve incident</button>
                        </div>
                    </form>
                </div>` : `
                <div>
                    <h3>Resolution</h3>
                    <p style="margin:0;font-size:14px;color:var(--ink-2)">${esc(i.resolutionNote || 'Resolved')}<br>
                    <span style="color:var(--muted)">${esc(time(i.resolvedAt))}</span></p>
                </div>`}
            </div>

            <div class="subsection">
                <h3>Timeline</h3>
                <ol class="timeline">
                    ${d.updates.map((u) => `
                    <li>
                        <time datetime="${esc(u.createdAt)}">${esc(time(u.createdAt))}</time>
                        <div class="entry">
                            <span class="title">${esc(u.title)}</span>
                            ${u.body ? `<span class="body">${esc(u.body)}</span>` : ''}
                            <span class="source">${u.kind === 'OPERATOR' ? 'Operator' : 'Automatic'}</span>
                        </div>
                    </li>`).join('')}
                </ol>
            </div>`;

        OT.renderBars(document.getElementById('activity'), bars, 120);
        if (open) {
            if (draft) {
                setValue('update-title', draft.title);
                setValue('update-body', draft.body);
                setValue('admin-key', draft.key);
                const again = draft.focused && document.getElementById(draft.focused);
                if (again) again.focus();
            }
            wireActions(i.id);
        }
    }

    function valueOf(id) {
        const el = document.getElementById(id);
        return el ? el.value : '';
    }

    function setValue(id, value) {
        const el = document.getElementById(id);
        if (el && value) el.value = value;
    }

    // ---------- Actions ----------

    function adminKey() {
        const saved = OT.storage(KEY_STORAGE);
        if (saved) return saved;
        const input = document.getElementById('admin-key');
        const value = input ? input.value.trim() : '';
        if (!value) {
            toast('Enter the admin key first.', true);
            if (input) input.focus();
            return null;
        }
        return value;
    }

    async function adminCall(path, body) {
        const key = adminKey();
        if (!key) return null;
        try {
            const result = await api(path, { method: 'POST', body, headers: { 'X-Admin-Key': key } });
            OT.storage(KEY_STORAGE, key);
            return result;
        } catch (err) {
            if (err.status === 401) OT.storage(KEY_STORAGE, null);
            toast(err.message, true);
            return null;
        }
    }

    function wireActions(id) {
        const forget = document.getElementById('forget-key');
        if (forget) {
            forget.addEventListener('click', () => {
                OT.storage(KEY_STORAGE, null);
                loadDetail();
            });
        }

        document.getElementById('update-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const body = document.getElementById('update-body').value.trim();
            if (!body) {
                toast('Write the update first.', true);
                return;
            }
            const title = document.getElementById('update-title').value.trim() || null;
            const ok = await adminCall(`/api/admin/incidents/${id}/updates`, { title, body });
            if (ok) {
                document.getElementById('update-title').value = '';
                document.getElementById('update-body').value = '';
                toast('Update posted to the status page.');
                refresh();
            }
        });

        document.getElementById('resolve').addEventListener('click', async () => {
            const note = document.getElementById('update-body').value.trim() || null;
            const ok = await adminCall(`/api/admin/incidents/${id}/resolve`, { note });
            if (ok) {
                toast(`Incident #${id} resolved.`);
                refresh();
            }
        });
    }

    function select(id) {
        state.selectedId = id;
        renderTable();
        renderMap();
        loadDetail();
    }

    els.rows.addEventListener('click', (e) => {
        const button = e.target.closest('button[data-id]');
        if (button) select(Number(button.dataset.id));
    });

    els.scopeButtons.forEach((b) => b.addEventListener('click', () => {
        state.scope = b.dataset.scope;
        state.fitMap = true;
        els.scopeButtons.forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
        refresh();
    }));

    // ---------- Start ----------

    (async () => {
        try {
            state.zips = await api('/api/zips');
        } catch (e) {
            // Place names fall back to coordinates.
        }
        await refresh();
        OT.liveUpdates(refresh, els.live);
    })();
})();
