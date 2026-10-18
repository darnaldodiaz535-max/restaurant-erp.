// Resolve the optional experience script from the SAME release as this entry point.
// A missing Push dependency must never turn successful authentication into a login error.
const experienceScriptUrl = new URL('experiencia.js', document.currentScript.src);
experienceScriptUrl.search = new URL(document.currentScript.src).search;
let experienceLoading = null;
function ensureExperienceLoaded() {
    if (typeof bindPushAccount === 'function' && typeof openTaskCamera === 'function' && typeof startMarigex === 'function') return Promise.resolve();
    if (experienceLoading) return experienceLoading;
    experienceLoading = new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = experienceScriptUrl.href;
        const timer = setTimeout(() => finish(new Error('No se pudo cargar experiencia.js. Recarga la página con conexión a internet.')), 10000);
        let settled = false;
        function finish(error) {
            if (settled) return;
            settled = true; clearTimeout(timer); script.onload = script.onerror = null;
            if (error) { script.remove(); reject(error); } else resolve();
        }
        script.onload = () => finish(typeof bindPushAccount === 'function' && typeof openTaskCamera === 'function' && typeof startMarigex === 'function'
            ? null : new Error('experiencia.js no corresponde a esta versión o contiene un error de JavaScript.'));
        script.onerror = () => finish(new Error('No se pudo descargar experiencia.js. Comprueba que esté publicado junto con scrip.js.'));
        document.head.append(script);
    }).catch(error => { experienceLoading = null; throw error; });
    return experienceLoading;
}

const dashboard = document.getElementById("dashboard-screen");
const personal = document.getElementById("personal-module");
const otherModule = document.getElementById("other-module");
const menuItems = [...document.querySelectorAll("nav a")];
const employeeBody = document.querySelector("#personal-module tbody");
const employeesApi = "/api/empleados";
const modulesApi = "/api/modulos";
const authApi = "/api/auth";
let screenRevision = 0;
let attendanceQrTimer = null;
dashboard.parentElement.append(personal, otherModule);
document.querySelector(".activity-list")?.replaceChildren();
document.querySelector(".alert-list")?.replaceChildren();
document.querySelectorAll(".notification-item").forEach((item) => item.remove());

const navLabel = (link) => link.querySelector(".nav-label")?.textContent.trim() || link.textContent.trim();
const iconPaths = {
    Dashboard: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-6v-7H10v7H4a1 1 0 0 1-1-1z"/>',
    Personal: '<circle cx="9" cy="8" r="4"/><path d="M3 21v-2a6 6 0 0 1 12 0v2M16 11a4 4 0 0 1 4 4v6"/>',
    Asistencia: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    Horarios: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/>',
    "Tareas diarias": '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="m8 10 2 2 5-5M8 16h8"/>',
    "Check-in Diario": '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 2v4M16 2v4M3 9h18m-13 5 2 2 4-4"/>',
    "Cierre de turno": '<path d="M4 4h16v16H4zM8 8h8M8 12h8M8 16h5"/><path d="m15 16 2 2 4-4"/>',
    Inventario: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="M3 8v9l9 5 9-5V8M12 13v9"/>',
    Cocina: '<path d="M6 13h12l-1 8H7l-1-8ZM5 13a3 3 0 0 1 0-6 4 4 0 0 1 8-1 4 4 0 0 1 7 2 3 3 0 0 1-1 5"/>',
    Salón: '<circle cx="12" cy="12" r="7"/><path d="M2 3v6M5 3v6M2 6h3M3.5 9v12M21 3v18"/>',
    Barra: '<path d="M3 3h18l-9 10L3 3ZM12 13v8M7 21h10M7 7h10"/>',
    Copería: '<path d="M3 12h18l-3 8H6l-3-8ZM9 12V5a3 3 0 0 1 6 0M13 6h4M6 9h2M18 9h2"/>',
    Producción: '<path d="M4 20h16M6 20V9l6-5 6 5v11M9 20v-6h6v6"/>',
    "Recetas": '<path d="M6 3h12a2 2 0 0 1 2 2v16H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z"/><path d="M8 8h8M8 12h8M8 16h5"/>',
    Reservas: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M8 14h3"/>',
    Limpieza: '<path d="M4 20h16M7 16l2-8h6l2 8M10 8V4h4v4M12 2v2"/>',
    Incidencias: '<path d="M10 3 2 18a2 2 0 0 0 2 3h16a2 2 0 0 0 2-3L14 3a2 2 0 0 0-4 0Z"/><path d="M12 9v4M12 17h.01"/>',
    Compras: '<path d="M3 3h2l2.4 12.2a2 2 0 0 0 2 1.6h7.8a2 2 0 0 0 2-1.6L21 8H6"/><circle cx="10" cy="21" r="1"/><circle cx="18" cy="21" r="1"/>',
    Mermas: '<path d="M4 7h16M10 11v6M14 11v6M5 7l1 14h12l1-14M9 7V4h6v3"/>',
    "Control sanitario": '<path d="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0Z"/><path d="M12 11v7"/>',
    Reportes: '<path d="M4 20h16M6 17V9h3v8M11 17V4h3v13M16 17v-6h3v6"/>',
    Configuración: '<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1 1.4 1.1-1.4 2.4-1.7-.6a8 8 0 0 1-1.5.9l-.3 1.8h-2.8l-.3-1.8a8 8 0 0 1-1.5-.9l-1.7.6-1.4-2.4 1.4-1.1a7 7 0 0 1 0-1.8l-1.4-1.1 1.4-2.4 1.7.6a8 8 0 0 1 1.5-.9l.3-1.8h2.8l.3 1.8a8 8 0 0 1 1.5.9l1.7-.6 1.4 2.4-1.4 1.1a7 7 0 0 1 0 1.8Z"/>'
};
function makeIcon(paths, className) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24"); svg.setAttribute("fill", "none");
    svg.setAttribute("stroke", "currentColor"); svg.setAttribute("stroke-width", "1.8");
    svg.setAttribute("stroke-linecap", "round"); svg.setAttribute("stroke-linejoin", "round");
    svg.setAttribute("aria-hidden", "true"); svg.classList.add(className); svg.innerHTML = paths;
    return svg;
}
menuItems.forEach((link) => {
    const label = link.textContent.trim().replace(/^\S+\s*/, "");
    link.setAttribute("aria-label", label);
    const text = document.createElement("span"); text.className = "nav-label"; text.textContent = label;
    link.replaceChildren(makeIcon(iconPaths[label] || iconPaths.Dashboard, "nav-icon"), text);
});
const bell = document.querySelector(".bell-icon");
if (bell) { bell.replaceChildren(makeIcon('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>', "bell-svg")); }
document.querySelectorAll(".notification-item").forEach((item) => {
    const title = item.querySelector("strong")?.textContent || "";
    const glyph = title.includes("Tarea")
        ? '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="m8 10 2 2 5-5M8 16h8"/>'
        : title.includes("Incidencia")
            ? '<path d="M10 3 2 18a2 2 0 0 0 2 3h16a2 2 0 0 0 2-3L14 3a2 2 0 0 0-4 0Z"/><path d="M12 9v4M12 17h.01"/>'
            : '<path d="M3 3h2l2.4 12.2a2 2 0 0 0 2 1.6h7.8a2 2 0 0 0 2-1.6L21 8H6"/><circle cx="10" cy="21" r="1"/><circle cx="18" cy="21" r="1"/>';
    const icon = item.querySelector("i"); if (icon) icon.replaceChildren(makeIcon(glyph, "notification-icon-svg"));
});
function readStore(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; }
    catch { return fallback; }
}
function accessRole() { return currentProfile?.role || ""; }
function isOwnerRole() { return ["ADMIN", "EMPRESA", "JEFE_SALON", "JEFE_COCINA", "JEFE_LOCAL"].includes(accessRole()); }
function isAdminRole() { return ["ADMIN", "EMPRESA"].includes(accessRole()); }
function isAttendanceQrAdminRole() { return ["ADMIN", "EMPRESA"].includes(accessRole()); }
function attendanceScheduleAreaForRole() { return ({ JEFE_SALON: "SALON", JEFE_COCINA: "COCINA", JEFE_BARRA: "BARRA", JEFE_COPERIA: "COPERIA" })[accessRole()] || ""; }
function canManageAttendanceScheduleRole() { return isAttendanceQrAdminRole() || Boolean(attendanceScheduleAreaForRole()); }
function isSalonRole() { return accessRole() === "JEFE_SALON"; }
function isKitchenRole() { return accessRole() === "JEFE_COCINA"; }
function isWorkerRole() { return ["TRABAJADOR", "EMPLEADO"].includes(accessRole()); }
const kitchenModuleKeys = new Set(["tareas-diarias", "inventario", "cocina", "produccion", "recetas", "limpieza", "control-sanitario", "compras", "mermas", "incidencias", "asistencia", "turnos"]);
function canManageModule(key) {
    if (["personal", "turnos", "configuracion"].includes(key)) return isOwnerRole();
    return isOwnerRole() || isSalonRole() || (isKitchenRole() && kitchenModuleKeys.has(key));
}
function canSeeModule(key) { return Boolean(currentProfile); }
async function uploadRequest(url, formData) {
    const response = await fetch(url, { method: "POST", body: formData, credentials: "same-origin" });
    if (!response.ok) { const problem = await response.json().catch(() => ({})); throw new Error(problem.error || problem.message || problem.detail || `Error del servidor (${response.status})`); }
    return response.json();
}
async function apiRequest(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", ...(options.headers || {}) }
    });
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        const error = new Error(problem.error || problem.message || problem.detail || `Error del servidor (${response.status})`); error.status = response.status; throw error;
    }
    return response.status === 204 ? null : response.json();
}
function buildEmployeeRow(employee) {
    const row = document.createElement("tr");
    row.dataset.id = employee.id;
    [employee.name, employee.role, employee.area].forEach((value) => {
        const cell = document.createElement("td"); cell.textContent = value; row.appendChild(cell);
    });
    const statusCell = document.createElement("td");
    const status = document.createElement("span");
    status.className = `status ${employee.active ? "active" : "inactive"}`;
    status.textContent = employee.active ? "Activo" : "Inactivo";
    statusCell.appendChild(status); row.appendChild(statusCell);
    const actions = document.createElement("td");
    actions.innerHTML = '<button class="table-button" type="button">Ver</button>';
    if (isOwnerRole()) actions.innerHTML += ' <button class="table-button" type="button">Editar</button> <button class="table-button" type="button">Cambiar estado</button>';
    if (isOwnerRole()) actions.innerHTML += ' <button class="table-button" type="button">Eliminar</button>';
    if (isOwnerRole() && !employee.hasAccount) actions.innerHTML += ' <button class="table-button" type="button">Crear acceso</button>';
    row.appendChild(actions); return row;
}
async function loadEmployees() {
    try {
        const employees = await apiRequest(employeesApi);
        employeeBody.replaceChildren(...employees.map(buildEmployeeRow));
        filterEmployees();
        const counts = document.querySelectorAll(".personal-summary .summary-card strong");
        if (counts.length >= 3) {
            counts[0].textContent = employees.length;
            counts[1].textContent = employees.filter((employee) => employee.active).length;
            counts[2].textContent = employees.filter((employee) => !employee.active).length;
        }
    } catch (error) {
        console.error("No se pudieron cargar los empleados desde Oracle:", error);
        document.querySelectorAll(".personal-summary .summary-card strong").forEach(count => { count.textContent = "—"; });
        employeeBody.replaceChildren();
        const row = document.createElement("tr");
        const cell = document.createElement("td"); cell.colSpan = 5;
        cell.textContent = `No se pudo cargar Personal: ${error.message}`;
        row.appendChild(cell); employeeBody.appendChild(row);
    }
}
function slugifyLabel(label) { if (label === "Horarios") return "turnos"; return label.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""); }
function activateNav(selected) {
    menuItems.forEach((link) => {
        const active = link === selected;
        link.classList.toggle("active", active);
        if (active) link.setAttribute("aria-current", "page");
        else link.removeAttribute("aria-current");
    });
}
async function loadDashboardMetrics() {
    if (!currentProfile) return;
    const metricCards = [...document.querySelectorAll(".dashboard-cards .card")].filter(card => {
        const label = card.querySelector("h3")?.textContent.trim();
        return !["Check-in Diario", "Cierre de turno", "Recetas", "Configuración"].includes(label);
    });
    metricCards.forEach(card => { const number = card.querySelector("strong"); if (number) number.textContent = "…"; card.setAttribute("aria-busy", "true"); });
    try {
        const metrics = await apiRequest("/api/dashboard");
        metricCards.forEach((card) => {
            const label = card.querySelector("h3")?.textContent.trim();
            const value = metrics[label];
            const number = card.querySelector("strong");
            if (number) number.textContent = value !== undefined ? String(value) : "—";
            card.setAttribute("aria-busy", "false");
        });
    } catch (error) {
        console.error("No se pudieron actualizar los indicadores:", error);
        metricCards.forEach(card => { const number = card.querySelector("strong"); if (number) number.textContent = "—"; card.setAttribute("aria-busy", "false"); });
    }
    await loadDashboardOverview();
    void loadDashboardCharts();
}

