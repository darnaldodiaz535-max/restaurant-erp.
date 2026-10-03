// MariGex: keeps authentication, task endpoints and the personal inbox already in use.
async function startMarigex() {
    const started=performance.now();
    try { await initializeAuthentication(); }
    finally {
        await new Promise(resolve=>setTimeout(resolve,Math.max(0,1700-(performance.now()-started))));
        document.body.classList.remove('booting');
        const splash=document.getElementById('marigexSplash');
        splash.classList.add('splash-exit');setTimeout(()=>splash.remove(),250);
        if(currentProfile && new URLSearchParams(location.search).has('avisos'))openPushInbox();
    }
}

async function openTaskCamera(taskId,title,onSaved) {
    const dialog=element('dialog',undefined,'profile-dialog evidence-camera');
    dialog.setAttribute('aria-label','Realizar tarea: '+title);
    const video=element('video');video.autoplay=true;video.muted=true;video.playsInline=true;video.hidden=true;
    const preview=element('img');preview.alt='Vista previa de la evidencia';preview.hidden=true;
    const feedback=element('p','Toma una foto o elige una imagen JPG, PNG o WebP. Máximo 4 MB.');feedback.setAttribute('role','status');
    const input=element('input');input.type='file';input.accept='image/jpeg,image/png,image/webp';input.setAttribute('capture','environment');input.hidden=true;
    let stream=null,file=null,url=null,busy=false,cameraVersion=0;
    const stop=()=>{stream?.getTracks().forEach(t=>t.stop());stream=null;video.srcObject=null;video.hidden=true;};
    const choose=async selected=>{
        if(!selected)return;
        if(!['image/jpeg','image/png','image/webp'].includes(selected.type)||selected.size>4*1024*1024) {feedback.textContent='Selecciona una imagen JPG, PNG o WebP de hasta 4 MB.';return;}
        ++cameraVersion;stop();if(url)URL.revokeObjectURL(url);file=selected;url=URL.createObjectURL(file);preview.src=url;preview.hidden=false;send.disabled=false;capture.disabled=true;
        feedback.textContent='Revisa la foto antes de enviarla. La tarea quedará pendiente de revisión.';
    };
    const camera=actionButton('Abrir cámara',async()=>{
        if(!navigator.mediaDevices?.getUserMedia){input.click();return;}
        const version=++cameraVersion;camera.disabled=true;feedback.textContent='Solicitando acceso a la cámara…';
        try{
            stop();const fresh=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1600}},audio:false});
            if(!dialog.open || version!==cameraVersion){fresh.getTracks().forEach(t=>t.stop());return;}
            stream=fresh;video.srcObject=stream;video.hidden=false;preview.hidden=true;await video.play();capture.disabled=false;
            feedback.textContent='Encuadra la evidencia. Se solicita la cámara trasera; la elección final depende del dispositivo.';
        }catch(error){feedback.textContent='No se pudo abrir la cámara. Puedes usar “Tomar o elegir foto”.';}
        finally{camera.disabled=false;}
    });
    const capture=actionButton('Tomar foto',()=>{
        if(!video.videoWidth)return;
        const canvas=element('canvas'),scale=Math.min(1,1600/video.videoWidth);
        canvas.width=Math.round(video.videoWidth*scale);canvas.height=Math.round(video.videoHeight*scale);
        canvas.getContext('2d').drawImage(video,0,0,canvas.width,canvas.height);
        canvas.toBlob(blob=>{if(blob&&dialog.open)void choose(new File([blob],'evidencia.jpg',{type:'image/jpeg'}));},'image/jpeg',.85);
    });capture.disabled=true;
    const picker=actionButton('Tomar o elegir foto',()=>input.click());input.addEventListener('change',()=>void choose(input.files?.[0]));
    const send=actionButton('Enviar evidencia',async()=>{
        if(busy||!file)return;busy=true;send.disabled=camera.disabled=capture.disabled=picker.disabled=cancel.disabled=true;
        feedback.textContent='Guardando evidencia…';
        try{const data=new FormData();data.append('taskId',String(taskId));data.append('photo',file);await uploadRequest('/api/evidencias',data);dialog.close();await onSaved();await loadUserNotifications();}
        catch(error){feedback.textContent=error.message;}
        finally{busy=false;send.disabled=!file;camera.disabled=picker.disabled=cancel.disabled=false;}
    },true);send.disabled=true;
    const cancel=actionButton('Cancelar',()=>dialog.close());
    const actions=element('div',undefined,'module-controls');actions.append(camera,capture,picker,send,cancel);
    const hidden=()=>{if(document.hidden)stop();};document.addEventListener('visibilitychange',hidden);
    dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();});
    dialog.addEventListener('close',()=>{stop();if(url)URL.revokeObjectURL(url);document.removeEventListener('visibilitychange',hidden);dialog.remove();});
    dialog.append(element('h2','Realizar tarea'),element('p',title),video,preview,input,feedback,actions);
    document.body.append(dialog);dialog.showModal();
}

