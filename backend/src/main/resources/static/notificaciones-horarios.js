// Personal inbox. Never use the global activity stream as a user's inbox.
let noticeBefore=0;
function resetNotifications() {
    noticeBefore=0;
    notificationMenu?.querySelectorAll('.notification-item,.notice-more').forEach(n=>n.remove());
    notificationMenu?.classList.remove('show');
    const badge=document.querySelector('.notification-badge');if(badge){badge.textContent='0';badge.hidden=true;}
    const count=notificationMenu?.querySelector('.notification-header span');if(count)count.textContent='Sin avisos';
}
async function loadUserNotifications(more=false) {
    if(!currentProfile || !notificationMenu) return;
    const profile=currentProfile;
    try {
        const data=await apiRequest('/api/notificaciones'+(more?'?before='+noticeBefore:''));
        if(currentProfile!==profile)return;
        if(!more) notificationMenu.querySelectorAll('.notification-item').forEach(n=>n.remove());
        notificationMenu.querySelectorAll('.notice-more').forEach(n=>n.remove());
        const badge=document.querySelector('.notification-badge');badge.textContent=String(data.unread);badge.hidden=!data.unread;
        notificationMenu.querySelector('.notification-header span').textContent=data.unread+' pendientes';
        notificationBtn.setAttribute('aria-label',`Notificaciones, ${data.unread} pendientes`);
        const footer=document.getElementById('markNotificationsRead');footer.textContent='Marcar todas como leídas';footer.hidden=!data.unread;
        data.items.forEach(notice=>{
            const item=element('div',undefined,'notification-item'+(notice.read?' notice-read':''));
            const content=element('div');content.append(element('strong',notice.module),element('p',notice.message),element('small',notice.createdAt));
            const open=actionButton('Abrir',async()=>{
                try {await apiRequest(`/api/notificaciones/${notice.id}/leida`,{method:'POST'});await loadUserNotifications();notificationMenu.classList.remove('show');
                    const link=Array.from(menuItems).find(l=>navLabel(l)===notice.module);if(link)showScreen(link);
                }catch(error){alert(error.message);}
            });content.append(open);
            if(!notice.read)content.append(actionButton('Marcar leída',async()=>{try{await apiRequest(`/api/notificaciones/${notice.id}/leida`,{method:'POST'});await loadUserNotifications();}catch(error){alert(error.message);}}));
            item.append(content);notificationMenu.insertBefore(item,footer);noticeBefore=notice.id;
        });
        if(!data.items.length && !more)notificationMenu.insertBefore(element('div','No tienes notificaciones.','notification-item'),footer);
        if(data.hasMore){const next=actionButton('Ver anteriores',()=>void loadUserNotifications(true));next.classList.add('notice-more');notificationMenu.insertBefore(next,footer);}
    }catch(error){
        if(currentProfile!==profile)return;
        notificationMenu.querySelector('.notification-header span').textContent='No se pudieron cargar';
        console.error('Notificaciones:',error.message);
    }
}
function renderAiSchedules(imageSection) {
    const section=element('section',undefined,'data-section ai-schedule-section');imageSection.after(section);
    const feedback=element('p','','empty-state');feedback.setAttribute('role','status');
    const form=element('form',undefined,'record-form');form.hidden=true;
    const start=element('input');start.type='date';start.required=true;
    const instructions=element('textarea');instructions.required=true;instructions.maxLength=6000;instructions.rows=5;instructions.placeholder='Abraham descansa el martes. Necesito 3 personas en cocina por turno…';
    const fields=element('div',undefined,'record-form-fields');
    for(const [text,input] of [['Primer día (se generan 7 días)',start],['Instrucciones',instructions]]){const label=element('label',text);label.append(input);fields.append(label);}
    const generate=element('button','Generar propuesta','primary-button');generate.type='submit';form.append(fields,generate);
    const preview=element('div');let staff=[],draft=null,busy=false;
    section.append(actionButton('Generar horario con IA',()=>{form.hidden=!form.hidden;},true),element('p','La propuesta no cambia el horario. Revísala y edítala antes de publicar. Los turnos existentes se conservan.'),form,feedback,preview);
    const showDraft=d=>{
        draft=d;preview.replaceChildren();
        preview.append(element('h3','Vista previa editable · '+d.startDate));
        (d.proposal.warnings||[]).forEach(w=>preview.append(element('p',w,'ai-warning')));
        const wrapper=element('div',undefined,'table-container'),table=element('table'),thead=element('thead'),head=element('tr');
        ['Trabajador','Fecha','Inicio','Término','Acciones'].forEach(t=>head.append(element('th',t)));thead.append(head);table.append(thead);
        const body=element('tbody');table.append(body);wrapper.append(table);preview.append(wrapper);
        const addRow=s=>{
            const tr=element('tr'),employee=element('select'),date=element('input'),from=element('input'),to=element('input');
            employee.setAttribute('aria-label','Trabajador');staff.forEach(e=>employee.add(new Option(e.name+' · '+e.area,e.id)));employee.value=String(s.employeeId);
            date.type='date';date.value=s.date;date.setAttribute('aria-label','Fecha');from.type=to.type='time';from.value=s.start;to.value=s.end;from.setAttribute('aria-label','Inicio');to.setAttribute('aria-label','Término');
            [employee,date,from,to].forEach(input=>{const td=element('td');td.append(input);tr.append(td);});
            const actions=element('td');actions.append(actionButton('Quitar',()=>tr.remove()));tr.append(actions);body.append(tr);
        };
        d.proposal.shifts.forEach(addRow);
        preview.append(actionButton('Agregar turno',()=>addRow({employeeId:staff[0]?.id,date:d.startDate,start:'09:00',end:'17:00'})));
        const publish=actionButton('Publicar horario',async()=>{
            if(busy || !isOwnerRole())return;
            const shifts=Array.from(body.rows).map(row=>{const [employee,date,from,to]=row.querySelectorAll('select,input');return{employeeId:Number(employee.value),date:date.value,start:from.value,end:to.value};});
            if(!shifts.length || shifts.some(s=>!s.employeeId||!s.date||!s.start||!s.end||s.end<=s.start)){feedback.textContent='Revisa trabajadores, fechas y horas de la propuesta.';return;}
            if(!confirm('¿Publicar estos turnos y notificar a los trabajadores afectados?'))return;
            busy=true;publish.disabled=true;generate.disabled=true;
            try{await apiRequest(`/api/horarios/ia/propuestas/${draft.id}/publicar`,{method:'POST',body:JSON.stringify({shifts,warnings:d.proposal.warnings||[]})});feedback.textContent='Horario publicado. Se notificó a los trabajadores afectados.';preview.replaceChildren();await renderModule('Horarios');await loadUserNotifications();}
            catch(error){feedback.textContent=error.message;}
            finally{busy=false;publish.disabled=false;generate.disabled=false;}
        },true);preview.append(publish);
    };
    form.addEventListener('submit',async event=>{
        event.preventDefault();if(busy||!isOwnerRole()||!form.reportValidity())return;
        if(!instructions.value.trim()){feedback.textContent='Escribe las instrucciones.';return;}
        busy=true;generate.disabled=true;feedback.textContent='Generando propuesta… Puede tardar hasta 90 segundos.';
        try{const d=await apiRequest('/api/horarios/ia/propuestas',{method:'POST',body:JSON.stringify({startDate:start.value,instructions:instructions.value.trim()})});showDraft(d);feedback.textContent='Propuesta guardada. Revisa todos los turnos antes de publicar.';}
        catch(error){feedback.textContent=error.message;}
        finally{busy=false;generate.disabled=false;}
    });
    void(async()=>{
        try{
            staff=(await apiRequest(employeesApi)).filter(e=>e.active&&AREA_OPTIONS.some(a=>a.key===normalizeArea(e.area)));
            const config=await apiRequest('/api/horarios/ia/config');
            if(!config.configured){feedback.textContent='La IA está pendiente de configuración: OPENAI_API_KEY y OPENAI_MODEL en el servidor. Puedes seguir usando horarios e imágenes.';generate.disabled=true;}
            const drafts=await apiRequest('/api/horarios/ia/propuestas');
            if(drafts.length){const picker=element('select');picker.setAttribute('aria-label','Borradores guardados');picker.add(new Option('Recuperar propuesta guardada',''));drafts.forEach((d,i)=>picker.add(new Option(d.startDate+' · '+d.id.slice(0,8),String(i))));picker.addEventListener('change',()=>{if(picker.value!=='')showDraft(drafts[Number(picker.value)]);});section.insertBefore(picker,preview);}
        }catch(error){feedback.textContent=error.message;generate.disabled=true;}
    })();
}
