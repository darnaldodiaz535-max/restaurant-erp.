// Extensiones de la aplicación existente: comparten sesión, estilos y llamadas API.
const AREA_OPTIONS = [
    {key:"COCINA", label:"Cocina", icon:"🍳"},
    {key:"SALON", label:"Salón", icon:"🍽️"},
    {key:"BARRA", label:"Barra", icon:"🍹"},
    {key:"COPERIA", label:"Copería", icon:"🧼"}
];
function normalizeArea(value) { return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toUpperCase(); }
function areaLabel(key) { return AREA_OPTIONS.find(a => a.key === key)?.label || "Pendientes de clasificar"; }
function canViewArea(key) { return Boolean(currentProfile); }
function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
}
function actionButton(text, action, primary = false) {
    const button = element("button", text, primary ? "primary-button" : "secondary-button");
    button.type = "button"; button.addEventListener("click", action); return button;
}
function areaHeader(title, description) {
    const header = element("div", undefined, "module-header");
    const copy = element("div"); copy.append(element("h2", title), element("p", description)); header.append(copy);
    return header;
}
function prepareAreaScreen(title, description) {
    screenRevision++;
    otherModule.replaceChildren(areaHeader(title, description));
    otherModule.className = "module-screen";
    otherModule.style.setProperty("--module-accent", "#2563eb");
    dashboard.hidden = true; personal.hidden = true; otherModule.hidden = false;
}
function renderTaskAreas() {
    prepareAreaScreen("Tareas diarias", "Selecciona un área para consultar sus tareas pendientes, enviadas y completadas.");
    const grid = element("div", undefined, "area-grid");
    AREA_OPTIONS.forEach(area => {
        const card = actionButton(`${area.icon} ${area.label}`, () => void renderModule("Tareas diarias", area.key));
        card.className = "area-card";
        card.disabled = !canViewArea(area.key);
        card.title = card.disabled ? "Disponible para el equipo de esta área y sus jefaturas" : `Ver tareas de ${area.label}`;
        grid.append(card);
    });
    otherModule.append(grid);
    if (currentProfile) otherModule.append(actionButton(isOwnerRole() ? "Clasificar tareas antiguas sin área" : "Ver tareas sin área", () => void renderModule("Tareas diarias", "SIN_AREA")));
    if (isWorkerRole() && !AREA_OPTIONS.some(a => canViewArea(a.key))) otherModule.append(element("p", "Tu ficha no tiene una de estas áreas. Solicita al administrador que revise el campo Área en Personal.", "empty-state"));
}
async function renderArea(area) {
    if (!canViewArea(area)) return;
    prepareAreaScreen(areaLabel(area), "Equipo y actividades del área. Las fichas se mantienen en Personal.");
    const box = element("section", undefined, "data-section area-content");
    const actions = element("div", undefined, "module-controls");
    actions.append(actionButton("Ver tareas del área", () => void renderModule("Tareas diarias", area), true));
    if (area === "COCINA") actions.append(actionButton("Preparaciones de cocina", () => void renderModule("Cocina")));
    box.append(actions, element("h3", "Trabajadores del área")); otherModule.append(box);
    const message = element("p", "Cargando equipo…", "empty-state"); box.append(message);
    try {
        const members = await apiRequest(`/api/areas/${area}/empleados`);
        message.textContent = members.length ? "" : "No hay trabajadores registrados en esta área.";
        const list = element("div", undefined, "team-grid");
        members.forEach(member => {
            const card = element("article", undefined, "team-card");
            card.append(element("strong", member.name), element("p", member.position), element("small", member.status === "ACTIVO" ? "Activo" : "Inactivo")); list.append(card);
        });
        box.append(list);
    } catch (error) { message.textContent = error.message; }
}