function chileDateISO(date = new Date()) {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Santiago", year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

function renderAttendanceChart(marks, today) {
    const root = document.getElementById("attendance-chart");
    if (!root) return;
    root.replaceChildren();
    const anchor = new Date(`${today}T12:00:00Z`);
    const mondayOffset = (anchor.getUTCDay() + 6) % 7;
    const monday = new Date(anchor); monday.setUTCDate(anchor.getUTCDate() - mondayOffset);
    const days = ["L", "M", "X", "J", "V", "S", "D"].map((label, index) => {
        const date = new Date(monday); date.setUTCDate(monday.getUTCDate() + index);
        const iso = date.toISOString().slice(0, 10);
        const people = new Set((marks || []).filter(mark => mark.fecha === iso && (mark.entrada || mark.salida)).map(mark => mark.empleadoId));
        return { label, date: iso, count: people.size };
    });
    const max = Math.max(1, ...days.map(day => day.count));
    const chart = document.createElement("div"); chart.className = "attendance-bars";
    days.forEach(day => {
        const column = document.createElement("div"); column.className = "attendance-bar-column";
        const count = document.createElement("strong"); count.textContent = String(day.count);
        const track = document.createElement("div"); track.className = "attendance-bar-track";
        const bar = document.createElement("span"); bar.className = "attendance-bar";
        bar.style.height = `${day.count ? Math.max(8, day.count / max * 100) : 2}%`;
        bar.title = `${day.date}: ${day.count} ${day.count === 1 ? "persona" : "personas"}`;
        track.appendChild(bar);
        const label = document.createElement("small"); label.textContent = day.label;
        column.append(count, track, label); chart.appendChild(column);
    });
    root.appendChild(chart);
    const legend = document.createElement("p"); legend.className = "chart-legend";
    legend.innerHTML = '<span aria-hidden="true"></span> Asistencias registradas'; root.appendChild(legend);
}

async function renderTasksAreaChart(today) {
    const root = document.getElementById("tasks-area-chart");
    if (!root) return;
    const areas = [
        ["COCINA", "Cocina", "#12aeb1"], ["SALON", "Salón", "#367df0"],
        ["BARRA", "Barra", "#70aef2"], ["COPERIA", "Copería", "#b8d9f5"], ["SIN_AREA", "Sin área", "#cbd5e1"]
    ];
    const results = await Promise.all(areas.map(async ([key]) => {
        const rows = await apiRequest(`/api/modulos/tareas-diarias?area=${encodeURIComponent(key)}`);
        return rows.filter(row => row.values?.[2] === today && !["COMPLETADA", "COMPLETADO", "FINALIZADA", "FINALIZADO"].includes(String(row.values?.[3] || "").trim().toUpperCase())).length;
    }));
    root.replaceChildren();
    const total = results.reduce((sum, value) => sum + value, 0);
    if (!total) {
        const empty = document.createElement("p"); empty.className = "chart-empty"; empty.textContent = "No hay tareas pendientes para hoy."; root.appendChild(empty); return;
    }
    const stopList = []; let cursor = 0;
    areas.forEach((area, index) => {
        const end = cursor + results[index] / total * 100;
        stopList.push(`${area[2]} ${cursor}% ${end}%`); cursor = end;
    });
    const donut = document.createElement("div"); donut.className = "tasks-donut";
    donut.style.setProperty("--donut-stops", stopList.join(", "));
    const center = document.createElement("div"); center.className = "tasks-donut-center";
    const totalValue = document.createElement("strong"); totalValue.textContent = String(total);
    const totalLabel = document.createElement("span"); totalLabel.textContent = "pendientes"; center.append(totalValue, totalLabel); donut.appendChild(center);
    const legend = document.createElement("ul"); legend.className = "tasks-area-legend";
    areas.forEach((area, index) => {
        if (!results[index]) return;
        const item = document.createElement("li"); const swatch = document.createElement("span"); swatch.style.backgroundColor = area[2];
        const label = document.createElement("span"); label.textContent = area[1];
        const count = document.createElement("strong"); count.textContent = String(results[index]);
        item.append(swatch, label, count); legend.appendChild(item);
    });
    root.classList.add("has-task-data"); root.append(donut, legend);
}

async function loadDashboardCharts() {
    const section = document.querySelector(".dashboard-insights");
    if (!["ADMIN", "EMPRESA"].includes(accessRole())) { if (section) section.hidden = true; return; }
    const today = chileDateISO();
    const attendanceRoot = document.getElementById("attendance-chart");
    const taskRoot = document.getElementById("tasks-area-chart");
    try {
        const report = await apiRequest(`/api/asistencia/reporte?periodo=semana&fecha=${encodeURIComponent(today)}`);
        renderAttendanceChart(report.marcas || [], today);
    } catch (error) {
        console.error("No se pudo cargar el gráfico de asistencia:", error);
        if (attendanceRoot) attendanceRoot.innerHTML = '<p class="chart-error">No se pudo cargar la asistencia.</p>';
    }
    try { await renderTasksAreaChart(today); }
    catch (error) {
        console.error("No se pudo cargar el gráfico de tareas:", error);
        if (taskRoot) taskRoot.innerHTML = '<p class="chart-error">No se pudieron cargar las tareas.</p>';
    }
}

async function loadDashboardOverview() {
    if (!currentProfile) return;

    try {
        const overview = await apiRequest("/api/dashboard/overview");
        const activities = overview.activities || [];
        const alerts = overview.alerts || [];
        const activityList = document.querySelector(".activity-list");
        const activitySection = document.querySelector(".recent-activity");

        if (activityList) {
            activityList.replaceChildren();

            if (!activities.length) {
                const empty = document.createElement("p");
                empty.className = "empty-state";
                empty.textContent = "Aún no hay actividad registrada.";
                activityList.appendChild(empty);
            } else {
                activities.slice(0, 3).forEach((activity) => {
                    const swipe = document.createElement("div");
                    swipe.className = "activity-swipe";

                    const item = document.createElement("div");
                    item.className = "activity-item";
                    item.dataset.activityId = String(activity.id);

                    const icon = document.createElement("span");
                    icon.className = "activity-icon";

                    const moduleLabel = menuItems
                        .map(navLabel)
                        .find((label) =>
                            slugifyLabel(label) === activity.module)
                        || activity.module;

                    icon.textContent =
                        moduleLabel === "Personal" ? "👥"
                        : moduleLabel === "Asistencia" ? "🕒"
                        : moduleLabel.includes("Tarea") ? "✅"
                        : "📋";

                    const content = document.createElement("div");
                    content.className = "activity-content";

                    const title = document.createElement("strong");
                    const actionNames = {
                        CREAR: "Registro agregado",
                        ACTUALIZAR: "Registro actualizado",
                        ELIMINAR: "Registro eliminado",
                        ENVIAR_TAREA: "Tarea enviada para revisión",
                        COMPLETAR_TAREA: "Tarea completada"
                    };
                    title.textContent =
                        `${actionNames[activity.action] || activity.action}`
                        + ` · ${moduleLabel}`;

                    const detail = document.createElement("p");
                    detail.textContent =
                        `${activity.detail} · Usuario: ${activity.username}`;

                    const time = document.createElement("small");
                    time.textContent = activity.createdAt;
                    content.append(title, detail, time);
                    item.append(icon, content);

                    swipe.append(item);
                    activityList.append(swipe);
                });
            }
        }

        const alertSection = document.querySelector(".alerts");
        const alertList = document.querySelector(".alert-list");

        if (alertSection && alertList) {
            alertList.replaceChildren();
            alertSection.hidden = alerts.length === 0;

            alerts.forEach((alert) => {
                const item = document.createElement("div");
                item.className = "alert-item";

                const open = document.createElement("button");
                open.type = "button";
                open.className = "alert-open";

                const icon = document.createElement("span");
                icon.className = "alert-icon";
                icon.textContent = alert.icon;

                const content = document.createElement("span");
                content.className = "alert-content";

                const title = document.createElement("strong");
                title.textContent = alert.module;

                const message = document.createElement("span");
                message.className = "alert-message";
                message.textContent = alert.message;

                content.append(title, message);
                open.append(icon, content);

                const goToModule = () => {
                    const target = menuItems.find(
                        (link) => navLabel(link) === alert.module);
                    if (target) showScreen(target);
                };
                open.addEventListener("click", goToModule);

                item.append(open);

                if (isOwnerRole()) {
                    const dismiss = document.createElement("button");
                    dismiss.type = "button";
                    dismiss.className = "alert-dismiss";
                    dismiss.textContent = "Descartar";
                    dismiss.setAttribute(
                        "aria-label",
                        `Descartar alerta: ${alert.module}`);
dismiss.addEventListener("click", async () => {
    item.remove();
    alertSection.hidden = alertList.children.length === 0;

    try {
        await apiRequest(
            `/api/dashboard/alerts/`
                + encodeURIComponent(alert.id),
            { method: "DELETE" });
        await loadDashboardMetrics();
    } catch (error) {
        await loadDashboardMetrics();
        window.alert(
            `No se pudo descartar la alerta: ${error.message}`);
    }
});

                    item.append(dismiss);
                }

                alertList.appendChild(item);
            });
        }

        await loadUserNotifications();
    } catch (error) {
        console.error(
            "No se pudo actualizar la actividad y las alertas:",
            error);
    }
}
function showScreen(link) {
    const label = navLabel(link);
    const key = slugifyLabel(label);
    if (label !== "Dashboard" && !canSeeModule(key)) return;
    screenRevision++;
    dashboard.hidden = true; personal.hidden = true; otherModule.hidden = true;
    if (label === "Dashboard") dashboard.hidden = false;
    else if (label === "Personal") personal.hidden = false;
    else if (label === "Tareas diarias") renderTaskAreas();
    else if (label === "Check-in Diario") void renderCheckinModule();
    else if (label === "Cierre de turno") renderCloseShiftPlaceholder();
    else if (label === "Recetas") renderRecipesPlaceholder();
    else if (label === "Asistencia") void renderAttendanceModule();
    else if (["Cocina", "Salón", "Barra", "Copería"].includes(label)) void renderArea(normalizeArea(label));
    else void renderModule(label);
    activateNav(link);
    if (label === "Dashboard") void loadDashboardMetrics();
}

function renderCloseShiftPlaceholder() {
    otherModule.hidden = false;
    otherModule.replaceChildren();
    const section = document.createElement("section"); section.className = "module-screen checkin-section";
    section.append(checkinNode("h2", "module-title", "Cierre de turno"));
    section.append(checkinNode("p", "empty-state", "Este apartado ya está agregado. La función para registrar el cierre se incorporará más adelante."));
    otherModule.append(section);
}

function renderRecipesPlaceholder() {
    otherModule.hidden = false;
    otherModule.replaceChildren();
    const section = document.createElement("section"); section.className = "module-screen checkin-section";
    section.append(checkinNode("h2", "module-title", "Recetas"));
    section.append(checkinNode("p", "empty-state", "Aquí cada restaurante podrá registrar los productos que vende y los ingredientes de sus recetas. Después conectaremos esos datos con el inventario y el cierre de turno."));
    otherModule.append(section);
}

const checkinAreas = ["SALON", "COPERIA", "COCINA", "BARRA"];
const checkinAreaNames = { SALON: "Salón", COPERIA: "Copería", COCINA: "Cocina", BARRA: "Barra" };
const checkinAdmin = () => ["ADMIN", "EMPRESA"].includes(accessRole());
function checkinNode(tag, cls, text) { const node = document.createElement(tag); if (cls) node.className = cls; if (text !== undefined) node.textContent = text; return node; }
function checkinButton(label, handler, cls = "secondary-button") { const button = checkinNode("button", cls, label); button.type = "button"; button.addEventListener("click", handler); return button; }
function checkinAreaForProfile() {
    const raw = String(currentProfile?.area || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase();
    if (raw.includes("COPER")) return "COPERIA"; if (raw.includes("SALON")) return "SALON";
    if (raw.includes("COCINA")) return "COCINA"; if (raw.includes("BARRA")) return "BARRA"; return "";
}
function checkinWeekStart() {
    const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Santiago", year: "numeric", month: "2-digit", day: "2-digit"
    }).formatToParts(new Date()).map(part => [part.type, part.value]));
    const d = new Date(Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), 12));
    d.setUTCDate(d.getUTCDate() - (d.getUTCDay() + 6) % 7 - 7);
    return d.getUTCFullYear() + "-" + String(d.getUTCMonth() + 1).padStart(2, "0") + "-" + String(d.getUTCDate()).padStart(2, "0");
}
function checkinHeader(title, subtitle) {
    const h = checkinNode("div", "module-header checkin-header"), heading = checkinNode("div", "module-heading"), copy = checkinNode("div");
    heading.appendChild(checkinNode("span", "module-icon", "📋")); copy.append(checkinNode("h2", "", title), checkinNode("p", "", subtitle)); heading.appendChild(copy); h.appendChild(heading); return h;
}
async function renderCheckinModule(selectedArea = null, adminView = false) {
    const revision = ++screenRevision; dashboard.hidden = true; personal.hidden = true; otherModule.hidden = false;
    otherModule.replaceChildren(); otherModule.className = "module-screen module-screen--checkin-diario"; otherModule.style.setProperty("--module-accent", "#2563eb");
    const admin = checkinAdmin(), area = selectedArea || checkinAreaForProfile();
    otherModule.appendChild(checkinHeader(adminView ? "Check-in administrador" : "Check-in Diario", adminView ? "Resumen del equipo por plaza." : "Busca tu nombre y completa solo tu propio check-in."));
    const section = checkinNode("section", "generic-data checkin-data"), tabs = checkinNode("div", "checkin-tabs");
    if (admin) {
        tabs.appendChild(checkinButton("Mi check-in", () => void renderCheckinModule()));
        tabs.appendChild(checkinButton("Check-in administrador", () => void renderCheckinModule(area || "COCINA", true), adminView ? "primary-button" : "secondary-button"));
    }
    if (!area && !adminView) {
        checkinAreas.forEach(code => tabs.appendChild(checkinButton(checkinAreaNames[code], () => void renderCheckinModule(code), "checkin-area-button")));
        section.append(tabs, checkinNode("p", "empty-state", "Elige tu plaza para buscar tu nombre. Solo podrás abrir tu propio check-in.")); otherModule.appendChild(section); return;
    }
    if (adminView) checkinAreas.forEach(code => tabs.appendChild(checkinButton(checkinAreaNames[code], () => void renderCheckinModule(code, true), area === code ? "primary-button" : "secondary-button")));
    section.appendChild(tabs); const code = area || checkinAreaForProfile();
    if (!code) { section.appendChild(checkinNode("p", "empty-state", "Tu usuario no tiene una plaza compatible.")); otherModule.appendChild(section); return; }
    section.appendChild(checkinNode("h3", "checkin-area-title", checkinAreaNames[code]));
    const feedback = checkinNode("p", "checkin-feedback"); feedback.setAttribute("role", "status"); section.appendChild(feedback);
    if (adminView) await renderCheckinAdminArea(section, code, revision, feedback); else await renderCheckinWorkerArea(section, code, revision, feedback);
    if (revision === screenRevision) otherModule.appendChild(section);
}
async function renderCheckinWorkerArea(section, area, revision, feedback) {
    try {
        const roster = await apiRequest("/api/checkin/areas/" + encodeURIComponent(area) + "/personas"); if (revision !== screenRevision) return;
        const list = checkinNode("div", "checkin-roster");
        roster.forEach(person => {
            const row = checkinNode("div", "checkin-roster-row" + (person.self ? " is-self" : ""));
            row.appendChild(checkinNode("span", "", person.name));
            if (person.canOpen) row.appendChild(checkinButton("Abrir mi check-in", async () => { try { await renderCheckinWorkerDay(section, feedback); } catch (e) { feedback.textContent = e.message; } }, "primary-button"));
            else row.appendChild(checkinNode("span", "checkin-private-label", "Privado")); list.appendChild(row);
        });
        section.appendChild(list); if (!roster.some(p => p.self)) feedback.textContent = "Tu nombre no aparece en esta plaza; solo puedes acceder a tu área.";
    } catch (e) { feedback.textContent = "No se pudo cargar el equipo: " + e.message; return; }
    try {
        const date = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Santiago" });
        const day = await apiRequest("/api/checkin/me/dia?fecha=" + encodeURIComponent(date));
        if (day.employeeName) await renderCheckinDay(section, feedback, day);
        await renderCheckinPersonalSummary(section);
    } catch (e) { feedback.textContent = "No se pudo cargar tu check-in: " + e.message; }
}
async function renderCheckinWorkerDay(section, feedback) { const day = await apiRequest("/api/checkin/me"); await renderCheckinDay(section, feedback, day); }
async function renderCheckinDay(section, feedback, day) {
    section.querySelector(".checkin-day")?.remove(); const card = checkinNode("div", "checkin-day"), title = checkinNode("div", "checkin-day-title");
    title.append(checkinNode("div", "", day.employeeName + " · " + day.date), checkinNode("span", "checkin-percent", day.dayStatus === "LIBRE" ? "Día libre" : day.compliancePercent + "%")); card.appendChild(title);
    const actionUrl = day.dayStatus === "LIBRE" ? "/api/checkin/me/trabajando" : "/api/checkin/me/libre";
    card.appendChild(checkinButton(day.dayStatus === "LIBRE" ? "Cambiar a día trabajado" : "Marcar día LIBRE", async () => {
        if (day.dayStatus !== "LIBRE" && !confirm("¿Marcar hoy como día libre? No contará como incumplimiento.")) return;
        try { await renderCheckinDay(section, feedback, await apiRequest(actionUrl, { method: "PUT" })); } catch (e) { feedback.textContent = e.message; }
    }));
    if (day.dayStatus === "LIBRE") card.appendChild(checkinNode("p", "checkin-note", "Este día no afecta tu porcentaje semanal."));
    else if (!day.tasks.length) card.appendChild(checkinNode("p", "empty-state", "Aún no hay tareas configuradas para tu plaza."));
    day.tasks.forEach(task => {
        const row = checkinNode("article", "checkin-task" + (task.status === "REALIZADA" ? " is-done" : ""));
        const copy = checkinNode("div", "checkin-task-copy"), state = task.status === "REALIZADA" ? "Hecha" : task.method === "FOTO" ? "Pendiente · requiere foto" : "Pendiente";
        copy.append(checkinNode("strong", "", task.title), checkinNode("small", "", state + (task.completedAt ? " · " + task.completedAt.replace("T", " ") : ""))); row.appendChild(copy);
        if (task.photoId) row.appendChild(checkinButton("Ver foto", () => window.open("/api/checkin/fotos/" + task.photoId, "_blank", "noopener")));
        if (task.status !== "REALIZADA" && day.dayStatus !== "LIBRE") {
            if (task.method === "FOTO") {
                const label = checkinNode("label", "checkin-photo-button", "Subir foto"), input = checkinNode("input"); input.type = "file"; input.accept = "image/jpeg,image/png,image/webp"; input.hidden = true;
                input.addEventListener("change", async () => { if (!input.files?.[0]) return; const form = new FormData(); form.append("foto", input.files[0]); try { const updated = await uploadRequest("/api/checkin/tareas/" + task.id + "/foto", form); feedback.textContent = "Evidencia guardada."; await renderCheckinDay(section, feedback, updated); } catch (e) { feedback.textContent = e.message; } });
                label.appendChild(input); row.appendChild(label);
            } else row.appendChild(checkinButton("Hecho", async () => { try { const updated = await apiRequest("/api/checkin/tareas/" + task.id + "/hecha", { method: "POST" }); feedback.textContent = "Tarea guardada."; await renderCheckinDay(section, feedback, updated); } catch (e) { feedback.textContent = e.message; } }, "primary-button"));
        }
        card.appendChild(row);
    });
    section.appendChild(card);
}
async function renderCheckinPersonalSummary(section) {
    const week = await apiRequest("/api/checkin/me/semanal?semana=" + encodeURIComponent(checkinWeekStart()));
    const trend = await apiRequest("/api/checkin/me/tendencia"), card = checkinNode("div", "checkin-summary-card");
    card.appendChild(checkinNode("h3", "", "Mi semana · " + week.weekStart + " al " + week.weekEnd)); const stats = checkinNode("div", "checkin-stats");
    [["Días trabajados", week.daysWorked], ["Días libres", week.daysFree], ["Tareas asignadas", week.assignedTasks], ["Realizadas", week.completedTasks], ["No realizadas", week.incompleteTasks], ["Cumplimiento", week.compliancePercent == null ? "—" : week.compliancePercent + "%"]].forEach(pair => { const item = checkinNode("div", "checkin-stat"); item.append(checkinNode("small", "", pair[0]), checkinNode("strong", "", String(pair[1]))); stats.appendChild(item); });
    card.append(stats, checkinTrendChart(trend, false)); section.appendChild(card);
}
function checkinTrendChart(points, admin) {
    const chart = checkinNode("div", "checkin-trend"); chart.appendChild(checkinNode("h3", "", admin ? "Cumplimiento semanal por trabajador" : "Mi cumplimiento por semana"));
    if (!points || !points.length) { chart.appendChild(checkinNode("p", "empty-state", "Aún no hay semanas con resultados.")); return chart; }
    points.forEach(point => {
        const row = checkinNode("div", "checkin-trend-row"), value = point.compliancePercent, label = admin ? point.name + " · " + (checkinAreaNames[point.area] || point.area) + " · " + point.weekStart : point.weekStart;
        row.appendChild(checkinNode("span", "checkin-trend-label", label)); const track = checkinNode("div", "checkin-progress"), bar = checkinNode("span", "checkin-progress-bar" + (value != null && value > 90 ? " is-excellent" : ""));
        bar.style.width = Math.max(0, Math.min(100, value || 0)) + "%"; track.appendChild(bar); row.appendChild(track); row.appendChild(checkinNode("strong", "checkin-trend-value", value == null ? "—" : value + "%")); chart.appendChild(row);
    }); return chart;
}
async function renderCheckinAdminArea(section, area, revision, feedback) {
    try {
        const results = await Promise.all([
            apiRequest("/api/checkin/admin/area/" + encodeURIComponent(area)),
            apiRequest("/api/checkin/admin/semanal?semana=" + encodeURIComponent(checkinWeekStart())),
            apiRequest("/api/checkin/admin/tendencia")
        ]);
        if (revision !== screenRevision) return; const today = results[0], report = results[1], trend = results[2];
        const daily = checkinNode("div", "checkin-summary-card"); daily.appendChild(checkinNode("h3", "", "Estado de hoy"));
        if (!today.length) daily.appendChild(checkinNode("p", "empty-state", "No hay trabajadores activos con usuario en esta plaza."));
        today.forEach(person => { const row = checkinNode("div", "checkin-admin-person"); row.append(checkinNode("strong", "", person.name), checkinNode("span", "", person.dayStatus === "LIBRE" ? "LIBRE" : person.completedTasks + "/" + person.assignedTasks + " tareas · " + (person.compliancePercent == null ? "—" : person.compliancePercent + "%"))); daily.appendChild(row); }); section.appendChild(daily);
        const weekly = checkinNode("div", "checkin-summary-card"); weekly.appendChild(checkinNode("h3", "", "Reporte semanal global · " + report.weekStart + " al " + report.weekEnd)); const stats = checkinNode("div", "checkin-stats");
        [["Cumplimiento general", report.compliancePercent == null ? "—" : report.compliancePercent + "%"], ["Días trabajados", report.daysWorked], ["Días libres", report.daysFree], ["Tareas asignadas", report.assignedTasks], ["Realizadas", report.completedTasks], ["No realizadas", report.incompleteTasks]].forEach(pair => { const item = checkinNode("div", "checkin-stat"); item.append(checkinNode("small", "", pair[0]), checkinNode("strong", "", String(pair[1]))); stats.appendChild(item); }); weekly.appendChild(stats);
        (report.employees || []).filter(p => p.area === area).forEach(person => { const row = checkinNode("div", "checkin-admin-person"); row.append(checkinNode("strong", "", person.name), checkinNode("span", "", (person.compliancePercent == null ? "—" : person.compliancePercent + "%") + " · " + person.completedTasks + "/" + person.assignedTasks + " hechas · " + person.daysFree + " libres")); weekly.appendChild(row); });
        section.append(weekly, checkinTrendChart(trend, true));
    } catch (e) { feedback.textContent = "No se pudo cargar el reporte administrativo: " + e.message; }
}

