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
    Inventario: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="M3 8v9l9 5 9-5V8M12 13v9"/>',
    Cocina: '<path d="M6 13h12l-1 8H7l-1-8ZM5 13a3 3 0 0 1 0-6 4 4 0 0 1 8-1 4 4 0 0 1 7 2 3 3 0 0 1-1 5"/>',
    Salón: '<circle cx="12" cy="12" r="7"/><path d="M2 3v6M5 3v6M2 6h3M3.5 9v12M21 3v18"/>',
    Barra: '<path d="M3 3h18l-9 10L3 3ZM12 13v8M7 21h10M7 7h10"/>',
    Copería: '<path d="M3 12h18l-3 8H6l-3-8ZM9 12V5a3 3 0 0 1 6 0M13 6h4M6 9h2M18 9h2"/>',
    Producción: '<path d="M4 20h16M6 20V9l6-5 6 5v11M9 20v-6h6v6"/>',
    "Mise en place": '<path d="m14 6 4-4 4 4-4 4M3 21l11-11M8 8l8 8M4 4l4 4"/>',
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
function isSalonRole() { return accessRole() === "JEFE_SALON"; }
function isKitchenRole() { return accessRole() === "JEFE_COCINA"; }
function isWorkerRole() { return ["TRABAJADOR", "EMPLEADO"].includes(accessRole()); }
const kitchenModuleKeys = new Set(["tareas-diarias", "inventario", "cocina", "produccion", "mise-en-place", "limpieza", "control-sanitario", "compras", "mermas", "incidencias", "asistencia", "turnos"]);
function canManageModule(key) {
    if (["personal", "turnos", "configuracion"].includes(key)) return isOwnerRole();
    return isOwnerRole() || isSalonRole() || (isKitchenRole() && kitchenModuleKeys.has(key));
}
function canSeeModule(key) { return Boolean(currentProfile); }
async function uploadRequest(url, formData) {
    const response = await fetch(url, { method: "POST", body: formData, credentials: "same-origin" });
    if (!response.ok) { const problem = await response.json().catch(() => ({})); throw new Error(problem.error || `Error del servidor (${response.status})`); }
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
        const error = new Error(problem.error || problem.message || `Error del servidor (${response.status})`); error.status = response.status; throw error;
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
        const counts = document.querySelectorAll(".personal-summary .summary-card strong");
        if (counts.length >= 3) {
            counts[0].textContent = employees.length;
            counts[1].textContent = employees.filter((employee) => employee.active).length;
            counts[2].textContent = employees.filter((employee) => !employee.active).length;
        }
    } catch (error) {
        console.error("No se pudieron cargar los empleados desde Oracle:", error);
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
    try {
        const metrics = await apiRequest("/api/dashboard");
        document.querySelectorAll(".dashboard-cards .card").forEach((card) => {
            const label = card.querySelector("h3")?.textContent.trim();
            const value = metrics[label];
            const number = card.querySelector("strong");
            if (number && value !== undefined) number.textContent = String(value);
        });
    } catch (error) { console.error("No se pudieron actualizar los indicadores:", error); }
    await loadDashboardOverview();
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
                activities.forEach((activity) => {
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

                    if (isOwnerRole()) {
                        const remove = document.createElement("button");
                        remove.type = "button";
                        remove.className = "activity-delete";
                        remove.textContent = "Eliminar";
                        remove.setAttribute(
                            "aria-label",
                            `Eliminar actividad: ${title.textContent}`);

                     remove.addEventListener("click", async () => {
    swipe.remove();

    try {
        await apiRequest(
            `/api/dashboard/activity/${activity.id}`,
            { method: "DELETE" });
        await loadDashboardMetrics();
    } catch (error) {
        await loadDashboardMetrics();
        window.alert(
            `No se pudo eliminar la actividad: ${error.message}`);
    }
});

                        let startX = 0;
                        let startY = 0;

                        item.addEventListener(
                            "touchstart",
                            (event) => {
                                const touch = event.changedTouches[0];
                                startX = touch.clientX;
                                startY = touch.clientY;
                            },
                            { passive: true });

                        item.addEventListener(
                            "touchend",
                            (event) => {
                                const touch = event.changedTouches[0];
                                const dx = touch.clientX - startX;
                                const dy = touch.clientY - startY;

                                if (Math.abs(dx) < 45
                                        || Math.abs(dx) < Math.abs(dy)) {
                                    return;
                                }

                                if (dx < 0) {
                                    swipe.classList.add(
                                        "activity-swipe--open");
                                } else {
                                    swipe.classList.remove(
                                        "activity-swipe--open");
                                }
                            },
                            { passive: true });

                        swipe.append(remove);
                    }

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
    else if (["Cocina", "Salón", "Barra", "Copería"].includes(label)) void renderArea(normalizeArea(label));
    else void renderModule(label);
    activateNav(link);
    if (label === "Dashboard") void loadDashboardMetrics();
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
    "Mise en place": ["Preparación", "Cantidad", "Responsable", "Hora límite", "Estado"],
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
    const revision = ++screenRevision;
    otherModule.replaceChildren();
    const slug = slugifyLabel(name);
    otherModule.className = `module-screen module-screen--${slug}`;
    const accents = {
        asistencia: "#0891b2", turnos: "#7c3aed", "tareas-diarias": "#2563eb", inventario: "#059669",
        cocina: "#ea580c", produccion: "#d97706", "mise-en-place": "#0d9488", reservas: "#db2777",
        limpieza: "#0284c7", incidencias: "#dc2626", compras: "#4f46e5", mermas: "#64748b",
        "control-sanitario": "#16a34a", reportes: "#9333ea", configuracion: "#475569"
    };
    otherModule.style.setProperty("--module-accent", accents[slug] || "#2563eb");
    const icons = {
        asistencia: "🕒", turnos: "📅", "tareas-diarias": "✅", inventario: "📦", cocina: "👨‍🍳",
        produccion: "🍳", "mise-en-place": "🔪", reservas: "🗓️", limpieza: "🧹", incidencias: "⚠️",
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
        "Mise en place": "Planifica las preparaciones previas al servicio.",
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
    const add = document.createElement("button"); add.className = "primary-button"; add.type = "button"; add.textContent = `+ ${name === "Horarios" ? "Crear horario por trabajador" : name === "Asistencia" ? "Registrar asistencia" : "Agregar registro"}`;
    if (!canManageModule(slug)) {
        add.disabled = true; add.title = "Solo administración puede gestionar estos registros";
    }
    if (selectedArea === "SIN_AREA") add.hidden = true;
    header.append(heading, add);
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
          record.values.forEach((value, columnIndex) => {
    if (slug === "inventario" && columnIndex === 6) return;

    const td = document.createElement("td");
    td.textContent = value;
    row.appendChild(td);
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
    if (name === "Configuración") await renderNotificationPreferences(section);
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
employeeSearch?.addEventListener("input", () => {
    const query = employeeSearch.value.trim().toLocaleLowerCase("es");
    [...employeeBody.rows].forEach((row) => { row.hidden = !row.textContent.toLocaleLowerCase("es").includes(query); });
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
    const roleLabels = { ADMIN: "Administrador", EMPRESA: "Jefe de empresa", JEFE_SALON: "Jefe de salón", JEFE_COCINA: "Jefe de cocina", JEFE_LOCAL: "Jefe de local", TRABAJADOR: "Trabajador", EMPLEADO: "Trabajador" };
    document.getElementById("currentUserRole").textContent = roleLabels[profile.role] || profile.position;
}
async function enterApplication(profile) {
    fillProfile(profile);
    document.body.classList.remove("logged-out");
    void ensureExperienceLoaded().then(() => {
        if (currentProfile === profile) return bindPushAccount();
    }).catch(error => console.warn('El acceso continúa sin Push:', error.message));
    document.getElementById("addEmployeeButton").disabled = !isOwnerRole();
    document.getElementById("managePlazasButton").hidden = false;
    document.getElementById("managePlazasButton").textContent = isOwnerRole() ? "Agregar plaza / Gestionar plazas" : "Ver plazas";
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
    document.getElementById("auth-title").textContent = "MariGex";
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
    document.getElementById("profile-name").textContent = currentProfile.name;
    document.getElementById("profile-position").textContent = currentProfile.position;
    document.getElementById("profile-area").textContent = currentProfile.area;
    document.getElementById("profile-username").textContent = currentProfile.username;
    const roleLabels = { ADMIN: "Administrador", EMPRESA: "Jefe de empresa", JEFE_SALON: "Jefe de salón", JEFE_COCINA: "Jefe de cocina", JEFE_LOCAL: "Jefe de local", TRABAJADOR: "Trabajador", EMPLEADO: "Trabajador" };
    document.getElementById("profile-role").textContent = roleLabels[currentProfile.role] || currentProfile.position;
    profileDialog.showModal();
}
document.getElementById("closeProfile").addEventListener("click", () => profileDialog.close());
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