async function renderSchedules(recordsSection) {
    const box = element("section", undefined, "data-section schedule-section");
    const head = element("div", undefined, "module-header");
    head.append(element("h3", "Horario del equipo"));
    const upload = actionButton("Crear / Subir horario", () => { form.reset(); delete form.dataset.replace; form.hidden = false; title.focus(); }, true);
    upload.disabled = !isOwnerRole(); upload.title = isOwnerRole() ? "Subir una imagen" : "Solo el administrador puede subir horarios";
    head.append(upload); box.append(head, element("p", "Consulta el horario en imagen o los registros por trabajador que aparecen debajo."));
    const form = element("form", undefined, "record-form"); form.hidden = true;
    const titleLabel = element("label", "Título del horario");
    const title = element("input"); title.required = true; title.maxLength = 160; title.name = "title"; titleLabel.append(title);
    const imageLabel = element("label", "Imagen JPG, PNG o WebP (máximo 4 MB)");
    const image = element("input"); image.type = "file"; image.accept = "image/jpeg,image/png,image/webp"; image.required = true; image.name = "image"; imageLabel.append(image);
    const fields = element("div", undefined, "record-form-fields"); fields.append(titleLabel, imageLabel);
    const recipientsLabel=element("label","Avisar a estos trabajadores (selecciona los afectados; Ctrl permite varios)");
    const recipients=element("select"); recipients.multiple=true; recipients.name="employeeIds"; recipients.size=5; recipientsLabel.append(recipients);
    fields.append(recipientsLabel);
    fields.append(element("small","Sin selección, una imagen nueva no envía avisos. Al reemplazar sin selección se conservan sus destinatarios anteriores."));
    if(isOwnerRole()) { try { const staff=await apiRequest(employeesApi);staff.filter(e=>e.active).forEach(e=>recipients.add(new Option(e.name+" · "+e.area,e.id))); } catch(error) { recipientsLabel.append(element("small",error.message)); } }
    const save = element("button", "Guardar horario", "primary-button"); save.type = "submit";
    form.append(fields, save, actionButton("Cancelar", () => form.hidden = true)); box.append(form);
    const feedback = element("p", "Cargando horarios…", "empty-state"); feedback.setAttribute("role", "status"); box.append(feedback);
    const gallery = element("div", undefined, "schedule-gallery"); box.append(gallery);
    recordsSection.before(box);
    if(isOwnerRole()) renderAiSchedules(box);
    const load = async () => {
        try {
            const rows = await apiRequest("/api/horarios"); gallery.replaceChildren();
            feedback.textContent = rows.length ? "" : "Todavía no hay una imagen del horario.";
            rows.forEach(row => {
                const card = element("article", undefined, "schedule-card");
                card.append(element("h4", row.title), element("small", `Guardado: ${row.createdAt}`));
                const link = element("a"); link.href = `/api/horarios/${row.id}/imagen?v=${encodeURIComponent(row.createdAt)}`; link.target = "_blank"; link.rel = "noopener";
                const preview = element("img"); preview.src = link.href; preview.alt = row.title; preview.loading = "lazy";
                link.append(preview, element("span", "Abrir imagen completa")); card.append(link);
                const actions = element("div", undefined, "module-controls");
                const replace = actionButton("Reemplazar", () => { form.reset(); form.dataset.replace = row.id; title.value = row.title; Array.from(recipients.options).forEach(o=>o.selected=(row.employeeIds||[]).includes(Number(o.value))); form.hidden = false; title.focus(); });
                const remove = actionButton("Eliminar horario", async () => {
                    if (!confirm(`¿Eliminar el horario «${row.title}»?`)) return;
                    remove.disabled = true;
                    try { await apiRequest(`/api/horarios/${row.id}`, {method:"DELETE"}); await load(); await loadDashboardMetrics(); }
                    catch(error) { feedback.textContent = error.message; remove.disabled = false; }
                });
                replace.disabled = remove.disabled = !isOwnerRole();
                if (!isOwnerRole()) replace.title = remove.title = "Solo el administrador puede modificar horarios";
                actions.append(replace, remove); card.append(actions); gallery.append(card);
            });
        } catch (error) { feedback.textContent = `No se pudieron cargar los horarios: ${error.message}`; }
    };
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (!isOwnerRole()) return;
        const file = image.files?.[0];
        if (!file || file.size > 4 * 1024 * 1024) { feedback.textContent = "Selecciona una imagen de hasta 4 MB."; return; }
        const data = new FormData(form); if (form.dataset.replace) data.append("replaceId", form.dataset.replace);
        save.disabled = true;
        try { await uploadRequest("/api/horarios", data); form.hidden = true; form.reset(); await load(); await loadDashboardMetrics(); }
        catch(error) { feedback.textContent = error.message; }
        finally { save.disabled = false; }
    });
    await load();
}