function getAttendancePosition() {
    if (!navigator.geolocation) return Promise.reject(new Error("Este dispositivo no permite obtener la ubicación."));
    return new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve,
        error => reject(new Error(error.code === 1 ? "Activa el permiso de ubicación para registrar asistencia." : "No se pudo obtener tu ubicación. Activa el GPS e inténtalo otra vez.")),
        { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 }));
}

async function scanAttendanceQr(feedback) {
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" } }, audio: false });
    const overlay = checkinNode("div", "attendance-scan-overlay"), dialog = checkinNode("section", "attendance-scan-dialog");
    dialog.setAttribute("role", "dialog"); dialog.setAttribute("aria-modal", "true"); dialog.setAttribute("aria-label", "Escanear QR de asistencia");
    dialog.appendChild(checkinNode("h3", "", "Escanea el QR que muestra administración"));
    dialog.appendChild(checkinNode("p", "", "Apunta la cámara al código visible en el local."));
    const video = document.createElement("video"); video.className = "attendance-scan-video"; video.muted = true; video.playsInline = true; video.srcObject = stream;
    const canvas = document.createElement("canvas"); canvas.hidden = true; const ctx = canvas.getContext("2d", { willReadFrequently: true });
    const status = checkinNode("p", "attendance-scan-status", "Buscando el código…");
    const cancel = checkinButton("Cancelar", () => {}, "secondary-button"); dialog.append(video, canvas, status, cancel); overlay.appendChild(dialog); document.body.appendChild(overlay);
    await video.play(); feedback.textContent = "Escanea el QR actual del local para continuar.";
    return new Promise((resolve, reject) => {
        let done = false, lastScan = 0;
        const finish = (error, token) => {
            if (done) return; done = true; clearInterval(scanner); clearTimeout(timeout); stream.getTracks().forEach(track => track.stop()); overlay.remove();
            if (error) reject(error); else resolve(token);
        };
        const scanner = setInterval(() => {
            if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || !video.videoWidth || Date.now() - lastScan < 120) return;
            lastScan = Date.now(); canvas.width = video.videoWidth; canvas.height = video.videoHeight;
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const code = window.jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: "dontInvert" });
            if (code?.data) finish(null, code.data);
        }, 100);
        const timeout = setTimeout(() => finish(new Error("No se detectó el QR. Comprueba que esté visible e inténtalo otra vez.")), 90000);
        cancel.addEventListener("click", () => finish(new Error("Registro cancelado.")), { once: true });
    });
}

