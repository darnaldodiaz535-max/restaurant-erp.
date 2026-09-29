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
                    const item = document.createElement("div"); item.className = "activity-item";
                    const icon = document.createElement("span"); icon.className = "activity-icon";
                    const moduleLabel = menuItems.map(navLabel).find((label) => slugifyLabel(label) === activity.module) || activity.module;
                    icon.textContent = moduleLabel === "Personal" ? "👥" : moduleLabel === "Asistencia" ? "🕒" : moduleLabel.includes("Tarea") ? "✅" : "📋";
                    const content = document.createElement("div"); content.className = "activity-content";
                    const title = document.createElement("strong");
                    const actionNames = { CREAR: "Registro agregado", ACTUALIZAR: "Registro actualizado", ELIMINAR: "Registro eliminado", ENVIAR_TAREA: "Tarea enviada para revisión", COMPLETAR_TAREA: "Tarea completada" };
                    title.textContent = `${actionNames[activity.action] || activity.action} · ${moduleLabel}`;
                    const detail = document.createElement("p"); detail.textContent = `${activity.detail} · Usuario: ${activity.username}`;
                    const time = document.createElement("small"); time.textContent = activity.createdAt;
                    content.append(title, detail, time); item.append(icon, content); activityList.appendChild(item);
                });
            }
        }
        const alertSection = document.querySelector(".alerts");
        const alertList = document.querySelector(".alert-list");
        if (alertSection && alertList) {
            alertList.replaceChildren();
            alertSection.hidden = alerts.length === 0;
            alerts.forEach((alert) => {
                const item = document.createElement("div"); item.className = "alert-item"; item.tabIndex = 0; item.setAttribute("role", "link");
                const icon = document.createElement("span"); icon.className = "alert-icon"; icon.textContent = alert.icon;
                const content = document.createElement("div"); content.className = "alert-content";
                const title = document.createElement("strong"); title.textContent = alert.module;
                const message = document.createElement("p"); message.textContent = alert.message;
                content.append(title, message); item.append(icon, content);
                const goToModule = () => { const target = menuItems.find((link) => navLabel(link) === alert.module); if (target) showScreen(target); };
                item.addEventListener("click", goToModule);
                item.addEventListener("keydown", (event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); goToModule(); } });
                alertList.appendChild(item);
            });
        }
        await loadUserNotifications();
    } catch (error) {
        console.error("No se pudo actualizar la actividad y las alertas:", error);
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
    else if (["Cocina", "Salón", "Barra", "Copería"].includes(label)) void renderArea(normalizeArea(label));
    else void renderModule(label);
    activateNav(link);
    if (label === "Dashboard") void loadDashboardMetrics();
}