async function renderPlazas() {
    if (!currentProfile) return;
    prepareAreaScreen("Plazas del restaurante", "Define los puestos y cupos necesarios. La ocupación se calcula con el cargo y área de los trabajadores activos.");
    otherModule.append(actionButton("← Volver a Personal", () => showScreen(menuItems.find(link => navLabel(link) === "Personal"))));
    const box = element("section", undefined, "data-section area-content"); otherModule.append(box);
    const form = element("form", undefined, "record-form");
    const fields = element("div", undefined, "record-form-fields");
    const position = element("input"); position.required = true; position.maxLength = 80;
    const area = element("select"); AREA_OPTIONS.forEach(a => area.add(new Option(a.label, a.key)));
    const slots = element("input"); slots.type = "number"; slots.min = 1; slots.max = 9999; slots.step = 1; slots.value = 1; slots.required = true;
    const active = element("select"); active.add(new Option("Activa", "true")); active.add(new Option("Inactiva", "false"));
    [["Cargo (igual al de Personal)",position],["Área",area],["Cupos",slots],["Estado",active]].forEach(([text,input]) => { const label = element("label",text); label.append(input); fields.append(label); });
    const save = element("button","Agregar plaza","primary-button"); save.type = "submit";
    const reset = () => { form.reset(); slots.value = 1; delete form.dataset.id; save.textContent = "Agregar plaza"; };
    form.append(fields, save, actionButton("Limpiar / Cancelar edición", reset)); form.hidden = !isOwnerRole(); box.append(form);
    const feedback = element("p", "", "empty-state"); feedback.setAttribute("role","status"); box.append(feedback);
    const tableWrap = element("div", undefined,"table-container"); box.append(tableWrap);
    const load = async () => {
        try {
            const rows = await apiRequest("/api/plazas");
            const table = element("table"); const head = element("tr");
            ["Cargo","Área","Cupos","Ocupados","Disponibles","Estado","Acciones"].forEach(text => head.append(element("th",text)));
            const thead = element("thead"); thead.append(head); table.append(thead); const body = element("tbody"); table.append(body);
            rows.forEach(row => {
                const tr = element("tr");
                [row.position,areaLabel(row.area),row.slots,row.occupied,Math.max(0,row.slots-row.occupied),row.active ? "Activa" : "Inactiva"].forEach(text => tr.append(element("td",text)));
                const td = element("td"); if (isOwnerRole()) td.append(actionButton("Editar / Desactivar", () => { position.value=row.position; area.value=row.area; slots.value=row.slots; active.value=String(row.active); form.dataset.id=row.id; save.textContent="Guardar cambios"; position.focus(); }));
                tr.append(td); body.append(tr);
            });
            tableWrap.replaceChildren(table); feedback.textContent = rows.length ? "Desactivar una plaza conserva sus datos y no modifica empleados." : "No hay plazas definidas. Agrega la primera.";
        } catch(error) { feedback.textContent = error.message; }
    };
    form.addEventListener("submit", async event => {
        event.preventDefault(); if (!isOwnerRole()) return; save.disabled=true;
        try {
            await apiRequest(`/api/plazas${form.dataset.id ? `/${form.dataset.id}` : ""}`, {method:form.dataset.id ? "PUT" : "POST", body:JSON.stringify({position:position.value.trim(),area:area.value,slots:Number(slots.value),active:active.value==="true"})});
            reset(); await load(); await loadDashboardMetrics();
        } catch(error) { feedback.textContent=error.message; }
        finally { save.disabled=false; }
    });
    await load();
}
const managePlazasButton = actionButton("Agregar plaza / Gestionar plazas", () => void renderPlazas());
managePlazasButton.id = "managePlazasButton"; managePlazasButton.hidden = true;
document.getElementById("addEmployeeButton").after(managePlazasButton);
setInterval(() => { if (!document.hidden && currentProfile) void loadDashboardOverview(); }, 60000);
void initializeAuthentication();