async function renderAttendanceModule() {
    if (attendanceQrTimer) { clearInterval(attendanceQrTimer); attendanceQrTimer = null; }
    const revision = ++screenRevision;
    const admin = isAdminRole();
    const qrAdmin = isAttendanceQrAdminRole();
    dashboard.hidden = true; personal.hidden = true; otherModule.hidden = false;
    otherModule.replaceChildren(); otherModule.className = "module-screen module-screen--asistencia";
    otherModule.style.setProperty("--module-accent", "#0891b2");
    const header = checkinHeader("Asistencia", admin ? "Consulta las marcas del personal, revisa el cumplimiento de turnos y administra el QR del local." : "Registra tu entrada y salida y consulta tu historial de asistencia.");
    otherModule.appendChild(header);
    const section = checkinNode("section", "generic-data attendance-panel");
    const feedback = checkinNode("p", "checkin-feedback"); feedback.setAttribute("role", "status"); feedback.setAttribute("aria-live", "polite");
    const profileCard = checkinNode("div", "attendance-clock");
    let identity = { nombre: currentProfile?.name || "", rut: "" };
    if (!admin) {
        try { identity = await apiRequest("/api/asistencia/me"); } catch (error) { feedback.textContent = error.message; }
        if (revision !== screenRevision) return;
    profileCard.appendChild(checkinNode("h3", "", "Marcar asistencia · " + (identity.nombre || currentProfile?.name || "")));
    const rutLabel = checkinNode("label", "attendance-rut-label", "RUT registrado en tu cuenta");
    const rutInput = checkinNode("input", "attendance-rut"); rutInput.type = "text"; rutInput.value = identity.rut || ""; rutInput.autocomplete = "off"; rutInput.maxLength = 20; rutInput.placeholder = "Escribe tu RUT"; rutInput.setAttribute("aria-label", "RUT registrado en tu cuenta"); rutLabel.appendChild(rutInput);
    profileCard.appendChild(rutLabel);
    const actions = checkinNode("div", "attendance-actions");
    const mark = async (type) => {
        if (!rutInput.value.trim()) { feedback.textContent = "Escribe tu RUT para continuar."; rutInput.focus(); return; }
        if (!navigator.mediaDevices?.getUserMedia || typeof window.jsQR !== "function") { feedback.textContent = "No se pudo iniciar el lector de QR. Recarga la página e inténtalo otra vez."; return; }
        let stream;
        try {
            const qrToken = await scanAttendanceQr(feedback);
            feedback.textContent = "QR validado. Comprobando que estás en el local…";
            const position = await getAttendancePosition();
            if (position.coords.accuracy > 100) throw new Error("La ubicación no es suficientemente precisa. Activa el GPS y vuelve a intentarlo.");
            feedback.textContent = "QR y ubicación listos. Tomando la foto…";
            stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
            const video = document.createElement("video"); video.muted = true; video.playsInline = true; video.srcObject = stream;
            await video.play();
            await new Promise(resolve => { if (video.videoWidth > 0) return resolve(); video.addEventListener("loadedmetadata", resolve, { once: true }); setTimeout(resolve, 3000); });
            if (!video.videoWidth || !video.videoHeight) throw new Error("La cámara no entregó una imagen. Vuelve a intentarlo.");
            const scale = Math.min(1, 960 / Math.max(video.videoWidth, video.videoHeight));
            const canvas = document.createElement("canvas"); canvas.width = Math.max(1, Math.round(video.videoWidth * scale)); canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
            canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
            const photo = await new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error("No se pudo capturar la foto.")), "image/jpeg", 0.78));
            const formData = new FormData(); formData.append("tipo", type); formData.append("rut", rutInput.value.trim()); formData.append("foto", photo, "asistencia.jpg");
            formData.append("qrToken", qrToken); formData.append("latitude", String(position.coords.latitude)); formData.append("longitude", String(position.coords.longitude)); formData.append("accuracy", String(position.coords.accuracy));
            const result = await uploadRequest("/api/asistencia/marcar", formData);
            feedback.textContent = `${type === "ENTRADA" ? "Entrada" : "Salida"} registrada a las ${result.hora}.`;
            await loadAttendanceReport(section, controls.period.value, controls.from.value, controls.to.value, admin ? controls.employee.value : "", admin ? controls.area.value : "", feedback);
        } catch (error) { feedback.textContent = error.message || "No se pudo registrar la asistencia."; }
        finally { stream?.getTracks().forEach(track => track.stop()); }
    };
    actions.append(checkinButton("Entrada", () => void mark("ENTRADA"), "primary-button attendance-mark-button"), checkinButton("Salida", () => void mark("SALIDA"), "primary-button attendance-mark-button"));
    profileCard.appendChild(actions);
    }
    if (!qrAdmin) {
        const locationHelp = checkinNode("div", "attendance-location-help");
        const locationStatus = checkinNode("p", "attendance-location-status", "Activa la ubicación de este dispositivo antes de marcar. Al escanear el QR, la aplicación comprobará que estás en el local.");
        const enableLocation = checkinButton("Activar ubicación", async () => {
            enableLocation.disabled = true;
            try {
                const position = await getAttendancePosition();
                if (position.coords.accuracy > 100) throw new Error("La ubicación se detectó, pero no es precisa. Activa el GPS y vuelve a intentarlo.");
                locationStatus.textContent = `Ubicación activada en este dispositivo (precisión aproximada: ${Math.round(position.coords.accuracy)} m). Al marcar, se volverá a comprobar.`;
            } catch (error) {
                locationStatus.textContent = error.message || "No se pudo activar la ubicación. Revisa el permiso de ubicación del navegador y el GPS.";
            } finally { enableLocation.disabled = false; }
        }, "secondary-button");
        locationHelp.append(locationStatus, enableLocation);
        profileCard.appendChild(locationHelp);
    }
    if (admin) {
        const adminActions = checkinNode("div", "attendance-admin-actions");
        adminActions.appendChild(checkinNode("p", "attendance-admin-label", "Administración"));
        profileCard.appendChild(adminActions);
        if (qrAdmin) {
        const qrPanel = checkinNode("section", "attendance-qr-admin");
        qrPanel.appendChild(checkinNode("h4", "", "QR de asistencia del local"));
        qrPanel.appendChild(checkinNode("p", "attendance-qr-help", "Muestra este código en el local. Se renueva automáticamente antes de vencer; el trabajador lo escanea al marcar entrada o salida."));
        const qrSetup = checkinNode("div", "attendance-qr-setup");
        const radiusLabel = checkinNode("label", "attendance-radius-label", "Radio permitido (metros)"), radiusInput = checkinNode("input", "attendance-rut");
        radiusInput.type = "number"; radiusInput.min = "20"; radiusInput.max = "1000"; radiusInput.step = "10"; radiusInput.value = "100"; radiusInput.setAttribute("aria-label", "Radio permitido en metros"); radiusLabel.appendChild(radiusInput);
        const locationStatus = checkinNode("p", "attendance-location-status", "Primero configura la ubicación del local desde aquí, estando físicamente en el local.");
        const saveLocation = checkinButton("Usar mi ubicación actual como local", async () => {
            saveLocation.disabled = true;
            try {
                const position = await getAttendancePosition();
                if (position.coords.accuracy > 100) throw new Error("La ubicación no es suficientemente precisa. Activa el GPS y vuelve a intentar.");
                const config = await apiRequest("/api/asistencia/qr/config", { method: "POST", body: JSON.stringify({ latitude: position.coords.latitude, longitude: position.coords.longitude, radiusMeters: Number(radiusInput.value) }) });
                if (!config.gpsSchemaReady || !config.qrTableReady) throw new Error("Antes de activar asistencia con QR, ejecuta la migración 10_asistencia_qr_ubicacion.sql en Oracle.");
                locationStatus.textContent = `Local configurado con un radio de ${config.radiusMeters} metros.`;
                qrSetup.hidden = true; qrDisplay.hidden = false;
                await refreshAttendanceQr();
            } catch (error) { locationStatus.textContent = error.message || "No se pudo guardar la ubicación."; }
            finally { saveLocation.disabled = false; }
        }, "secondary-button");
        qrSetup.append(radiusLabel, saveLocation);
        const qrDisplay = checkinNode("div", "attendance-qr-display"); qrDisplay.hidden = true;
        const qrCode = checkinNode("div", "attendance-qr-code"); qrCode.setAttribute("aria-label", "Código QR temporal para marcar asistencia");
        const qrExpiry = checkinNode("p", "attendance-qr-expiry", "Preparando QR…");
        const changeLocation = checkinButton("Cambiar ubicación o radio", () => { qrDisplay.hidden = true; qrSetup.hidden = false; }, "secondary-button");
        qrDisplay.append(qrCode, qrExpiry, changeLocation);
        qrPanel.append(qrSetup, qrDisplay, locationStatus);
        adminActions.appendChild(qrPanel);
        const refreshAttendanceQr = async () => {
            if (qrDisplay.hidden || revision !== screenRevision) return;
            try {
                const qr = await apiRequest("/api/asistencia/qr/actual");
                if (revision !== screenRevision) return;
                qrCode.replaceChildren();
                const generated = window.qrcode(0, "M"); generated.addData(qr.token); generated.make();
                qrCode.innerHTML = generated.createSvgTag(6, 4);
                const svg = qrCode.querySelector("svg"); svg?.setAttribute("role", "img"); svg?.setAttribute("aria-label", "QR temporal de asistencia");
                const remaining = Math.max(0, Math.ceil((qr.expiresAt - Date.now()) / 1000));
                qrExpiry.textContent = `Código vigente por ${remaining} segundos. Se actualizará automáticamente.`;
                locationStatus.textContent = "Ubicación configurada. El QR está activo para el personal presente en el local.";
            } catch (error) {
                qrCode.replaceChildren(); qrExpiry.textContent = error.message || "No se pudo generar el QR.";
            }
        };
        try {
            const config = await apiRequest("/api/asistencia/qr/config");
            if (revision !== screenRevision) return;
            if (!config.gpsSchemaReady || !config.qrTableReady) {
                qrSetup.hidden = true; qrDisplay.hidden = true;
                locationStatus.textContent = "Para activar el QR y guardar la ubicación de cada marca, primero ejecuta la migración 10_asistencia_qr_ubicacion.sql en Oracle.";
            } else if (config.configured) {
                radiusInput.value = String(config.radiusMeters); qrSetup.hidden = true; qrDisplay.hidden = false;
                await refreshAttendanceQr();
                attendanceQrTimer = setInterval(() => void refreshAttendanceQr(), 15000);
            }
        } catch (error) { locationStatus.textContent = error.message || "No se pudo cargar la configuración del QR."; }
        }
    }
    if (canManageAttendanceScheduleRole()) {
        const schedulePanel = checkinNode("section", "attendance-qr-admin attendance-schedule-admin");
        const scheduleHeading = checkinNode("div", "attendance-schedule-heading");
        scheduleHeading.appendChild(checkinNode("h4", "", "Cargar horario de asistencia"));
        const help = document.createElement("details"); help.className = "attendance-schedule-help";
        const helpTitle = document.createElement("summary"); helpTitle.textContent = "¿Cómo funciona?";
        help.append(helpTitle, checkinNode("p", "attendance-qr-help", "Sube el Excel (.xlsx o .xls). Para un ciclo de dos semanas, usa una fila por trabajador y encabezados Semana 1 - Lunes a Domingo y Semana 2 - Lunes a Domingo. También se aceptan semanas fijas y tablas con fecha y turno. Escribe LIBRE en cada día sin turno. Elige el área y la vigencia; para el ciclo de dos semanas, la fecha inicial debe ser lunes y se guardan solo los 14 días del patrón, que se repite durante la vigencia. Los nombres activos del área se vinculan automáticamente; solo tendrás que asignar los que no coincidan."));
        schedulePanel.append(scheduleHeading, help);
        const scheduleActions = checkinNode("div", "attendance-qr-setup");
        const areaSelect = checkinNode("select", "attendance-filter"); areaSelect.setAttribute("aria-label", "Área del horario");
        [["COCINA", "Cocina"], ["SALON", "Salón"], ["BARRA", "Barra"], ["COPERIA", "Copería"]].forEach(([value, label]) => areaSelect.add(new Option(label, value)));
        const assignedScheduleArea = attendanceScheduleAreaForRole();
        if (assignedScheduleArea) { areaSelect.value = assignedScheduleArea; areaSelect.disabled = true; }
        const startLabel = checkinNode("label", "attendance-radius-label", "Vigente desde");
        const startDate = checkinNode("input", "attendance-rut"); startDate.type = "date"; startDate.setAttribute("aria-label", "Fecha de inicio de vigencia del horario");
        const todayLocal = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Santiago" });
        const defaultStart = new Date(`${todayLocal}T12:00:00Z`);
        const currentWeekday = defaultStart.getUTCDay() || 7;
        defaultStart.setUTCDate(defaultStart.getUTCDate() + ((8 - currentWeekday) % 7));
        startDate.value = defaultStart.toISOString().slice(0, 10);
        startLabel.appendChild(startDate);
        const endLabel = checkinNode("label", "attendance-radius-label", "Vigente hasta");
        const endDate = checkinNode("input", "attendance-rut"); endDate.type = "date"; endDate.setAttribute("aria-label", "Fecha de término de vigencia del horario"); endLabel.appendChild(endDate);
        const fileLabel = checkinNode("label", "attendance-radius-label", "Archivo Excel del horario (.xlsx o .xls)");
        const fileInput = checkinNode("input", "attendance-schedule-file"); fileInput.type = "file"; fileInput.accept = ".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"; fileInput.setAttribute("aria-label", "Seleccionar horario Excel"); fileLabel.appendChild(fileInput);
        const previewSchedule = checkinButton("Leer y revisar horario", async () => {
            const file = fileInput.files?.[0];
            if (!file) { feedback.textContent = "Selecciona el Excel del horario."; return; }
            const body = new FormData(); body.append("planilla", file); body.append("area", areaSelect.value);
            if (startDate.value) body.append("vigenteDesde", startDate.value);
            if (endDate.value) body.append("vigenteHasta", endDate.value);
            previewSchedule.disabled = true; previewWrap.replaceChildren();
            try {
                const result = await uploadRequest("/api/asistencia/horarios/previsualizar", body);
                const rows = result.turnos || [], mappings = new Map(), mappingSelects = new Set();
                const employees = await apiRequest(employeesApi);
                const biweekly = result.tipoCiclo === "ROTACION_2_SEMANAS";
                const previewTitle = checkinNode("h5", "", biweekly
                    ? `Vista previa · ${result.personas} personas · ciclo de 14 días · vigencia ${result.vigenteDesde} a ${result.vigenteHasta}`
                    : `Vista previa · ${result.personas} personas · ${rows.length} turnos · ${result.vigenteDesde} a ${result.vigenteHasta}`);
                const previewHint = checkinNode("p", "attendance-qr-help", "Los trabajadores activos del área se reconocen por su nombre. Si alguno no se vinculó, asígnalo una vez en su primera fila. Revisa turnos y días libres antes de confirmar.");
                const tableWrap = checkinNode("div", "table-container attendance-table-wrap"), table = checkinNode("table"), head = checkinNode("tr"), bodyRows = checkinNode("tbody");
                (biweekly ? ["Trabajador", "Semana", "Día", "Entrada", "Salida", "Estado"] : ["Trabajador", "Fecha", "Entrada", "Salida", "Estado"]).forEach(label => head.appendChild(checkinNode("th", "", label)));
                const thead = checkinNode("thead"); thead.appendChild(head); table.append(thead, bodyRows); tableWrap.appendChild(table);
                for (const shift of rows) {
                    const tr = checkinNode("tr"), workerCell = checkinNode("td");
                    const workerName = String(shift.trabajador || shift.rut || "Sin identificar");
                    if (!mappings.has(workerName)) mappings.set(workerName, shift.empleadoId || "");
                    const select = checkinNode("select", "attendance-filter"); select.add(new Option("Seleccionar trabajador…", ""));
                    employees.filter(person => person.active !== false && (!person.area || person.area.toUpperCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "") === areaSelect.value)).forEach(person => select.add(new Option(`${person.name || person.nombre}${person.rut ? ` · ${person.rut}` : ""}`, person.id || person.idEmpleado)));
                    select.value = String(mappings.get(workerName) || "");
                    select.addEventListener("change", () => { mappings.set(workerName, select.value); previewWrap.dataset.mappings = JSON.stringify(Object.fromEntries(mappings)); });
                    if (shift.empleadoId) workerCell.textContent = workerName;
                    else if (!mappingSelects.has(workerName)) { workerCell.append(checkinNode("span", "", `${workerName} `), select); mappingSelects.add(workerName); }
                    else workerCell.textContent = workerName;
                    if (biweekly) tr.append(workerCell, checkinNode("td", "", `Semana ${shift.semanaCiclo}`), checkinNode("td", "", shift.diaNombre), checkinNode("td", "", shift.entrada || "—"), checkinNode("td", "", shift.salida || "—"), checkinNode("td", "", shift.estado === "LIBRE" ? "Libre" : "Turno"));
                    else tr.append(workerCell, checkinNode("td", "", shift.fecha), checkinNode("td", "", shift.entrada || "—"), checkinNode("td", "", shift.salida || "—"), checkinNode("td", "", shift.estado === "LIBRE" ? "Libre" : "Turno"));
                    tr.dataset.worker = workerName; bodyRows.appendChild(tr);
                }
                previewWrap.dataset.mappings = JSON.stringify(Object.fromEntries(mappings));
                const confirmSchedule = checkinButton("Confirmar y guardar horario", async () => {
                    const selectedMappings = JSON.parse(previewWrap.dataset.mappings || "{}");
                    const unresolved = [...new Set(rows.filter(row => !row.empleadoId).map(row => row.trabajador))].filter(name => !selectedMappings[name]);
                    if (unresolved.length) { feedback.textContent = `Asigna cada nombre antes de guardar: ${unresolved.join(", ")}.`; return; }
                    const payload = { area: result.area, tipoCiclo: result.tipoCiclo, vigenteDesde: result.vigenteDesde, vigenteHasta: result.vigenteHasta, turnos: rows.map(row => ({ ...row, empleadoId: row.empleadoId || selectedMappings[row.trabajador] })) };
                    confirmSchedule.disabled = true;
                    try {
                        const saved = await apiRequest("/api/asistencia/horarios/confirmar", { method: "POST", body: JSON.stringify(payload) });
                        feedback.textContent = `Horario guardado: ${saved.personas} personas y ${saved.turnos} turnos. Vigencia ${saved.vigenteDesde} a ${saved.vigenteHasta}.`;
                        previewWrap.replaceChildren(); await loadAttendanceReport(section, controls.period.value, controls.from.value, controls.to.value, "", controls.area.value, feedback);
                    } catch (error) { feedback.textContent = error.message || "No se pudo guardar el horario."; }
                    finally { confirmSchedule.disabled = false; }
                }, "primary-button");
                previewWrap.append(previewTitle, previewHint, tableWrap, confirmSchedule);
                feedback.textContent = "Vista previa lista. Revisa el contenido antes de guardarlo.";
            } catch (error) { feedback.textContent = error.message || "No se pudo leer el horario."; }
            finally { previewSchedule.disabled = false; }
        }, "primary-button");
        const previewWrap = checkinNode("div", "attendance-schedule-preview");
        const clearSelectedFile = () => { previewWrap.replaceChildren(); };
        areaSelect.addEventListener("change", clearSelectedFile); fileInput.addEventListener("change", clearSelectedFile);
        scheduleActions.append(areaSelect, startLabel, endLabel, fileLabel, previewSchedule); schedulePanel.append(scheduleActions, previewWrap);
        profileCard.appendChild(schedulePanel);
    }
    section.append(profileCard, feedback);
    const controls = { period: checkinNode("select", "attendance-filter"), from: checkinNode("input", "attendance-filter"), to: checkinNode("input", "attendance-filter"), area: checkinNode("select", "attendance-filter"), employee: checkinNode("select", "attendance-filter") };
    controls.period.add(new Option("Día", "dia")); controls.period.add(new Option("Semana", "semana")); controls.period.add(new Option("Mes", "mes")); controls.period.value = "mes";
    const today = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Santiago" });
    controls.from.type = controls.to.type = "date";
    controls.from.setAttribute("aria-label", "Fecha desde"); controls.to.setAttribute("aria-label", "Fecha hasta");
    const isoDate = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    const setPeriodDates = (period, anchorText) => {
        const anchor = new Date(`${anchorText || today}T12:00:00`);
        let start = new Date(anchor), end = new Date(anchor);
        if (period === "semana") { start.setDate(start.getDate() - ((start.getDay() + 6) % 7)); end = new Date(start); end.setDate(end.getDate() + 6); }
        else if (period === "mes") { start.setDate(1); end = new Date(start.getFullYear(), start.getMonth() + 1, 0); }
        controls.from.value = isoDate(start); controls.to.value = isoDate(end);
    };
    setPeriodDates("mes", today);
    const filterRow = checkinNode("div", "attendance-filters"); filterRow.append(checkinNode("label", "", "Período"), controls.period, checkinNode("label", "", "Desde"), controls.from, checkinNode("label", "", "Hasta"), controls.to);
    if (admin) {
        controls.area.add(new Option("Todas las áreas", ""));
        [["COCINA", "Cocina"], ["SALON", "Salón"], ["BARRA", "Barra"], ["COPERIA", "Copería"]].forEach(([value, label]) => controls.area.add(new Option(label, value)));
        controls.employee.add(new Option("Todo el personal", ""));
        controls.area.setAttribute("aria-label", "Filtrar por área"); controls.employee.setAttribute("aria-label", "Filtrar por trabajador");
        filterRow.append(checkinNode("label", "", "Área"), controls.area, checkinNode("label", "", "Trabajador"), controls.employee);
    }
    let attendancePeople = [];
    const populateAttendancePeople = () => {
        if (!admin) return;
        const selected = controls.employee.value, area = controls.area.value;
        controls.employee.replaceChildren(new Option("Todo el personal", ""));
        attendancePeople.filter(person => !area || String(person.area || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().includes(area))
            .forEach(person => controls.employee.add(new Option(person.nombre, person.id)));
        controls.employee.value = [...controls.employee.options].some(option => option.value === selected) ? selected : "";
    };
    const reload = async () => loadAttendanceReport(section, controls.period.value, controls.from.value, controls.to.value, admin ? controls.employee.value : "", admin ? controls.area.value : "", feedback);
    controls.period.addEventListener("change", () => { setPeriodDates(controls.period.value, controls.from.value || today); void reload(); });
    controls.from.addEventListener("change", () => void reload()); controls.to.addEventListener("change", () => void reload());
    controls.area.addEventListener("change", () => { controls.employee.value = ""; populateAttendancePeople(); void reload(); }); controls.employee.addEventListener("change", () => void reload());
    const download = checkinButton("Descargar CSV", async () => {
        try {
            const report = await apiRequest(`/api/asistencia/reporte?periodo=${encodeURIComponent(controls.period.value)}&desde=${encodeURIComponent(controls.from.value)}&hasta=${encodeURIComponent(controls.to.value)}${admin && controls.employee.value ? `&empleadoId=${encodeURIComponent(controls.employee.value)}` : ""}`);
            const areaKey = admin ? String(controls.area.value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase() : "";
            const exportRows = (report.marcas || []).filter(row => !areaKey || String(row.area || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().includes(areaKey));
            const csvHeaders = qrAdmin
                ? ["Nombre", "RUT", "Fecha", "Área", "Turno programado", "Estado del horario", "Hora de entrada", "Diferencia de entrada (min)", "Hora de salida", "Diferencia de salida (min)", "Horas previstas", "Horas trabajadas"]
                : ["Nombre", "RUT", "Fecha", "Hora de entrada", "Hora de salida", "Horas trabajadas"];
            const lines = [csvHeaders, ...exportRows.map(row => qrAdmin
                ? [row.nombre, row.rut, row.fecha, row.area, row.turnoProgramado, row.estadoHorario, row.entrada, row.minutosEntrada, row.salida, row.minutosSalida, row.horasProgramadas, row.horasTrabajadas || "Pendiente"]
                : [row.nombre, row.rut, row.fecha, row.entrada, row.salida, row.horasTrabajadas || "Pendiente"])];
            const csv = lines.map(line => line.map(value => { const text = String(value ?? ""); const safe = /^[=+@\-]/.test(text) ? "'" + text : text; return `"${safe.replaceAll('"', '""')}"`; }).join(";")).join("\r\n");
            const url = URL.createObjectURL(new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" })); const link = document.createElement("a"); link.href = url; link.download = `asistencia-${report.desde}-${report.hasta}.csv`; link.click(); URL.revokeObjectURL(url);
        } catch (error) { feedback.textContent = error.message; }
    });
    section.append(filterRow, download);
    const summary = checkinNode("div", "attendance-summary"); summary.dataset.attendanceSummary = "true"; section.appendChild(summary);
    const tableWrap = checkinNode("div", `table-container attendance-table-wrap${qrAdmin ? " attendance-schedule-report" : ""}`), table = checkinNode("table"), thead = checkinNode("thead"), head = checkinNode("tr");
    const tableHeaders = ["Trabajador", "RUT", "Fecha"];
    if (qrAdmin) tableHeaders.push("Área", "Turno programado", "Estado horario");
    tableHeaders.push("Entrada", "Salida");
    if (qrAdmin) tableHeaders.push("Horas previstas", "Horas trabajadas", "Dif. entrada (min)", "Dif. salida (min)");
    if (admin) tableHeaders.push("Ubicación");
    tableHeaders.forEach(label => head.appendChild(checkinNode("th", "", label))); thead.appendChild(head); table.append(thead, checkinNode("tbody")); tableWrap.appendChild(table); section.appendChild(tableWrap);
    otherModule.appendChild(section);
    await loadAttendanceReport(section, controls.period.value, controls.from.value, controls.to.value, "", admin ? controls.area.value : "", feedback);
    if (admin) {
        try {
            const data = await apiRequest(`/api/asistencia/reporte?periodo=${encodeURIComponent(controls.period.value)}&desde=${encodeURIComponent(controls.from.value)}&hasta=${encodeURIComponent(controls.to.value)}`);
            attendancePeople = data.personas || []; populateAttendancePeople();
        } catch (error) { feedback.textContent = error.message; }
    }
}

async function loadAttendanceReport(section, period, from, to, employeeId, area, feedback) {
    try {
        const suffix = employeeId ? `&empleadoId=${encodeURIComponent(employeeId)}` : "";
        const report = await apiRequest(`/api/asistencia/reporte?periodo=${encodeURIComponent(period)}&desde=${encodeURIComponent(from)}&hasta=${encodeURIComponent(to)}${suffix}`);
        const areaKey = String(area || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase();
        const rows = (report.marcas || []).filter(row => !areaKey || String(row.area || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().includes(areaKey));
        const summary = section.querySelector("[data-attendance-summary]"); summary.replaceChildren();
        if (isAdminRole()) {
            summary.appendChild(checkinNode("h3", "Resumen por persona · " + report.desde + " a " + report.hasta));
            const wrap = checkinNode("div", "table-container attendance-summary-table-wrap"), table = checkinNode("table"), head = checkinNode("tr"), body = checkinNode("tbody");
            ["Trabajador", "Turnos completos", "Puntuales", "Tardanzas", "Salidas anticipadas", "Horas programadas", "Horas trabajadas", "Saldo vs. horario"].forEach(label => head.appendChild(checkinNode("th", "", label)));
            const thead = checkinNode("thead"); thead.appendChild(head); table.append(thead, body); wrap.appendChild(table); summary.appendChild(wrap);
            const totals = new Map(), today = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Santiago" });
            rows.forEach(row => {
                if (String(row.fecha || "") > today) return;
                const key = String(row.empleadoId || row.nombre);
                if (!totals.has(key)) totals.set(key, { completos: 0, puntuales: 0, tarde: 0, salidasTemprano: 0, programadas: 0, trabajadas: 0 });
                const total = totals.get(key), scheduled = String(row.turnoProgramado || "").toUpperCase(), status = String(row.estadoHorario || "").toUpperCase();
                if (scheduled && scheduled !== "LIBRE" && scheduled !== "—") total.programadas += Number(row.horasProgramadas || 0);
                if (scheduled && scheduled !== "LIBRE" && row.entrada && row.salida) total.completos++;
                if (status.startsWith("PUNTUAL")) total.puntuales++;
                if (status.startsWith("TARDE")) total.tarde++;
                if (status.includes("SALIDA ANTICIPADA")) total.salidasTemprano++;
                const hours = String(row.horasTrabajadas || "").match(/(\d+)\s*h\s*(\d+)\s*min/i);
                if (hours) total.trabajadas += Number(hours[1]) + Number(hours[2]) / 60;
            });
            const people = (report.resumen || []).filter(person => {
                if (!areaKey) return true;
                const personArea = person.area || rows.find(row => String(row.empleadoId) === String(person.empleadoId))?.area || "";
                return String(personArea).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().includes(areaKey);
            });
            people.forEach(person => {
                const total = totals.get(String(person.empleadoId)) || { completos: 0, puntuales: 0, tarde: 0, salidasTemprano: 0, programadas: 0, trabajadas: 0 };
                const tr = checkinNode("tr");
                const balance = total.trabajadas - total.programadas;
                [person.nombre, total.completos, total.puntuales, total.tarde, total.salidasTemprano, total.programadas.toFixed(1) + " h", total.trabajadas.toFixed(1) + " h", (balance > 0 ? "+" : "") + balance.toFixed(1) + " h"].forEach(value => tr.appendChild(checkinNode("td", "", String(value))));
                body.appendChild(tr);
            });
        }
        const tbody = section.querySelector(".attendance-table-wrap tbody"); tbody.replaceChildren();
        rows.forEach(row => {
            const tr = checkinNode("tr"), cells = [row.nombre, row.rut, row.fecha];
            if (isAttendanceQrAdminRole()) cells.push(row.area || "—", row.turnoProgramado || "—", row.estadoHorario || "—");
            cells.push(row.entrada || "—", row.salida || "—");
            if (isAttendanceQrAdminRole()) cells.push(row.horasProgramadas == null ? "—" : row.horasProgramadas, row.horasTrabajadas || "—", row.minutosEntrada == null ? "—" : row.minutosEntrada, row.minutosSalida == null ? "—" : row.minutosSalida);
            cells.forEach(value => tr.appendChild(checkinNode("td", "", value)));
            if (isAdminRole()) {
                const location = checkinNode("td", "attendance-location-cell");
                [["Entrada", row.entradaLat, row.entradaLon, row.entradaDistancia, row.entradaPrecision], ["Salida", row.salidaLat, row.salidaLon, row.salidaDistancia, row.salidaPrecision]].forEach(([label, lat, lon, distance, accuracy]) => {
                    if (lat == null || lon == null) return;
                    const link = document.createElement("a"); link.href = `https://maps.google.com/?q=${encodeURIComponent(`${lat},${lon}`)}`; link.target = "_blank"; link.rel = "noopener noreferrer";
                    link.textContent = `${label}: validada · ${Math.round(Number(distance))} m (±${Math.round(Number(accuracy))} m)`; location.append(link, document.createElement("br"));
                });
                if (!location.childElementCount) location.textContent = "Sin datos de ubicación";
                tr.appendChild(location);
            }
            tbody.appendChild(tr);
        });
        if (!rows.length) feedback.textContent = `No hay marcas entre ${report.desde} y ${report.hasta}.`;
    } catch (error) { feedback.textContent = error.message; }
}

const moduleKey = (name) => `restaurant-erp-module-${name}`;
const moduleSchemas = {
    Asistencia: ["Trabajador", "Fecha", "Hora de entrada", "Hora de salida", "Estado"],
    Horarios: ["Trabajador", "Fecha", "Hora de inicio", "Hora de término", "Área"],
"Tareas diarias": ["Tarea", "Responsable", "Fecha", "Estado", "Área", "Método"],
Inventario: [
    "Insumo",
    "Categoría",
    "Stock actual",
    "Unidad",
    "Stock mínimo",
    "Stock máximo",
    "Grupo"
],
    Cocina: ["Preparación", "Responsable", "Hora", "Estado"],
    "Producción": ["Preparación", "Cantidad", "Unidad", "Responsable", "Fecha"],
    Reservas: ["Cliente", "Fecha", "Hora", "Personas", "Contacto", "Estado"],
    Limpieza: ["Área", "Tarea", "Responsable", "Frecuencia", "Estado"],
    Incidencias: ["Tipo", "Área", "Descripción", "Responsable", "Estado"],
    Compras: ["Producto", "Cantidad", "Proveedor", "Fecha", "Estado"],
    Mermas: ["Producto", "Cantidad", "Unidad", "Motivo", "Fecha"],
    "Control sanitario": ["Punto de control", "Temperatura (°C)", "Fecha y hora", "Responsable", "Estado"],
    Reportes: ["Nombre del reporte", "Período", "Fecha", "Estado"],
    Configuración: ["Opción", "Valor", "Descripción"]
};
function getInventoryVisualState(values) {
    const readNumber = (value) => {
        if (value === null || value === undefined
                || String(value).trim() === "") {
            return null;
        }

        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    };

    const current = readNumber(values[2]);
    const minimum = readNumber(values[4]);
    const maximum = readNumber(values[5]);

    const hasMaximum = values[5] !== null
        && values[5] !== undefined
        && String(values[5]).trim() !== "";

    if (
        current === null
        || minimum === null
        || current < 0
        || minimum < 0
        || (hasMaximum && (maximum === null || maximum < minimum))
    ) {
        return {
            key: "unknown",
            text: "— Revisa las cantidades"
        };
    }

    if (current < minimum) {
        return {
            key: "low",
            text: "⚠️ Stock bajo"
        };
    }

    if (maximum !== null && current > maximum) {
        return {
            key: "high",
            text: "🔴 Sobre stock"
        };
    }

    return {
        key: "ok",
        text: "✅ Stock bien"
    };
}

function createInventoryStateBadge(values) {
    const state = getInventoryVisualState(values);
    const badge = document.createElement("span");

    badge.className = `inventory-state inventory-state--${state.key}`;
    badge.textContent = state.text;

    return badge;
}

async function renderModule(name, selectedArea = null) {
    if (name === "Asistencia") return renderAttendanceModule();
    const revision = ++screenRevision;
    otherModule.replaceChildren();
    const slug = slugifyLabel(name);
    otherModule.className = `module-screen module-screen--${slug}`;
    const accents = {
        asistencia: "#0891b2", turnos: "#7c3aed", "tareas-diarias": "#2563eb", inventario: "#059669",
        cocina: "#ea580c", produccion: "#d97706", recetas: "#0d9488", reservas: "#db2777",
        limpieza: "#0284c7", incidencias: "#dc2626", compras: "#4f46e5", mermas: "#64748b",
        "control-sanitario": "#16a34a", reportes: "#9333ea", configuracion: "#475569"
    };
    otherModule.style.setProperty("--module-accent", accents[slug] || "#2563eb");
    const icons = {
        asistencia: "🕒", turnos: "📅", "tareas-diarias": "✅", inventario: "📦", cocina: "👨‍🍳",
        produccion: "🍳", recetas: "📖", reservas: "🗓️", limpieza: "🧹", incidencias: "⚠️",
        compras: "🛒", mermas: "♻️", "control-sanitario": "🌡️", reportes: "📊", configuracion: "⚙️"
    };
    const header = document.createElement("div"); header.className = "module-header";
    const title = document.createElement("h2"); title.textContent = selectedArea ? `${name} · ${areaLabel(selectedArea)}` : name;
    const descriptions = {
        Asistencia: "Registra entradas, salidas y estado de asistencia del equipo.",
        Horarios: "Organiza los horarios y áreas asignadas al personal.",
        "Tareas diarias": "Asigna y revisa las tareas operativas del día.",
        Inventario: "Controla existencias y niveles mínimos de insumos.",
        Cocina: "Da seguimiento a las preparaciones activas.",
        Producción: "Registra cantidades preparadas y responsables.",
        Recetas: "Registra las recetas de los productos para vincularlas con el inventario y el cierre de turno.",
        Reservas: "Administra clientes, horarios y cantidad de personas.",
        Limpieza: "Organiza las tareas de limpieza por área.",
        Incidencias: "Registra problemas y su estado de resolución.",
        Compras: "Da seguimiento a productos y pedidos a proveedores.",
        Mermas: "Registra productos descartados y sus motivos.",
        "Control sanitario": "Anota temperaturas y controles sanitarios.",
        Reportes: "Mantén un registro de los reportes del restaurante.",
        Configuración: "Guarda preferencias y datos básicos del sistema."
    };
    const description = document.createElement("p"); description.textContent = descriptions[name] || `Gestión de ${name.toLocaleLowerCase("es")}.`;
    const heading = document.createElement("div"); heading.className = "module-heading";
    const icon = document.createElement("span"); icon.className = "module-icon"; icon.textContent = icons[slug] || "📋"; icon.setAttribute("aria-hidden", "true");
    const copy = document.createElement("div"); copy.append(title, description); heading.append(icon, copy);
    if (name === "Horarios") {
        const scheduleHost = document.createElement("div");
        scheduleHost.className = "schedule-module-content";
        otherModule.append(scheduleHost);
        otherModule.hidden = false;
        await renderSchedules(scheduleHost);
        return;
    }
    const add = document.createElement("button"); add.className = "primary-button"; add.type = "button"; add.textContent = `+ ${name === "Horarios" ? "Crear horario por trabajador" : name === "Asistencia" ? "Registrar asistencia" : "Agregar registro"}`;
    const importInventory = document.createElement("button");
importInventory.className = "secondary-button";
importInventory.type = "button";
importInventory.textContent = "Importar";
importInventory.hidden = name !== "Inventario" || !canManageModule(slug);
importInventory.addEventListener("click", () => {
    document.getElementById("inventoryImportDialog")?.showModal();
});
    if (!canManageModule(slug)) {
        add.disabled = true; add.title = "Solo administración puede gestionar estos registros";
    }
    if (selectedArea === "SIN_AREA") add.hidden = true;
  header.append(heading, add, importInventory);
    const section = document.createElement("div"); section.className = "data-section generic-data";
    const controls = document.createElement("div"); controls.className = "module-controls";
    const search = document.createElement("input"); search.type = "search"; search.placeholder = `Buscar en ${name.toLocaleLowerCase("es")}...`; search.setAttribute("aria-label", `Buscar en ${name}`);
    const form = document.createElement("form"); form.className = "record-form"; form.hidden = true;
    const formFields = document.createElement("div"); formFields.className = "record-form-fields";
    const fields = moduleSchemas[name] || ["Nombre", "Descripción", "Estado"];
const inputs = fields.map((label) => {
    const wrap = document.createElement("label");
    wrap.textContent = label;

    const isArea = slug === "tareas-diarias" && label === "Área";
    const isMethod = slug === "tareas-diarias" && label === "Método";
    const isEmployee = ["Trabajador", "Responsable"].includes(label);
    const isInventoryGroup = slug === "inventario" && label === "Grupo";
    const isInventoryStock = slug === "inventario"
        && ["Stock actual", "Stock mínimo", "Stock máximo"].includes(label);

    const input = document.createElement(
        isArea || isMethod || isEmployee || isInventoryGroup
            ? "select"
            : "input"
    );

    if (isInventoryGroup) {
        input.add(new Option("Selecciona un grupo", "", true, true));
        input.add(new Option("Cocina", "COCINA"));
        input.add(new Option("Barra", "BARRA"));
    } else if (isMethod) {
        input.add(new Option("Enviar foto", "FOTO", true, true));
        input.add(new Option("Marcar como hecha", "SIMPLE"));
    } else if (isArea) {
        AREA_OPTIONS.forEach((area) => {
            input.add(new Option(area.label, area.key));
        });
        input.value = selectedArea === "SIN_AREA" ? "" : selectedArea;
        input.disabled = selectedArea !== "SIN_AREA";
    } else if (isEmployee) {
        input.dataset.employeeReference = "true";
        input.add(new Option("Selecciona una persona", ""));
    }

    if (input.tagName !== "SELECT") {
        input.type = /fecha y hora/i.test(label)
            ? "datetime-local"
            : /fecha/i.test(label)
                ? "date"
                : /hora/i.test(label)
                    ? "time"
                    : /cantidad|stock|personas|temperatura/i.test(label)
                        ? "number"
                        : "text";
    }

    if (input.type === "number") {
        input.step = "any";
        input.min = "0";
    }

    input.required = label !== "Stock máximo";

    if (label === "Stock máximo") {
        input.placeholder = "Opcional";
    }

    input.setAttribute("aria-label", label);

    if (isInventoryStock) {
        const quantity = document.createElement("span");
        quantity.className = "inventory-quantity";
        input.inputMode = "decimal";

        const adjust = (delta) => {
            if (!canManageModule("inventario")) return;

            const current = input.value === "" ? 0 : input.valueAsNumber;
            if (!Number.isFinite(current)) {
                input.reportValidity();
                return;
            }

            input.value = String(
                Number(Math.max(0, current + delta).toFixed(6))
            );
            input.dispatchEvent(new Event("input", { bubbles: true }));
        };

        const minus = actionButton("−", () => adjust(-1));
        const plus = actionButton("+", () => adjust(1));

        minus.setAttribute("aria-label", `Disminuir ${label}`);
        plus.setAttribute("aria-label", `Aumentar ${label}`);
        minus.disabled = plus.disabled = !canManageModule("inventario");

        input.addEventListener("input", () => {
            if (Number.isFinite(input.valueAsNumber)
                    && input.valueAsNumber < 0) {
                input.value = "0";
            }
        });

        quantity.append(minus, input, plus);
        wrap.appendChild(quantity);
    } else {
        wrap.appendChild(input);
    }

    if (slug === "inventario" && label === "Unidad") {
        const units = document.createElement("datalist");
        units.id = "inventory-unit-options";

        [
            "kg", "gramos", "litros", "ml", "unidades",
            "cajas", "botellas", "paquetes", "bolsas"
        ].forEach((unit) => {
            const option = document.createElement("option");
            option.value = unit;
            units.appendChild(option);
        });

        input.setAttribute("list", units.id);
        input.maxLength = 20;
        input.placeholder = "Selecciona o escribe una unidad";
        wrap.appendChild(units);
    }

    formFields.appendChild(wrap);
    return input;
});
    const employeeSelects = inputs.filter((input) => input.dataset.employeeReference);
    if (employeeSelects.length) {
        if (isWorkerRole()) {
            employeeSelects.forEach((select) => select.add(new Option(currentProfile.name, currentProfile.employeeId)));
        } else {
            try {
                const employees = await apiRequest(employeesApi);
                employeeSelects.forEach((select) => employees.filter((employee) => slug !== "tareas-diarias" || selectedArea === "SIN_AREA" || normalizeArea(employee.area) === selectedArea).forEach((employee) => select.add(new Option(employee.name, employee.id))));
            } catch (error) {
                alert(`No se pudieron cargar los trabajadores: ${error.message}`);
            }
        }
        if (isWorkerRole()) employeeSelects.forEach((select) => { select.value = String(currentProfile.employeeId); select.disabled = true; });
    }
    const restoreFixedFields = () => {
        if (slug === "tareas-diarias" && selectedArea !== "SIN_AREA") inputs[4].value = selectedArea;
        if (isWorkerRole()) {
            employeeSelects.forEach((input) => input.value = String(currentProfile.employeeId));
            if (slug === "incidencias") {
                inputs[1].value = normalizeArea(currentProfile.area); inputs[1].disabled = true;
                inputs[4].value = "PENDIENTE"; inputs[4].disabled = true;
            }
        }
    };
    restoreFixedFields();
    const submit = document.createElement("button"); submit.type = "submit"; submit.className = "primary-button"; submit.textContent = "Guardar";
    const cancel = document.createElement("button"); cancel.type = "button"; cancel.className = "secondary-button"; cancel.textContent = "Cancelar";
    form.append(formFields, submit, cancel);
    let inventoryStatePreview = null;

const refreshInventoryStatePreview = () => {
    if (!inventoryStatePreview) return;

    inventoryStatePreview.replaceChildren(
        document.createTextNode("Estado al guardar: "),
        createInventoryStateBadge(
            inputs.map((input) => input.value)
        )
    );
};

if (slug === "inventario") {
    inventoryStatePreview = document.createElement("p");
    inventoryStatePreview.className = "inventory-state-preview";
    inventoryStatePreview.setAttribute("role", "status");
    inventoryStatePreview.setAttribute("aria-live", "polite");

    formFields.after(inventoryStatePreview);

    [inputs[2], inputs[4], inputs[5]].forEach((input) => {
        input.addEventListener("input", refreshInventoryStatePreview);
    });

    refreshInventoryStatePreview();
}
    const tableWrap = document.createElement("div"); tableWrap.className = "table-container";
    const table = document.createElement("table");
    const thead = document.createElement("thead"); const headRow = document.createElement("tr");
fields
    .filter((label) => !(slug === "inventario" && label === "Grupo"))
    .concat(slug === "inventario" ? ["Estado", "Acciones"] : ["Acciones"])
    .forEach((label) => {
        const th = document.createElement("th");
        th.textContent = label;
        headRow.appendChild(th);
    });
    thead.appendChild(headRow); const tbody = document.createElement("tbody"); table.append(thead, tbody); tableWrap.appendChild(table);
    const empty = document.createElement("p"); empty.className = "empty-state"; empty.textContent = "Todavía no hay registros. Usa el botón para agregar el primero.";
    if (selectedArea) controls.append(actionButton("← Áreas de tareas", renderTaskAreas));
    controls.append(search); section.append(controls, form, tableWrap, empty);
    if (revision !== screenRevision) return;
    otherModule.append(header, section); otherModule.hidden = false;
    let records = [];
let loadError = "";
let inventoryGroupFilter = null;

if (slug === "inventario") {
    const groupLabel = document.createElement("label");
    groupLabel.textContent = "Inventario: ";

    inventoryGroupFilter = document.createElement("select");
    inventoryGroupFilter.setAttribute("aria-label", "Grupo de inventario");
    inventoryGroupFilter.add(new Option("Cocina", "COCINA"));
    inventoryGroupFilter.add(new Option("Barra", "BARRA"));
    inventoryGroupFilter.add(
        new Option("Pendientes de clasificar", "SIN_GRUPO")
    );

    inventoryGroupFilter.addEventListener("change", () => {
        form.reset();
        delete form.dataset.editing;
        form.hidden = true;
        draw();
    });

    groupLabel.appendChild(inventoryGroupFilter);
    controls.prepend(groupLabel);
    const inventoryFeedback = document.createElement("p");
inventoryFeedback.className = "empty-state";
inventoryFeedback.setAttribute("role", "status");
inventoryFeedback.hidden = true;

const sendInventoryButton = actionButton(
    "Enviar inventario",
    async () => {
        if (!canManageModule("inventario")) return;

        const group = inventoryGroupFilter?.value;
        if (group !== "COCINA" && group !== "BARRA") {
            inventoryFeedback.textContent =
                "Selecciona Cocina o Barra antes de enviar.";
            inventoryFeedback.hidden = false;
            return;
        }

        const groupName = group === "COCINA" ? "Cocina" : "Barra";
        if (!confirm(`¿Enviar el inventario completo de ${groupName}?`)) {
            return;
        }

        sendInventoryButton.disabled = true;
        inventoryFeedback.hidden = true;

        try {
            const result = await apiRequest("/api/inventario/enviar", {
                method: "POST",
                body: JSON.stringify({ group })
            });

            inventoryFeedback.textContent = result.message;
            inventoryFeedback.hidden = false;

            if (result.sent) {
                await loadUserNotifications();
            }
        } catch (error) {
            inventoryFeedback.textContent =
                `No se pudo enviar el inventario: ${error.message}`;
            inventoryFeedback.hidden = false;
        } finally {
            sendInventoryButton.disabled =
                !canManageModule("inventario");
        }
    },
    true
);

sendInventoryButton.disabled = !canManageModule("inventario");
controls.append(sendInventoryButton);
section.append(inventoryFeedback);
}

const draw = () => {
        tbody.replaceChildren();
        const term = search.value.trim().toLocaleLowerCase("es");

const filtered = records
    .map((record, index) => ({ record, index }))
    .filter(({ record }) => {
        const matchesText = record.values
            .join(" ")
            .toLocaleLowerCase("es")
            .includes(term);

        if (!matchesText) return false;
        if (slug !== "inventario") return true;

        const group = String(record.values[6] || "")
            .trim()
            .toUpperCase();

        const selected = inventoryGroupFilter.value;

        return selected === "SIN_GRUPO"
            ? group === ""
            : group === selected;
    });
        filtered.forEach(({ record, index }) => {
            const row = document.createElement("tr");
        const stockInputs = [];

record.values.forEach((value, columnIndex) => {
    if (slug === "inventario" && columnIndex === 6) return;

    const td = document.createElement("td");
    const esStockInventario =
        slug === "inventario" && [2, 4, 5].includes(columnIndex);

    if (!esStockInventario) {
        td.textContent = value;
        row.appendChild(td);
        return;
    }

    td.classList.add("inventory-stock-cell");

    const cantidad = document.createElement("span");
    cantidad.className = "inventory-quantity";

    const input = document.createElement("input");
    input.type = "number";
    input.inputMode = "decimal";
    input.min = "0";
    input.step = "any";
    input.setAttribute("aria-label", fields[columnIndex]);
    input.value = columnIndex === 5 ? (value ?? "") : (value || "0");
    input.disabled = !canManageModule("inventario");

    const ajustar = (delta) => {
        if (!canManageModule("inventario")) return;

        const actual = input.value === "" ? 0 : input.valueAsNumber;
        if (!Number.isFinite(actual)) return;

        input.value = String(Math.max(0, actual + delta));
    };

    const menos = actionButton("−", () => ajustar(-1));
    const mas = actionButton("+", () => ajustar(1));

    menos.setAttribute("aria-label", `Disminuir ${fields[columnIndex]}`);
    mas.setAttribute("aria-label", `Aumentar ${fields[columnIndex]}`);
    menos.disabled = mas.disabled = !canManageModule("inventario");

    input.addEventListener("input", () => {
        if (Number.isFinite(input.valueAsNumber) && input.valueAsNumber < 0) {
            input.value = "0";
        }
    });

    cantidad.append(menos, input, mas);
    td.appendChild(cantidad);
    row.appendChild(td);

    stockInputs.push({ columnIndex, input });
});
if (slug === "inventario") {
    const stateCell = document.createElement("td");
    stateCell.appendChild(
        createInventoryStateBadge(record.values)
    );
    row.appendChild(stateCell);
}
            const actions = document.createElement("td");
           if (
    slug === "tareas-diarias"
    && Number(record.references?.[1]) === Number(currentProfile?.employeeId)
    && normalizeArea(record.values[4]) === normalizeArea(currentProfile?.area)
) {
    const state = String(record.values[3] || "")
        .trim().toUpperCase();

    const method = String(record.values[5] || "FOTO")
        .trim().toUpperCase();

    const done = [
        "COMPLETADA",
        "COMPLETADO",
        "FINALIZADA",
        "FINALIZADO"
    ].includes(state);

    const inReview = state === "EN_REVISION";
    const validMethod = ["SIMPLE", "FOTO"].includes(method);

    const label = inReview
        ? "Pendiente de revisión"
        : done
            ? "Tarea completada"
            : method === "SIMPLE"
                ? "Marcar como hecha"
                : "Enviar foto";

    let completedHere = false;

    const send = actionButton(label, async () => {
        if (done || inReview || !validMethod || completedHere) return;

        send.disabled = true;

        try {
            if (method === "SIMPLE") {
                if (!confirm("¿Marcar esta tarea como hecha?")) return;

                await apiRequest(
                    `/api/evidencias/tareas/${record.id}/completar`,
                    { method: "POST" }
                );

                completedHere = true;
                send.textContent = "Tarea completada";

                await loadRecords();
                await loadDashboardMetrics();

                alert("Tarea completada correctamente");
            } else {
                await ensureExperienceLoaded();
                await openTaskCamera(
                    record.id,
                    record.values[0],
                    loadRecords
                );
            }
        } catch (error) {
            alert(error.message);
        } finally {
            send.disabled =
                done || inReview || !validMethod || completedHere;
        }
    });

    send.disabled = done || inReview || !validMethod;
    actions.append(send);
}
            if (canManageModule(slug)) {
                if (slug === "inventario") {
    const acceptChanges = document.createElement("button");
    acceptChanges.type = "button";
    acceptChanges.className = "table-button";
    acceptChanges.textContent = "Aceptar cambios";

    acceptChanges.addEventListener("click", async () => {
        acceptChanges.disabled = true;

        try {
            const values = [...record.values];

            stockInputs.forEach(({ columnIndex, input }) => {
                values[columnIndex] = input.value.trim();
            });

            await apiRequest(`${modulesApi}/${slug}/${record.id}`, {
                method: "PUT",
                body: JSON.stringify({
                    values,
                    references: record.references || []
                })
            });

            await loadRecords();
            await loadDashboardMetrics();
        } catch (error) {
            alert(`No se pudieron guardar los cambios: ${error.message}`);
            acceptChanges.disabled = false;
        }
    });

    actions.append(acceptChanges);
}
            const edit = document.createElement("button"); edit.type = "button"; edit.className = "table-button"; edit.textContent = "Editar";
            const remove = document.createElement("button"); remove.type = "button"; remove.className = "table-button"; remove.textContent = "Eliminar";
            edit.addEventListener("click", () => {
    inputs.forEach((input, i) => {
        input.value = input.dataset.employeeReference
            ? (record.references[i] ?? "")
            : (record.values[i] || "");
    });

    form.dataset.editing = String(index);
    form.hidden = false;
    submit.textContent = "Guardar cambios";
    refreshInventoryStatePreview();
    inputs[0].focus();
});
            remove.addEventListener("click", async () => {
                if (!confirm("¿Eliminar este registro de Oracle?")) return;
                try { await apiRequest(`${modulesApi}/${slug}/${record.id}`, { method: "DELETE" }); await loadRecords(); await loadDashboardMetrics(); }
                catch (error) { alert(`No se pudo eliminar: ${error.message}`); }
            });
            actions.append(edit, remove);
            }
            row.appendChild(actions); tbody.appendChild(row);
        });
        empty.hidden = filtered.length > 0;
        if (loadError) empty.textContent = `No se pudo cargar ${name}: ${loadError}`;
        else if (records.length && !filtered.length) empty.textContent = "No se encontraron coincidencias.";
        else empty.textContent = "Todavía no hay registros. Usa el botón para agregar el primero.";
    };
    const loadRecords = async () => {
        try { records = await apiRequest(`${modulesApi}/${slug}${selectedArea ? `?area=${encodeURIComponent(selectedArea)}` : ""}`); loadError = ""; }
        catch (error) { records = []; loadError = error.message; }
        draw();
    };
    add.addEventListener("click", () => {
    form.reset();
    restoreFixedFields();

    if (slug === "inventario") {
        const selected = inventoryGroupFilter.value;
        inputs[6].value = selected === "SIN_GRUPO" ? "" : selected;
    }

    if (isWorkerRole()) {
        employeeSelects.forEach((select) => {
            select.value = String(currentProfile.employeeId);
        });
    }

    delete form.dataset.editing;
    submit.textContent = "Guardar";
    form.hidden = false;
    refreshInventoryStatePreview();
    inputs[0].focus();
});
    cancel.addEventListener("click", () => { form.reset(); delete form.dataset.editing; form.hidden = true; });
    form.addEventListener("submit", async (event) => {
        event.preventDefault(); if (!canManageModule(slug)) return; const values = inputs.map((input) => input.value.trim());
        if (values.some((value, i) => inputs[i].required && !value)) return;
        const references = inputs.map((input) => input.dataset.employeeReference ? Number(input.value) : null);
        const payload = { values, references };
        try {
            const editing = form.dataset.editing !== undefined;
            const record = editing ? records[Number(form.dataset.editing)] : null;
            const url = editing ? `${modulesApi}/${slug}/${record.id}` : `${modulesApi}/${slug}`;
            await apiRequest(url, { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) });
            form.reset(); restoreFixedFields(); if (isWorkerRole()) employeeSelects.forEach((select) => { select.value = String(currentProfile.employeeId); }); delete form.dataset.editing; form.hidden = true; submit.textContent = "Guardar"; await loadRecords(); await loadDashboardMetrics();
        } catch (error) { alert(`No se pudo guardar: ${error.message}`); }
    });
    search.addEventListener("input", draw); await loadRecords();
    if (revision !== screenRevision) return;
    if (name === "Tareas diarias") await renderEvidenceReview(section, selectedArea);
    if (name === "Horarios") await renderSchedules(section);
    if (name === "Configuración") {
        await renderNotificationPreferences(section);
        await renderAccountSettings(section);
    }
}

async function renderEvidenceReview(section, selectedArea) {
    if (selectedArea === "SIN_AREA") return;
    try {
        const evidence = await apiRequest(`/api/evidencias?area=${encodeURIComponent(selectedArea)}`);
        const box = document.createElement("div"); box.className = "evidence-review";
        const heading = document.createElement("h3"); heading.textContent = "Fotos enviadas por el equipo";
        const table = document.createElement("table"); const head = document.createElement("tr");
        ["Tarea", "Trabajador", "Enviada", "Foto", "Acción"].forEach((label) => { const th = document.createElement("th"); th.textContent = label; head.appendChild(th); });
        const thead = document.createElement("thead"); thead.appendChild(head); const body = document.createElement("tbody"); table.append(thead, body);
        evidence.forEach((item) => {
            const row = document.createElement("tr");
            [item.task, item.employee, item.createdAt].forEach((value) => { const td = document.createElement("td"); td.textContent = value; row.appendChild(td); });
            const imageCell = document.createElement("td"); const link = document.createElement("a"); link.href = `/api/evidencias/${item.id}/foto`; link.target = "_blank"; link.rel = "noopener"; link.textContent = "Ver foto"; imageCell.appendChild(link); row.appendChild(imageCell);
            const actionCell = document.createElement("td");
            const approve = actionButton(item.result === "RECHAZADA" ? "Rechazada" : item.status === "REVISADA" ? "Revisada" : "Aprobar tarea", async () => { try { await apiRequest(`/api/evidencias/${item.id}/revisar`, {method:"POST"}); await renderModule("Tareas diarias", selectedArea); await loadDashboardMetrics(); } catch (error) { alert(error.message); } });
            approve.disabled = !isOwnerRole() || item.status === "REVISADA"; actionCell.append(approve);
            if (isOwnerRole() && item.status !== 'REVISADA') actionCell.append(actionButton('Rechazar evidencia',async()=>{
                if(!confirm('¿Rechazar esta evidencia y pedir una nueva foto?'))return;
                try{await apiRequest('/api/evidencias/'+item.id+'/revisar?approved=false',{method:'POST'});await renderModule('Tareas diarias',selectedArea);}
                catch(error){alert(error.message);}
            }));
            if (isOwnerRole() || isSalonRole()) {
                const remove = document.createElement("button"); remove.type = "button"; remove.className = "table-button"; remove.textContent = "Borrar";
                remove.addEventListener("click", async () => { if (!confirm("¿Ya revisaste esta evidencia y quieres borrar la foto? Al borrar la última foto, la tarea quedará marcada como completada.")) return; try { await apiRequest(`/api/evidencias/${item.id}`, { method: "DELETE" }); await renderModule("Tareas diarias", selectedArea); } catch (error) { alert(`No se pudo borrar: ${error.message}`); } }); actionCell.appendChild(remove);
            }
            row.appendChild(actionCell); body.appendChild(row);
        });
        const empty = document.createElement("p"); empty.className = "empty-state"; empty.textContent = evidence.length ? "" : "Todavía no han enviado fotos de tareas.";
        box.append(heading, table, empty); section.appendChild(box);
    } catch (error) { console.error("No se pudieron cargar las fotos de tareas:", error); }
}

menuItems.forEach((link) => link.addEventListener("click", (event) => { event.preventDefault(); showScreen(link); }));

// Las tarjetas del Dashboard abren el mismo módulo que la barra lateral.
document.querySelectorAll(".dashboard-cards .card").forEach((card) => {
    const title = card.querySelector("h3")?.textContent.trim();
    const destination = menuItems.find((link) => navLabel(link) === title);
    if (!destination) return;
    card.setAttribute("role", "link");
    card.setAttribute("tabindex", "0");
    card.setAttribute("aria-label", `Abrir módulo ${title}`);
    card.addEventListener("click", () => showScreen(destination));
    card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            showScreen(destination);
        }
    });
});

// Menús superiores y acciones de cuenta
const adminUser = document.getElementById("adminUser");
const adminMenu = document.getElementById("adminMenu");
if (adminUser && adminMenu) {
    adminUser.addEventListener("click", (event) => { event.stopPropagation(); adminMenu.classList.toggle("show"); });
    adminMenu.addEventListener("click", (event) => event.stopPropagation());
    adminMenu.querySelectorAll("button").forEach((button) => button.addEventListener("click", () => {
        const action = button.textContent.trim(); adminMenu.classList.remove("show");
        if (action.includes("perfil")) void openProfile();
        else if (action.includes("Configuración")) {
            const settings = menuItems.find((link) => navLabel(link) === "Configuración");
            if (settings) showScreen(settings);
        } else if (action.includes("Cerrar sesión")) void closeSession();
    }));
}
if (localStorage.getItem("restaurant-erp-compact") === "true") document.body.classList.add("compact-view");
if (localStorage.getItem("marigex-theme") === "dark") document.body.classList.add("theme-dark");
if (localStorage.getItem("marigex-data-saver") === "true") document.body.classList.add("data-saver");

const notificationBtn = document.getElementById("notificationBtn");
const notificationMenu = document.getElementById("notificationMenu");
if (notificationBtn && notificationMenu) {
    notificationBtn.setAttribute("aria-haspopup", "true"); notificationBtn.setAttribute("aria-controls", "notificationMenu");
    notificationBtn.setAttribute("aria-expanded", "false");
    notificationBtn.setAttribute("aria-label", "No hay notificaciones pendientes");
    notificationBtn.addEventListener("click", (event) => {
        event.stopPropagation();
        const opened = notificationMenu.classList.toggle("show");
        if (opened) void loadUserNotifications();
        notificationBtn.setAttribute("aria-expanded", String(opened));
    });
    notificationMenu.addEventListener("click", (event) => event.stopPropagation());
    notificationMenu.querySelectorAll(".notification-item").forEach((item) => item.addEventListener("click", () => {
        notificationMenu.classList.remove("show");
        const title = item.querySelector("strong")?.textContent ?? "";
        const destination = title.includes("Tarea") ? "Tareas diarias" : title.includes("Incidencia") ? "Incidencias" : title.includes("Compra") ? "Compras" : "Inventario";
        const target = menuItems.find((link) => link.textContent.includes(destination));
        if (target) showScreen(target);
    }));
    document.addEventListener("click", () => { notificationMenu.classList.remove("show"); notificationBtn.setAttribute("aria-expanded", "false"); });
}
document.getElementById("markNotificationsRead")?.addEventListener("click", async () => {
    try { await apiRequest("/api/notificaciones/leidas", {method:"POST"}); await loadUserNotifications(); }
    catch(error) { alert(error.message); }
});

// Búsqueda general: filtra tarjetas del Dashboard y opciones del menú.
const globalSearch = document.querySelector(".search input");
globalSearch?.addEventListener("input", () => {
    const query = globalSearch.value.trim().toLocaleLowerCase("es");
    document.querySelectorAll(".dashboard-cards .card").forEach((card) => { card.hidden = !card.textContent.toLocaleLowerCase("es").includes(query); });
    menuItems.forEach((link) => { link.hidden = !canSeeModule(slugifyLabel(navLabel(link))) || (Boolean(query) && !link.textContent.toLocaleLowerCase("es").includes(query)); });
});

const employeeSearch = document.getElementById("employeeSearch");
const employeeAreaFilter = document.getElementById("employeeAreaFilter");
function filterEmployees() {
    const query = employeeSearch.value.trim().toLocaleLowerCase("es");
    const area = employeeAreaFilter.value;
    [...employeeBody.rows].forEach((row) => {
        const rowArea = normalizeArea(row.cells[2]?.textContent);
        const matchesArea = !area || (area === "SIN_AREA" ? !rowArea || rowArea === "PENDIENTES DE CLASIFICAR" : rowArea === area);
        row.hidden = !matchesArea || !row.textContent.toLocaleLowerCase("es").includes(query);
    });
}
employeeSearch?.addEventListener("input", filterEmployees);
employeeAreaFilter?.addEventListener("change", filterEmployees);
const importDialog = document.getElementById("employeeImportDialog");
const pasteArea = document.getElementById("employeeImportPasteArea");
const pasteText = document.getElementById("employeeImportText");
const inventoryImportDialog = document.getElementById("inventoryImportDialog");
const inventoryPasteArea = document.getElementById("inventoryImportPasteArea");
const inventoryPasteText = document.getElementById("inventoryImportText");

document.getElementById("inventoryImportPaste")?.addEventListener("click", () => {
    inventoryPasteArea.hidden = false;
    inventoryPasteText.focus();
});

document.getElementById("inventoryImportClose")?.addEventListener("click", () => {
    inventoryImportDialog?.close();
    inventoryPasteArea.hidden = true;
    inventoryPasteText.value = "";
});
function convertirTextoAInsumos(texto) {
    const lineas = texto.trim().split(/\r?\n/).filter(linea => linea.trim());

    if (lineas.length < 2) {
        throw new Error("Incluye los encabezados y al menos un insumo.");
    }

    const separador = lineas[0].includes("\t")
        ? "\t"
        : lineas[0].includes(";")
            ? ";"
            : ",";

    function separarLinea(linea) {
        const celdas = [];
        let valor = "";
        let entreComillas = false;

        for (let i = 0; i < linea.length; i++) {
            const caracter = linea[i];

            if (caracter === '"' && linea[i + 1] === '"' && entreComillas) {
                valor += '"';
                i++;
            } else if (caracter === '"') {
                entreComillas = !entreComillas;
            } else if (caracter === separador && !entreComillas) {
                celdas.push(valor.trim());
                valor = "";
            } else {
                valor += caracter;
            }
        }

        celdas.push(valor.trim());
        return celdas;
    }

    const encabezados = separarLinea(lineas[0]).map(encabezado =>
        encabezado.normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toUpperCase()
            .replace(/[^A-Z0-9]+/g, "_")
    );

    const columnas = {
        insumo: encabezados.indexOf("INSUMO"),
        categoria: encabezados.indexOf("CATEGORIA"),
        stockActual: encabezados.indexOf("STOCK_ACTUAL"),
        unidad: encabezados.indexOf("UNIDAD"),
        stockMinimo: encabezados.indexOf("STOCK_MINIMO"),
        stockMaximo: encabezados.indexOf("STOCK_MAXIMO"),
        grupo: encabezados.indexOf("GRUPO")
    };

    const faltantes = Object.entries(columnas)
        .filter(([, indice]) => indice < 0)
        .map(([nombre]) => nombre);

    if (faltantes.length) {
        throw new Error(`Faltan columnas: ${faltantes.join(", ")}.`);
    }

    return lineas.slice(1).map(linea => {
        const celdas = separarLinea(linea);
        return Object.fromEntries(
            Object.entries(columnas).map(([nombre, indice]) => [
                nombre,
                celdas[indice] || ""
            ])
        );
    }).filter(fila => Object.values(fila).some(valor => valor.trim()));
}

async function confirmarInsumos(filas) {
    if (!filas.length) {
        alert("No encontré insumos para importar.");
        return;
    }

    if (!window.confirm(`Se importarán ${filas.length} insumos. ¿Deseas continuar?`)) {
        return;
    }

    try {
        await apiRequest("/api/inventario/importacion/confirm", {
            method: "POST",
            body: JSON.stringify({ filas })
        });

        inventoryImportDialog?.close();
        inventoryPasteArea.hidden = true;
        inventoryPasteText.value = "";
        await renderModule("Inventario");
        alert("Insumos importados correctamente.");
    } catch (error) {
        alert(`No se pudo importar: ${error.message}`);
    }
}

document.getElementById("inventoryImportTextSubmit")?.addEventListener("click", async () => {
    try {
        const nombres = inventoryPasteText.value
    .split(/\r?\n/)
    .map(nombre => nombre.trim())
    .filter(Boolean);

if (!nombres.length) {
    throw new Error("Pega al menos un nombre de insumo, uno por línea.");
}

const grupo = document.querySelector('[aria-label="Grupo de inventario"]')?.value;

if (grupo !== "COCINA" && grupo !== "BARRA") {
    throw new Error("Selecciona Cocina o Barra en el inventario.");
}

const filas = nombres.map(insumo => ({
    insumo,
    categoria: "Por clasificar",
    stockActual: "0",
    unidad: "Unidad",
    stockMinimo: "0",
    stockMaximo: "",
    grupo
}));

await confirmarInsumos(filas);
    } catch (error) {
        alert(error.message);
    }
});

document.getElementById("inventoryImportCsv")?.addEventListener("click", () => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".csv";

    input.addEventListener("change", async () => {
        const archivo = input.files?.[0];
        if (!archivo) return;

        try {
            const filas = convertirTextoAInsumos(await archivo.text());
            await confirmarInsumos(filas);
        } catch (error) {
            alert(`No se pudo leer el CSV: ${error.message}`);
        }
    });

    input.click();
});