async function pushRegistration() {
    if(!('serviceWorker' in navigator))throw new Error('Este navegador no admite PWA.');
    await navigator.serviceWorker.register('/service-worker.js',{updateViaCache:'none'});
    return Promise.race([navigator.serviceWorker.ready,new Promise((_,reject)=>setTimeout(()=>reject(new Error('La PWA todavía no está lista. Recarga la página.')),10000))]);
}
function pushOwner(registration,userId) {
    return new Promise(resolve=>{
        const channel=new MessageChannel();channel.port1.onmessage=()=>resolve();
        registration.active?.postMessage({type:'PUSH_OWNER',userId},[channel.port2]);setTimeout(resolve,1500);
    });
}
let pushBindingVersion=0;
async function bindPushAccount() {
    const version=++pushBindingVersion;
    if(!('serviceWorker' in navigator)||!currentProfile)return;
    const user=String(currentProfile.id);
    try{
        const registration=await pushRegistration();
        if(version!==pushBindingVersion || String(currentProfile?.id)!==user)return;
        const previous=localStorage.getItem('marigex-push-user');
        if(previous && previous!==user) { const old=await registration.pushManager?.getSubscription();if(old)await old.unsubscribe(); }
        if(version!==pushBindingVersion || String(currentProfile?.id)!==user)return;
        localStorage.setItem('marigex-push-user',user);await pushOwner(registration,user);
        const subscription=await registration.pushManager?.getSubscription();
        if(subscription && currentProfile && String(currentProfile.id)===user)await apiRequest('/api/notificaciones/push/suscripciones',{method:'POST',body:JSON.stringify(subscription)});
    }catch(error){console.info('Push pendiente:',error.message);}
}
async function disconnectPush() {
    ++pushBindingVersion;
    if(!('serviceWorker' in navigator))return;
    const registration=await navigator.serviceWorker.getRegistration('/');
    if(registration){
        await pushOwner(registration,null);
        const subscription=await registration.pushManager?.getSubscription();
        if(subscription){
            try{await apiRequest('/api/notificaciones/push/suscripciones',{method:'DELETE',body:JSON.stringify({endpoint:subscription.endpoint})});}
            finally{await subscription.unsubscribe();}
        }
        for(const notice of await registration.getNotifications())notice.close();
    }
    localStorage.removeItem('marigex-push-user');
}
async function renderNotificationPreferences(section) {
    const box=element('section',undefined,'data-section notification-preferences');section.before(box);
    box.append(element('h3','Mis notificaciones'),element('p','Estas preferencias solo se aplican a tu cuenta. Los avisos anteriores se conservan.'));
    const feedback=element('p');feedback.setAttribute('role','status');box.append(feedback);
    try{
        const [p,config]=await Promise.all([apiRequest('/api/notificaciones/preferencias'),apiRequest('/api/notificaciones/push/config')]);
        const form=element('form',undefined,'auth-form'),internal=element('input'),push=element('input'),mail=element('input'),email=element('input');
        for(const [text,input,checked] of [['Dentro de MariGex',internal,p.internal],['Notificaciones push',push,p.push],['Por correo electrónico',mail,p.mail]]){
            input.type='checkbox';input.checked=checked;const label=element('label',text,'preference-toggle');label.prepend(input);form.append(label);
        }
        email.type='email';email.maxLength=254;email.value=p.email||'';const emailLabel=element('label','Mi correo para avisos');emailLabel.append(email);form.append(emailLabel);
        const save=element('button','Guardar preferencias','primary-button');save.type='submit';form.append(save);
        form.addEventListener('submit',async e=>{
            e.preventDefault();if(mail.checked&&!email.value.trim()){feedback.textContent='Escribe tu correo.';return;}
            save.disabled=true;
            try{await apiRequest('/api/notificaciones/preferencias',{method:'PUT',body:JSON.stringify({internal:internal.checked,push:push.checked,mail:mail.checked,email:email.value.trim()})});feedback.textContent='Preferencias guardadas.';}
            catch(error){feedback.textContent=error.message;}finally{save.disabled=false;}
        });
        const enable=actionButton('Activar push en este dispositivo',async()=>{
            enable.disabled=true;
            try{
                if(!('PushManager' in window)||!('Notification' in window))throw new Error('Push no está disponible. En iPhone/iPad instala MariGex en la pantalla de inicio y ábrela desde allí (iOS 16.4 o posterior).');
                // Permission request occurs directly from this click, before awaiting network.
                const permission=await Notification.requestPermission();if(permission!=='granted')throw new Error('Permiso no concedido. Puedes cambiarlo en los ajustes del navegador.');
                const reg=await pushRegistration();let s=await reg.pushManager.getSubscription();
                const raw=config.publicKey.replace(/-/g,'+').replace(/_/g,'/');const bytes=Uint8Array.from(atob(raw+'='.repeat((4-raw.length%4)%4)),c=>c.charCodeAt(0));
                if(s && s.options.applicationServerKey && !bytes.every((v,i)=>v===new Uint8Array(s.options.applicationServerKey)[i])){await s.unsubscribe();s=null;}
                s=s||await reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:bytes});
                await apiRequest('/api/notificaciones/push/suscripciones',{method:'POST',body:JSON.stringify(s)});
                push.checked=true;await apiRequest('/api/notificaciones/preferencias',{method:'PUT',body:JSON.stringify({internal:internal.checked,push:true,mail:mail.checked,email:email.value.trim()})});
                await pushOwner(reg,String(currentProfile.id));localStorage.setItem('marigex-push-user',String(currentProfile.id));feedback.textContent='Push activado en este dispositivo.';
            }catch(error){feedback.textContent=error.message;}finally{enable.disabled=!config.configured;}
        });enable.disabled=!config.configured;
        const disable=actionButton('Desactivar push en este dispositivo',async()=>{try{await disconnectPush();feedback.textContent='Dispositivo desconectado. Los demás dispositivos conservan su configuración.';}catch(error){feedback.textContent=error.message;}});
        box.append(form,enable,disable,element('p','En iPhone/iPad, añade MariGex a la pantalla de inicio y ábrela desde su icono. Push y cámara requieren HTTPS.'));
        if(!config.configured)box.append(element('small','El administrador debe configurar las claves VAPID en el servidor para activar Push.'));
        if(!config.mailConfigured)box.append(element('small','El envío por correo está pendiente de configurar SMTP.'));
    }catch(error){feedback.textContent=error.message;}
}

