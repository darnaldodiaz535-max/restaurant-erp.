// One refresh coordinator for header, dashboard navigation and mobile pull-to-refresh.
const screenRefreshers = new Map();
let activeRefreshFailures = null;
let currentRefresh = null;
let refreshStatusTimer;
function registerScreenRefresh(load) {
    for (const [callback, revision] of screenRefreshers) if (revision !== screenRevision) screenRefreshers.delete(callback);
    screenRefreshers.set(load, screenRevision);
}
function showRefreshStatus(message, error = false) {
    const status = document.getElementById("screenRefreshStatus");
    if (!status) return;
    clearTimeout(refreshStatusTimer);
    status.textContent = message; status.hidden = !message;
    status.classList.toggle("is-error", error);
    if (message && !error) refreshStatusTimer = setTimeout(() => { status.hidden = true; }, 4000);
}
function refreshCurrentScreen({ toTop = false } = {}) {
    if (!currentProfile) return Promise.resolve(false);
    if (currentRefresh) {
        if (currentRefresh.revision === screenRevision) {
            currentRefresh.toTop ||= toTop;
            return currentRefresh.promise;
        }
        return currentRefresh.promise.then(() => refreshCurrentScreen({ toTop }));
    }
    if (!personal.hidden && deletingEmployees.size) {
        showRefreshStatus("Espera a que termine la eliminación en curso.");
        return Promise.resolve(false);
    }
    const operation = { revision: screenRevision, toTop, promise: null };
    currentRefresh = operation;
    operation.promise = performScreenRefresh(operation).finally(() => { if (currentRefresh === operation) currentRefresh = null; });
    return operation.promise;
}
async function performScreenRefresh(operation) {
    const revision = operation.revision;
    const position = { left: window.scrollX, top: window.scrollY };
    const details = [...otherModule.querySelectorAll("details")].map(node => node.open);
    const button = document.getElementById("refreshHomeButton");
    const indicator = document.getElementById("pullRefreshIndicator");
    button.disabled = true; button.setAttribute("aria-busy", "true");
    indicator.hidden = false; indicator.classList.add("is-loading"); indicator.textContent = "Actualizando…";
    showRefreshStatus(""); activeRefreshFailures = [];
    let succeeded = true;
    try {
        const loads = [...screenRefreshers].filter(([, version]) => version === revision).map(([load]) => load);
        if (!dashboard.hidden) await loadDashboardMetrics();
        else if (!personal.hidden) await loadEmployees();
        else {
            const results = await Promise.allSettled(loads.map(load => load()));
            results.forEach(result => { if (result.status === "rejected") activeRefreshFailures.push(result.reason); });
        }
        if (activeRefreshFailures.length) throw activeRefreshFailures[0];
        if (revision === screenRevision) showRefreshStatus("Datos actualizados.");
    } catch (error) {
        succeeded = false;
        if (revision === screenRevision) showRefreshStatus(`No se pudo completar la actualización: ${error.message}. Se conservan los datos visibles.`, true);
    } finally {
        activeRefreshFailures = null;
        button.disabled = false; button.removeAttribute("aria-busy");
        indicator.hidden = true; indicator.classList.remove("is-loading"); indicator.style.removeProperty("--pull-distance");
        if (revision === screenRevision) {
            otherModule.querySelectorAll("details").forEach((node, index) => { if (index < details.length) node.open = details[index]; });
            if (operation.toTop) window.scrollTo({ top: 0, left: 0, behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth" });
            else window.scrollTo(position);
        }
    }
    return succeeded;
}
document.getElementById("refreshHomeButton").addEventListener("click", () => void refreshCurrentScreen());
document.getElementById("dashboardHomeButton").addEventListener("click", () => {
    const destination = menuItems.find(link => navLabel(link) === "Dashboard" && !link.hidden);
    if (destination) showScreen(destination);
});

// Only claim a deliberate downward gesture begun at the top of the document.
let pull = null;
const pullThreshold = 76;
const interactive = "input,textarea,select,button,a,[contenteditable],dialog,.notification-menu";
function canStartPull(event) {
    if (!currentProfile || currentRefresh || event.touches.length !== 1 || window.scrollY > 1
        || document.querySelector("dialog[open]") || event.target.closest(interactive)) return false;
    for (let node = event.target; node && node !== document.body; node = node.parentElement) {
        if ((node.scrollHeight > node.clientHeight + 2 && node.scrollTop > 0)
            || (node.scrollWidth > node.clientWidth + 2 && getComputedStyle(node).overflowX !== "visible")) return false;
    }
    return true;
}
function resetPull() {
    pull = null;
    if (!currentRefresh) { const indicator = document.getElementById("pullRefreshIndicator"); indicator.hidden = true; indicator.style.removeProperty("--pull-distance"); }
}
document.addEventListener("touchstart", event => {
    resetPull();
    if (canStartPull(event)) pull = { x: event.touches[0].clientX, y: event.touches[0].clientY, distance: 0, revision: screenRevision };
}, { passive: true });
document.addEventListener("touchmove", event => {
    if (!pull) return;
    if (event.touches.length !== 1 || window.scrollY > 1 || screenRevision !== pull.revision) { resetPull(); return; }
    const dx = Math.abs(event.touches[0].clientX - pull.x), dy = event.touches[0].clientY - pull.y;
    if (dy < 0 || dx > 20 && dx > dy / 1.3) { resetPull(); return; }
    if (dy < 18 || dy < dx * 1.3) return;
    if (event.cancelable) event.preventDefault();
    pull.distance = Math.min(dy * .65, 110);
    const indicator = document.getElementById("pullRefreshIndicator");
    indicator.hidden = false; indicator.style.setProperty("--pull-distance", `${pull.distance}px`);
    indicator.textContent = pull.distance >= pullThreshold ? "Suelta para actualizar" : "Desliza para actualizar";
}, { passive: false });
document.addEventListener("touchend", () => {
    const ready = pull && pull.distance >= pullThreshold && pull.revision === screenRevision;
    resetPull();
    if (ready) void refreshCurrentScreen();
}, { passive: true });
document.addEventListener("touchcancel", resetPull, { passive: true });

if (typeof ResizeObserver !== "undefined") {
    const navigation = document.querySelector(".quick-navigation");
    new ResizeObserver(() => document.documentElement.style.setProperty("--quick-nav-height", `${navigation.getBoundingClientRect().height}px`)).observe(navigation);
}