document.getElementById("inventoryImportExcel")?.addEventListener("click", () => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".xlsx,.xls,.xlsm";

    input.addEventListener("change", async () => {
        const archivo = input.files?.[0];
        if (!archivo) return;

        const datos = new FormData();
        datos.append("archivo", archivo);
        const grupo = document.querySelector('[aria-label="Grupo de inventario"]')?.value;
        if (grupo) datos.append("grupo", grupo);

        try {
            const vistaPrevia = await uploadRequest(
                "/api/inventario/importacion/preview",
                datos
            );

            const filas = vistaPrevia.filas || [];
            const conErrores = filas.filter(fila => !fila.valida);

            if (conErrores.length) {
                const detalle = conErrores
                    .map(fila => `Fila ${fila.fila}: ${fila.errores.join(", ")}`)
                    .join("\n");
                alert(`Corrige estas filas del Excel:\n\n${detalle}`);
                return;
            }

            await confirmarInsumos(filas.map(fila => ({
                insumo: fila.insumo,
                categoria: fila.categoria,
                stockActual: fila.stockActual,
                unidad: fila.unidad,
                stockMinimo: fila.stockMinimo,
                stockMaximo: fila.stockMaximo,
                grupo: fila.grupo
            })));
        } catch (error) {
            alert(`No se pudo importar el Excel: ${error.message}`);
        }
    });

    input.click();
});