const moduleKey = (name) => `restaurant-erp-module-${name}`;
const moduleSchemas = {
    Asistencia: ["Trabajador", "Fecha", "Hora de entrada", "Hora de salida", "Estado"],
    Horarios: ["Trabajador", "Fecha", "Hora de inicio", "Hora de término", "Área"],
    "Tareas diarias": ["Tarea", "Responsable", "Fecha", "Estado", "Área"],
    Inventario: ["Insumo", "Categoría", "Stock actual", "Unidad", "Stock mínimo"],
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
        const wrap = document.createElement("label"); wrap.textContent = label;
        const isArea = slug === "tareas-diarias" && label === "Área";
        const input = isArea || ["Trabajador", "Responsable"].includes(label) ? document.createElement("select") : document.createElement("input");
        if (isArea) {
            AREA_OPTIONS.forEach((a) => input.add(new Option(a.label, a.key)));
            input.value = selectedArea === "SIN_AREA" ? "" : selectedArea;
            input.disabled = selectedArea !== "SIN_AREA";
        } else if (input.tagName === "SELECT") {
            input.dataset.employeeReference = "true";
            input.add(new Option("Selecciona una persona", ""));
        }
        if (input.tagName !== "SELECT") input.type = /fecha y hora/i.test(label) ? "datetime-local" : /fecha/i.test(label) ? "date" : /hora/i.test(label) ? "time" : /cantidad|stock|personas|temperatura/i.test(label) ? "number" : "text";
        if (input.type === "number") { input.step = "any"; input.min = "0"; }
        input.required = true; input.setAttribute("aria-label", label); wrap.appendChild(input); formFields.appendChild(wrap); return input;
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
    const tableWrap = document.createElement("div"); tableWrap.className = "table-container";
    const table = document.createElement("table");
    const thead = document.createElement("thead"); const headRow = document.createElement("tr");
    fields.concat("Acciones").forEach((label) => { const th = document.createElement("th"); th.textContent = label; headRow.appendChild(th); });
    thead.appendChild(headRow); const tbody = document.createElement("tbody"); table.append(thead, tbody); tableWrap.appendChild(table);
    const empty = document.createElement("p"); empty.className = "empty-state"; empty.textContent = "Todavía no hay registros. Usa el botón para agregar el primero.";
    if (selectedArea) controls.append(actionButton("← Áreas de tareas", renderTaskAreas));
    controls.append(search); section.append(controls, form, tableWrap, empty);
    if (revision !== screenRevision) return;
    otherModule.append(header, section); otherModule.hidden = false;
    let records = [];
    let loadError = "";
    const draw = () => {
        tbody.replaceChildren();
        const filtered = records.map((record, index) => ({ record, index })).filter(({ record }) => record.values.join(" ").toLocaleLowerCase("es").includes(search.value.trim().toLocaleLowerCase("es")));
        filtered.forEach(({ record, index }) => {
            const row = document.createElement("tr");
            record.values.forEach((value) => { const td = document.createElement("td"); td.textContent = value; row.appendChild(td); });
            const actions = document.createElement("td");
            if (canManageModule(slug) && slug === "tareas-diarias" && Number(record.references[1]) === Number(currentProfile.employeeId)) {
                const statusIndex = (moduleSchemas[name] || []).indexOf("Estado");
                const state = String(record.values[statusIndex] || "").toUpperCase();
                const photo = document.createElement("input"); photo.type = "file"; photo.accept = "image/jpeg,image/png,image/webp"; photo.hidden = true;
                const send = document.createElement("button"); send.type = "button"; send.className = "table-button"; send.textContent = state === "EN_REVISION" ? "Foto enviada" : "Completar con foto";
                send.disabled = ["EN_REVISION", "COMPLETADA", "COMPLETADO", "FINALIZADA", "FINALIZADO"].includes(state);
                send.addEventListener("click", () => photo.click());
                photo.addEventListener("change", async () => {
                    if (!photo.files?.[0]) return;
                    const data = new FormData(); data.append("taskId", String(record.id)); data.append("photo", photo.files[0]);
                    try { await uploadRequest("/api/evidencias", data); alert("La tarea y la foto quedaron guardadas para revisión."); await loadRecords(); }
                    catch (error) { alert(`No se pudo enviar la foto: ${error.message}`); }
                    finally { photo.value = ""; }
                });
                actions.append(send, photo);
            }
            if (canManageModule(slug)) {
            const edit = document.createElement("button"); edit.type = "button"; edit.className = "table-button"; edit.textContent = "Editar";
            const remove = document.createElement("button"); remove.type = "button"; remove.className = "table-button"; remove.textContent = "Eliminar";
            edit.addEventListener("click", () => { inputs.forEach((input, i) => { input.value = input.dataset.employeeReference ? (record.references[i] ?? "") : (record.values[i] || ""); }); form.dataset.editing = String(index); form.hidden = false; submit.textContent = "Guardar cambios"; inputs[0].focus(); });
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
    add.addEventListener("click", () => { form.reset(); restoreFixedFields(); if (isWorkerRole()) employeeSelects.forEach((select) => { select.value = String(currentProfile.employeeId); }); delete form.dataset.editing; submit.textContent = "Guardar"; form.hidden = false; inputs[0].focus(); });
    cancel.addEventListener("click", () => { form.reset(); delete form.dataset.editing; form.hidden = true; });
    form.addEventListener("submit", async (event) => {
        event.preventDefault(); if (!canManageModule(slug)) return; const values = inputs.map((input) => input.value.trim());
        if (values.some((value) => !value)) return;
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
            const approve = actionButton(item.status === "REVISADA" ? "Revisada" : "Aprobar tarea", async () => { try { await apiRequest(`/api/evidencias/${item.id}/revisar`, {method:"POST"}); await renderModule("Tareas diarias", selectedArea); await loadDashboardMetrics(); } catch (error) { alert(error.message); } });
            approve.disabled = !isOwnerRole() || item.status === "REVISADA"; actionCell.append(approve);
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
    resetNotifications();
    authScreen.hidden = false;
    loginForm.hidden = false;
    setupForm.hidden = true;
    document.getElementById("auth-title").textContent = "Restaurant ERP";
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
    try { await apiRequest(`${authApi}/logout`, { method: "POST" }); }
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