// Portal outside the topbar: a parent's overflow/stacking context cannot clip the inbox.
document.body.append(notificationMenu);
function positionNotifications() {
    if(!notificationMenu.classList.contains('show'))return;
    const view=window.visualViewport, width=view?.width||innerWidth,height=view?.height||innerHeight,offsetX=view?.offsetLeft||0,offsetY=view?.offsetTop||0;
    const anchor=notificationBtn.getBoundingClientRect(),panelWidth=Math.min(360,width-16);
    notificationMenu.style.width=panelWidth+'px';
    notificationMenu.style.left=(offsetX+Math.max(8,Math.min(anchor.right-panelWidth,width-panelWidth-8)))+'px';
    const top=Math.max(8,Math.min(anchor.bottom+8,Math.max(8,height-220)));
    notificationMenu.style.top=(offsetY+top)+'px';notificationMenu.style.maxHeight=Math.max(80,height-top-8)+'px';
}
notificationBtn.addEventListener('click',positionNotifications);
window.addEventListener('resize',positionNotifications);window.addEventListener('scroll',positionNotifications,{passive:true});
window.visualViewport?.addEventListener('resize',positionNotifications);
document.addEventListener('keydown',e=>{if(e.key==='Escape'){notificationMenu.classList.remove('show');notificationBtn.setAttribute('aria-expanded','false');}});
function openPushInbox() {
    if(!currentProfile)return;
    notificationMenu.classList.add('show');notificationBtn.setAttribute('aria-expanded','true');positionNotifications();void loadUserNotifications();
}
navigator.serviceWorker?.addEventListener('message',event=>{if(event.data?.type==='OPEN_NOTIFICATIONS')openPushInbox();});