document.getElementById("importEmployeesButton")?.addEventListener("click", () => {
    if (!isAdminRole()) return;
    importDialog?.showModal();
});

document.getElementById("employeeImportClose")?.addEventListener("click", () => {
    importDialog?.close();
    pasteArea.hidden = true;
    pasteText.value = "";
});

async function confirmarTrabajadores(filas) {
    if (!filas.length) {
        alert("No encontré trabajadores para importar.");
        return;
    }

    const confirmar = window.confirm(
        `Se importarán ${filas.length} trabajadores. ¿Deseas continuar?`
    );
    if (!confirmar) return;

    try {
        await apiRequest("/api/empleados/importacion/confirm", {
            method: "POST",
            body: JSON.stringify({ filas })
        });

        importDialog?.close();
        pasteArea.hidden = true;
        pasteText.value = "";
        await loadEmployees();
        await loadDashboardMetrics();
        alert("Trabajadores importados correctamente.");
    } catch (error) {
        alert(`No se pudo importar: ${error.message}`);
    }
}

function convertirTextoAFilas(texto) {
    const lineas = texto.trim().split(/\r?\n/).filter(linea => linea.trim());
    if (lineas.length < 2) {
        throw new Error("Pega los encabezados y al menos un trabajador.");
    }

    const separador = lineas[0].includes("\t")
        ? "\t"
        : lineas[0].includes(";")
            ? ";"
            : ",";

    function separarLinea(linea) {
        const celdas = [];
        let valor = "";
        let dentroDeComillas = false;

        for (let i = 0; i < linea.length; i++) {
            const caracter = linea[i];

            if (caracter === '"' && linea[i + 1] === '"' && dentroDeComillas) {
                valor += '"';
                i++;
            } else if (caracter === '"') {
                dentroDeComillas = !dentroDeComillas;
            } else if (caracter === separador && !dentroDeComillas) {
                celdas.push(valor.trim());
                valor = "";
            } else {
                valor += caracter;
            }
        }

        celdas.push(valor.trim());
        return celdas;
    }

    const encabezados = separarLinea(lineas[0]).map(encabezado =>
        encabezado.normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase()
    );

    const indice = nombre => encabezados.findIndex(encabezado =>
        encabezado === nombre || encabezado === `${nombre} trabajador`
    );

    const columnas = {
        nombre: indice("nombre"),
        cargo: indice("cargo"),
        area: indice("area"),
        rut: indice("rut")
    };

    const faltantes = Object.entries(columnas)
        .filter(([nombre, posicion]) => posicion < 0 && ["nombre", "rut"].includes(nombre))
        .map(([nombre]) => nombre.toUpperCase());

    if (faltantes.length) {
        throw new Error(`Faltan columnas en los encabezados: ${faltantes.join(", ")}.`);
    }

    return lineas.slice(1).map(linea => {
        const celdas = separarLinea(linea);
        return {
            nombre: celdas[columnas.nombre] || "",
            cargo: columnas.cargo < 0 ? "TRABAJADOR" : celdas[columnas.cargo] || "TRABAJADOR",
            area: columnas.area < 0 ? "SIN ASIGNAR" : celdas[columnas.area] || "SIN ASIGNAR",
            rut: celdas[columnas.rut] || ""
        };
    }).filter(fila => fila.nombre || fila.cargo || fila.area || fila.rut);
}

