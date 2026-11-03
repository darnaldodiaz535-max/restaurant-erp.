// Check for published updates when an installed app returns to the foreground.
// Let the user finish an unsent photograph/message before reloading.
(() => {
    if (!("serviceWorker" in navigator)) return;
    let registration, controller = navigator.serviceWorker.controller, lastCheck = 0;
    function showUpdate() {
        if (document.getElementById("marigexUpdateNotice")) return;
        const notice = document.createElement("aside");
        notice.id = "marigexUpdateNotice";
        notice.setAttribute("role", "status");
        Object.assign(notice.style, {position:"fixed",bottom:"max(16px, env(safe-area-inset-bottom))",left:"16px",right:"16px",zIndex:"10000",background:"#172554",color:"white",padding:"16px",borderRadius:"12px",display:"flex",flexWrap:"wrap",gap:"12px",alignItems:"center",boxShadow:"0 4px 24px #0004"});
        const text = document.createElement("span");
        text.textContent = "Hay una actualización de MariGex. Termina lo que estés enviando y pulsa Actualizar.";
        const button = document.createElement("button");
        button.type = "button"; button.textContent = "Actualizar";
        Object.assign(button.style, {padding:"10px 16px",borderRadius:"8px",border:"0",font:"inherit",fontWeight:"700",cursor:"pointer",background:"white",color:"#172554"});
        button.addEventListener("click", () => location.reload());
        notice.append(text, button); document.body.appendChild(notice);
    }
    navigator.serviceWorker.addEventListener("controllerchange", () => {
        if (controller) showUpdate();
        controller = navigator.serviceWorker.controller;
    });
    async function check() {
        if (!registration || document.visibilityState === "hidden" || Date.now() - lastCheck < 60000) return;
        lastCheck = Date.now();
        try { await registration.update(); } catch { /* Offline use remains available. */ }
    }
    window.addEventListener("load", async () => {
        try {
            registration = await navigator.serviceWorker.register("/service-worker.js", {updateViaCache:"none"});
            await check();
        } catch { /* The application also works without service-worker support. */ }
    });
    document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") void check(); });
    window.addEventListener("pageshow", () => void check());
    window.addEventListener("online", () => void check());
})();