document.getElementById("employeeImportPaste")?.addEventListener("click", () => {
    pasteArea.hidden = false;
    pasteText.focus();
});

document.getElementById("employeeImportTextSubmit")?.addEventListener("click", async () => {
    try {
        const filas = convertirTextoAFilas(pasteText.value);
        await confirmarTrabajadores(filas);
    } catch (error) {
        alert(error.message);
    }
});

async function seleccionarArchivo(tipo) {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = tipo === "excel" ? ".xlsx,.xls" : ".csv";

    input.addEventListener("change", async () => {
        const archivo = input.files?.[0];
        if (!archivo) return;

        if (tipo === "csv") {
            try {
                const filas = convertirTextoAFilas(await archivo.text());
                await confirmarTrabajadores(filas);
            } catch (error) {
                alert(`No se pudo leer el CSV: ${error.message}`);
            }
            return;
        }

        const datos = new FormData();
        datos.append("archivo", archivo);

        try {
            const vistaPrevia = await uploadRequest(
                "/api/empleados/importacion/preview",
                datos
            );

            const filas = vistaPrevia.filas || [];
            const conErrores = filas.filter(fila => !fila.valida);

            if (conErrores.length) {
                const detalle = conErrores
                    .map(fila => `Fila ${fila.fila}: ${fila.errores.join(", ")}`)
                    .join("\n");
                alert(`Hay filas que debes corregir en el Excel:\n\n${detalle}`);
                return;
            }

            await confirmarTrabajadores(
                filas.map(({ nombre, cargo, area, rut }) => ({
                    nombre, cargo, area, rut
                }))
            );
        } catch (error) {
            alert(`No se pudo importar el Excel: ${error.message}`);
        }
    });

    input.click();
}

document.getElementById("employeeImportExcel")?.addEventListener("click", () => {
    seleccionarArchivo("excel");
});

document.getElementById("employeeImportCsv")?.addEventListener("click", () => {
    seleccionarArchivo("csv");
});
document.getElementById("addEmployeeButton")?.addEventListener("click", async () => {
    if (!isOwnerRole()) return;
    const name = prompt("Nombre del trabajador:"); if (!name?.trim()) return;
    const role = prompt("Cargo:"); if (!role?.trim()) return;
    const area = prompt("Área:"); if (!area?.trim()) return;
    try {
        await apiRequest(employeesApi, { method: "POST", body: JSON.stringify({ name, role, area, active: true }) });
        await loadEmployees();
        await loadDashboardMetrics();
        alert("Trabajador guardado en Oracle.");
    } catch (error) { alert(`No se pudo guardar: ${error.message}`); }
});
employeeBody?.addEventListener("click", async (event) => {
    const button = event.target.closest("button"); if (!button) return;
    const row = button.closest("tr"); const action = button.textContent.trim();
    const id = row.dataset.id;
    const employee = {
        name: row.cells[0].textContent,
        role: row.cells[1].textContent,
        area: row.cells[2].textContent,
        active: row.cells[3].querySelector(".status")?.classList.contains("active") ?? true
    };
    if (action === "Ver") alert(`Trabajador: ${employee.name}\nCargo: ${employee.role}\nÁrea: ${employee.area}\nEstado: ${employee.active ? "Activo" : "Inactivo"}`);
    if (action === "Crear acceso") {
        if (!isOwnerRole()) return;
        openEmployeeAccount(Number(id), employee.name);
    }
    if (action === "Editar") {
        const name = prompt("Nombre:", employee.name); if (name === null || !name.trim()) return;
        const role = prompt("Cargo:", employee.role); if (role === null || !role.trim()) return;
        const area = prompt("Área:", employee.area); if (area === null || !area.trim()) return;
        try {
            await apiRequest(`${employeesApi}/${id}`, { method: "PUT", body: JSON.stringify({ ...employee, name, role, area }) });
            await loadEmployees();
            await loadDashboardMetrics();
        } catch (error) { alert(`No se pudo actualizar: ${error.message}`); }
    }
    if (action === "Cambiar estado") {
        try {
            await apiRequest(`${employeesApi}/${id}`, { method: "PUT", body: JSON.stringify({ ...employee, active: !employee.active }) });
            await loadEmployees();
            await loadDashboardMetrics();
        } catch (error) { alert(`No se pudo cambiar el estado: ${error.message}`); }
    }
    if (action === "Eliminar" && confirm(`¿Eliminar a ${employee.name}?`)) {
        try {
            await apiRequest(`${employeesApi}/${id}`, { method: "DELETE" });
            await loadEmployees();
            await loadDashboardMetrics();
        } catch (error) { alert(`No se pudo eliminar: ${error.message}`); }
    }
});
let currentProfile = null;
const authScreen = document.getElementById("auth-screen");
const loginForm = document.getElementById("login-form");
const setupForm = document.getElementById("setup-form");
const authError = document.getElementById("auth-error");
const profileDialog = document.getElementById("profileDialog");

function setAuthError(message) {
    authError.textContent = message || "";
    authError.hidden = !message;
}
function fillProfile(profile) {
    currentProfile = profile;
    document.getElementById("currentUserName").textContent = profile.name;
    const roleLabels = { ADMIN: "Administrador", EMPRESA: "Jefe de empresa", JEFE_SALON: "Jefe de salón", JEFE_COCINA: "Jefe de cocina", JEFE_BARRA: "Jefe de barra", JEFE_COPERIA: "Jefe de copería", JEFE_LOCAL: "Jefe de local", TRABAJADOR: "Trabajador", EMPLEADO: "Trabajador" };
    document.getElementById("currentUserRole").textContent = roleLabels[profile.role] || profile.position;
}
async function enterApplication(profile) {
    fillProfile(profile);
    document.body.classList.remove("logged-out");
    void ensureExperienceLoaded().then(() => {
        if (currentProfile === profile) return bindPushAccount();
    }).catch(error => console.warn('El acceso continúa sin Push:', error.message));
    document.getElementById("addEmployeeButton").disabled = !isOwnerRole();
    document.getElementById("importEmployeesButton").hidden = !isAdminRole();
    adminMenu?.querySelectorAll("button").forEach((button) => { if (button.textContent.includes("Configuración")) button.disabled = false; });
    menuItems.forEach(link => { link.hidden = false; });
    document.querySelectorAll(".dashboard-cards .card").forEach((card) => { const label = card.querySelector("h3")?.textContent.trim() || ""; card.hidden = !canSeeModule(slugifyLabel(label)); });
    authScreen.hidden = true;
    setAuthError("");
    const preferredLabel = "Dashboard";
    const firstVisible = menuItems.find((link) => navLabel(link) === preferredLabel && !link.hidden) || menuItems.find((link) => !link.hidden);
    if (firstVisible) showScreen(firstVisible);
    await loadEmployees();
    await loadDashboardMetrics();
}
function showLogin(error = "") {
    currentProfile = null;
    document.body.classList.add("logged-out");
    resetNotifications();
    authScreen.hidden = false;
    loginForm.hidden = false;
    setupForm.hidden = true;
    document.getElementById("auth-title").textContent = "Bienvenido a MariGex 66";
    document.getElementById("auth-description").textContent = "Inicia sesión para continuar.";
    setAuthError(error);
}
async function initializeAuthentication() {
    try {
        const status = await apiRequest(`${authApi}/status`);
        if (status.setupRequired) {
            const workers = await apiRequest(`${authApi}/setup/employees`);
            const selector = document.getElementById("setup-employee");
            selector.replaceChildren(new Option("Selecciona tu nombre", ""));
            workers.forEach((worker) => selector.add(new Option(`${worker.name} — ${worker.role}`, worker.id)));
            loginForm.hidden = true;
            setupForm.hidden = false;
            document.getElementById("auth-title").textContent = "Crear acceso administrador";
            document.getElementById("auth-description").textContent = workers.length
                ? "La primera cuenta será administradora y quedará vinculada a tu ficha de Personal."
                : "Primero agrega un trabajador en la base de datos para crear la cuenta administradora.";
            setupForm.querySelector("button").disabled = workers.length === 0;
            return;
        }
        const profile = await apiRequest(`${authApi}/session`);
        await enterApplication(profile);
    } catch (error) {
        showLogin(error.status === 401 ? "" : `No se pudo conectar con la aplicación. Revisa los registros del servidor. (${error.message})`);
    }
}
loginForm.addEventListener("submit", async (event) => {
    event.preventDefault(); setAuthError("");
    try {
        const profile = await apiRequest(`${authApi}/login`, { method: "POST", body: JSON.stringify({
            username: document.getElementById("login-username").value,
            password: document.getElementById("login-password").value
        }) });
        loginForm.reset(); await enterApplication(profile);
    } catch (error) { setAuthError(error.message); }
});
setupForm.addEventListener("submit", async (event) => {
    event.preventDefault(); setAuthError("");
    try {
        const profile = await apiRequest(`${authApi}/setup`, { method: "POST", body: JSON.stringify({
            employeeId: Number(document.getElementById("setup-employee").value),
            username: document.getElementById("setup-username").value,
            password: document.getElementById("setup-password").value
        }) });
        setupForm.reset(); await enterApplication(profile);
    } catch (error) { setAuthError(error.message); }
});
async function openProfile() {
    if (!currentProfile) return;
    const profileEmployeeId = currentProfile.employeeId;
    document.getElementById("profile-name").textContent = currentProfile.name;
    document.getElementById("profile-position").textContent = currentProfile.position;
    document.getElementById("profile-area").textContent = currentProfile.area;
    document.getElementById("profile-area-detail").textContent = currentProfile.area || "Sin área asignada";
    document.getElementById("profile-username").textContent = currentProfile.username;
    const roleLabels = { ADMIN: "Administrador", EMPRESA: "Jefe de empresa", JEFE_SALON: "Jefe de salón", JEFE_COCINA: "Jefe de cocina", JEFE_BARRA: "Jefe de barra", JEFE_COPERIA: "Jefe de copería", JEFE_LOCAL: "Jefe de local", TRABAJADOR: "Trabajador", EMPLEADO: "Trabajador" };
    document.getElementById("profile-role").textContent = roleLabels[currentProfile.role] || currentProfile.position;
    const avatar = document.getElementById("profileAvatar");
    const avatarKey = `marigex-profile-photo-${currentProfile.employeeId}`;
    const initials = currentProfile.name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join("").toUpperCase();
    const savedPhoto = localStorage.getItem(avatarKey);
    avatar.textContent = savedPhoto ? "" : initials;
    avatar.style.backgroundImage = savedPhoto ? `url("${savedPhoto}")` : "";
    avatar.classList.toggle("has-photo", Boolean(savedPhoto));
    const tenure = document.getElementById("profile-tenure");
    const startDate = document.getElementById("profile-start-date");
    const balance = document.getElementById("profile-vacation-balance");
    const vacationNote = document.getElementById("profile-vacation-note");
    tenure.textContent = "Cargando…"; balance.textContent = "Cargando…";
    document.getElementById("profileVacationList").replaceChildren();
    profileDialog.showModal();
    await loadProfileVacationAdmin();
    await loadProfileVacation();
}
async function loadProfileVacation() {
    const profileEmployeeId = currentProfile?.employeeId;
    try {
        const vacation = await apiRequest("/api/perfil/vacaciones");
        if (!profileDialog.open || !currentProfile || currentProfile.employeeId !== profileEmployeeId) return;
        document.getElementById("profile-tenure").textContent = formatServiceTime(vacation.serviceDays);
        document.getElementById("profile-start-date").textContent = `Cuenta creada el ${new Date(`${vacation.startDate}T12:00:00`).toLocaleDateString("es-CL")}`;
        document.getElementById("profile-vacation-balance").textContent = `${formatVacationDays(vacation.balanceDays)} días`;
        document.getElementById("profile-vacation-note").textContent = `${formatVacationDays(vacation.accruedDays)} días generados · ${formatVacationDays(vacation.usedDays)} utilizados. Base: 15 días hábiles por cada año completado; no incluye feriado progresivo.`;
        const list = document.getElementById("profileVacationList");
        list.replaceChildren();
        if (!vacation.uses.length) list.append(document.createTextNode("Todavía no tienes días de vacaciones registrados."));
        vacation.uses.forEach(use => {
            const row = document.createElement("div"); row.className = "vacation-history-row";
            const label = document.createElement("span"); label.textContent = `${new Date(`${use.date}T12:00:00`).toLocaleDateString("es-CL")} · ${formatVacationDays(use.days)} días${use.note ? ` · ${use.note}` : ""}`;
            row.append(label);
            if (isOwnerRole()) {
                const remove = document.createElement("button"); remove.type = "button"; remove.className = "table-button"; remove.textContent = "Eliminar";
                remove.addEventListener("click", async () => {
                    if (!confirm("¿Eliminar este registro? El saldo se recalculará.")) return;
                    try { await apiRequest(`/api/perfil/vacaciones/usos/${use.id}`, { method: "DELETE" }); await loadProfileVacation(); }
                    catch (error) { label.textContent = error.message; }
                });
                row.append(remove);
            }
            list.append(row);
        });
    } catch (error) {
        document.getElementById("profile-tenure").textContent = "No se pudo cargar";
        document.getElementById("profile-vacation-balance").textContent = "No disponible";
        document.getElementById("profile-start-date").textContent = error.message || "Revisa la conexión e inténtalo otra vez.";
        document.getElementById("profileVacationList").textContent = error.message || "No se pudo cargar el historial.";
    }
}
function formatVacationDays(value) { return new Intl.NumberFormat("es-CL", { maximumFractionDigits: 2 }).format(Number(value || 0)); }
function formatServiceTime(days) {
    const value = Math.max(0, Number(days) || 0); const years = Math.floor(value / 365.2425);
    const months = Math.floor((value - years * 365.2425) / 30.4369); const remaining = Math.floor(value - years * 365.2425 - months * 30.4369);
    return [years && `${years} ${years === 1 ? "año" : "años"}`, months && `${months} ${months === 1 ? "mes" : "meses"}`, !years && !months ? `${remaining} días` : null].filter(Boolean).join(" y ");
}
document.getElementById("closeProfile").addEventListener("click", () => profileDialog.close());
const profileVacationAdmin = document.getElementById("profileVacationAdmin");
const profileVacationForm = document.getElementById("profileVacationForm");
profileVacationForm.addEventListener("submit", async event => {
    event.preventDefault();
    const save = profileVacationForm.querySelector("button[type=submit]");
    const status = document.getElementById("profileVacationStatus");
    save.disabled = true; status.textContent = "Guardando…";
    try {
        await apiRequest("/api/perfil/vacaciones/usos", { method: "POST", body: JSON.stringify({
            employeeId: Number(document.getElementById("profileVacationEmployee").value),
            date: document.getElementById("profileVacationDate").value,
            days: Number(document.getElementById("profileVacationDays").value),
            note: document.getElementById("profileVacationNote").value.trim()
        }) });
        profileVacationForm.reset();
        document.getElementById("profileVacationDate").value = new Date().toLocaleDateString("en-CA");
        status.textContent = "Vacaciones registradas.";
        await loadProfileVacation();
    } catch (error) { status.textContent = error.message || "No se pudo registrar el uso."; }
    finally { save.disabled = false; }
});
async function loadProfileVacationAdmin() {
    if (!isOwnerRole()) { profileVacationAdmin.hidden = true; return; }
    profileVacationAdmin.hidden = false;
    const select = document.getElementById("profileVacationEmployee");
    select.replaceChildren(new Option("Selecciona trabajador…", ""));
    const date = document.getElementById("profileVacationDate");
    date.max = new Date().toLocaleDateString("en-CA"); date.value = date.max;
    try {
        const employees = await apiRequest("/api/empleados");
        employees.filter(item => item.active).forEach(item => select.add(new Option(item.name, String(item.id))));
    } catch (error) { document.getElementById("profileVacationStatus").textContent = error.message || "No se pudo cargar Personal."; }
}
document.getElementById("profilePhotoInput").addEventListener("change", async event => {
    const file = event.target.files?.[0];
    if (!file || !currentProfile) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type) || file.size > 5 * 1024 * 1024) {
        alert("Elige una imagen JPG, PNG o WebP de hasta 5 MB."); event.target.value = ""; return;
    }
    try {
        const source = await createImageBitmap(file);
        const canvas = document.createElement("canvas");
        const scale = Math.min(1, 320 / Math.max(source.width, source.height));
        canvas.width = Math.round(source.width * scale); canvas.height = Math.round(source.height * scale);
        canvas.getContext("2d").drawImage(source, 0, 0, canvas.width, canvas.height); source.close();
        const photo = canvas.toDataURL("image/jpeg", .82);
        localStorage.setItem(`marigex-profile-photo-${currentProfile.employeeId}`, photo);
        const avatar = document.getElementById("profileAvatar"); avatar.textContent = "";
        avatar.style.backgroundImage = `url("${photo}")`; avatar.classList.add("has-photo");
    } catch { alert("No se pudo guardar la foto en este dispositivo."); }
    finally { event.target.value = ""; }
});
async function closeSession() {
    if (!confirm("¿Quieres cerrar tu sesión?")) return;
    try { await disconnectPush(); } catch(error) { console.info('No se pudo desvincular Push:',error.message); }
    try { await apiRequest(authApi + '/logout', { method: 'POST' }); }
    catch (error) { console.error("No se pudo cerrar la sesión en el servidor:", error); }
    adminMenu?.classList.remove("show");
    showLogin("Sesión cerrada.");
    document.getElementById("login-password").value = "";
}









// La cuenta usa el endpoint y el hash existentes; el formulario no almacena claves.
const employeeAccountDialog = document.getElementById("employeeAccountDialog");
const employeeAccountForm = document.getElementById("employeeAccountForm");
let accountSubmitting = false;
function openEmployeeAccount(employeeId, name) {
    employeeAccountForm.reset();
    employeeAccountForm.dataset.employeeId = String(employeeId);
    document.getElementById("employeeAccountTitle").textContent = "Usuario para " + name;
    document.getElementById("employeeAccountError").textContent = "";
    employeeAccountDialog.showModal();
    document.getElementById("employeeAccountUsername").focus();
}
document.getElementById("cancelEmployeeAccount").addEventListener("click", () => employeeAccountDialog.close());
employeeAccountDialog.addEventListener("cancel", event => { if (accountSubmitting) event.preventDefault(); });
employeeAccountDialog.addEventListener("close", () => { employeeAccountForm.reset(); delete employeeAccountForm.dataset.employeeId; });
employeeAccountForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (accountSubmitting || !isOwnerRole() || !employeeAccountForm.reportValidity()) return;
    const errorBox = document.getElementById("employeeAccountError");
    const username = document.getElementById("employeeAccountUsername").value.trim();
    if (username.length < 3) { errorBox.textContent = "El usuario debe tener al menos 3 caracteres."; return; }
    accountSubmitting = true;
    const save = document.getElementById("saveEmployeeAccount");
    const cancel = document.getElementById("cancelEmployeeAccount");
    save.disabled = cancel.disabled = true;
    errorBox.textContent = "";
    try {
        await apiRequest(authApi + "/users", { method: "POST", body: JSON.stringify({
            employeeId: Number(employeeAccountForm.dataset.employeeId), username,
            password: document.getElementById("employeeAccountPassword").value,
            role: document.getElementById("employeeAccountRole").value
        }) });
        employeeAccountDialog.close();
        await loadEmployees();
        alert("Acceso creado correctamente.");
    } catch (error) { errorBox.textContent = error.message; }
    finally { accountSubmitting = false; save.disabled = cancel.disabled = false; }
});

// Cambio de la contraseña propia: no se envían IDs ni se almacenan contraseñas.
const changePasswordDialog = document.getElementById("changePasswordDialog");
const changePasswordForm = document.getElementById("changePasswordForm");
let passwordSubmitting = false;
document.getElementById("changePasswordButton").addEventListener("click", () => {
    if (!currentProfile) return;
    changePasswordForm.reset();
    document.getElementById("changePasswordError").textContent = "";
    changePasswordDialog.showModal();
    document.getElementById("currentPassword").focus();
});
document.getElementById("cancelPassword").addEventListener("click", () => changePasswordDialog.close());
changePasswordDialog.addEventListener("cancel", event => { if (passwordSubmitting) event.preventDefault(); });
changePasswordDialog.addEventListener("close", () => changePasswordForm.reset());
changePasswordForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (passwordSubmitting || !currentProfile || !changePasswordForm.reportValidity()) return;
    const currentPassword = document.getElementById("currentPassword").value;
    const newPassword = document.getElementById("newPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;
    const errorBox = document.getElementById("changePasswordError");
    errorBox.textContent = "";
    if (newPassword.length < 10 || newPassword.length > 200) { errorBox.textContent = "La nueva contraseña debe tener entre 10 y 200 caracteres."; return; }
    if (newPassword !== confirmPassword) { errorBox.textContent = "Las nuevas contraseñas no coinciden."; return; }
    if (newPassword === currentPassword) { errorBox.textContent = "La nueva contraseña debe ser diferente de la actual."; return; }
    const save = document.getElementById("savePassword");
    const cancel = document.getElementById("cancelPassword");
    passwordSubmitting = true; save.disabled = cancel.disabled = true;
    try {
        await apiRequest(authApi + "/password", { method: "POST", body: JSON.stringify({currentPassword, newPassword, confirmPassword}) });
        changePasswordDialog.close();
        alert("Contraseña actualizada correctamente");
    } catch (error) { errorBox.textContent = error.message; }
    finally { passwordSubmitting = false; save.disabled = cancel.disabled = false; }
});
