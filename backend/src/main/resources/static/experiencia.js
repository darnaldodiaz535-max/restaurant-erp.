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
function pushOwner(registration, userId) {
    return new Promise((resolve, reject) => {
        const worker = registration.active;

        if (!worker) {
            reject(new Error(
                'El dispositivo todavía no está listo. Recarga MariGex.'
            ));
            return;
        }

        const channel = new MessageChannel();
        let settled = false;
        let timer;

        const finish = error => {
            if (settled) return;
            settled = true;

            clearTimeout(timer);
            channel.port1.close();
            channel.port2.close();

            if (error) reject(error);
            else resolve();
        };

        timer = setTimeout(() => {
            finish(new Error(
                'No se pudo confirmar la configuración del dispositivo. Vuelve a intentarlo.'
            ));
        }, 5000);

        channel.port1.onmessage = event => {
            if (event.data === 'ok') finish();
        };

        channel.port1.onmessageerror = () => {
            finish(new Error(
                'No se pudo confirmar la configuración del dispositivo.'
            ));
        };

        try {
            worker.postMessage(
                { type: 'PUSH_OWNER', userId },
                [channel.port2]
            );
        } catch {
            finish(new Error(
                'No se pudo comunicar con el dispositivo. Recarga MariGex.'
            ));
        }
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

    const problems = [];
    let registration = null;
    let subscription = null;

    try {
        if ('serviceWorker' in navigator) {
            try {
                registration =
                    await navigator.serviceWorker.getRegistration('/');
            } catch {
                problems.push(
                    'No se pudo consultar la configuración del dispositivo.'
                );
            }
        }

        if (registration) {
            try {
                await pushOwner(registration, null);
            } catch {
                problems.push(
                    'No se pudo confirmar la desvinculación de la cuenta.'
                );
            }

            try {
                subscription =
                    await registration.pushManager?.getSubscription();
            } catch {
                problems.push(
                    'No se pudo consultar la suscripción del navegador.'
                );
            }

            if (subscription) {
                try {
                    await apiRequest(
                        '/api/notificaciones/push/suscripciones',
                        {
                            method: 'DELETE',
                            body: JSON.stringify({
                                endpoint: subscription.endpoint
                            })
                        }
                    );
                } catch {
                    problems.push(
                        'No se pudo confirmar la eliminación del registro en el servidor.'
                    );
                }

                try {
                    const removed = await subscription.unsubscribe();

                    if (!removed) {
                        const remaining =
                            await registration.pushManager.getSubscription();

                        if (remaining) {
                            problems.push(
                                'El navegador no confirmó la cancelación de Push.'
                            );
                        }
                    }
                } catch {
                    problems.push(
                        'No se pudo cancelar la suscripción en el navegador.'
                    );
                }
            }

            try {
                const notices = await registration.getNotifications();
                for (const notice of notices) notice.close();
            } catch {
                problems.push(
                    'No se pudieron cerrar los avisos que estaban visibles.'
                );
            }
        }
    } finally {
        try {
            localStorage.removeItem('marigex-push-user');
        } catch {
            problems.push(
                'No se pudo limpiar la vinculación local del dispositivo.'
            );
        }
    }

    if (problems.length) {
        throw new Error(
            'La desactivación no se pudo confirmar por completo. ' +
            problems.join(' ') +
            ' Comprueba la conexión y vuelve a intentarlo.'
        );
    }
}
async function renderNotificationPreferences(section) {
    const box = element(
        'section',
        undefined,
        'data-section notification-preferences'
    );

    section.before(box);

    box.append(
        element('h3', 'Mis notificaciones'),
        element(
            'p',
            'Estas preferencias solo se aplican a tu cuenta. Los avisos anteriores se conservan.'
        )
    );

    const feedback = element('p', 'Cargando preferencias…');
    feedback.setAttribute('role', 'status');
    feedback.setAttribute('aria-live', 'polite');
    feedback.setAttribute('aria-atomic', 'true');
    feedback.tabIndex = -1;
    box.append(feedback);

    const showMessage = (message, error = false, focus = false) => {
        feedback.textContent = message;
        feedback.classList.toggle('auth-error', error);
        feedback.style.fontWeight = '600';

        if (focus && box.isConnected) {
            feedback.focus({ preventScroll: true });
            feedback.scrollIntoView({
                block: 'nearest',
                behavior: 'smooth'
            });
        }
    };

    try {
        const initial = await apiRequest(
            '/api/notificaciones/preferencias'
        );

        let config = {
            configured: false,
            publicKey: '',
            mailConfigured: false
        };

        let configurationUnavailable = false;

        try {
            config = await apiRequest(
                '/api/notificaciones/push/config'
            );
        } catch {
            configurationUnavailable = true;
        }

        const form = element('form', undefined, 'auth-form');
        form.noValidate = true;

        const internal = element('input');
        const push = element('input');
        const mail = element('input');
        const email = element('input');

        for (const [text, input] of [
            ['Dentro de MariGex', internal],
            ['Notificaciones push', push],
            ['Por correo electrónico', mail]
        ]) {
            input.type = 'checkbox';

            const label = element(
                'label',
                text,
                'preference-toggle'
            );

            label.prepend(input);
            form.append(label);
        }

        email.type = 'email';
        email.inputMode = 'email';
        email.autocomplete = 'email';
        email.maxLength = 254;

        const emailLabel = element('label', 'Mi correo para avisos');
        emailLabel.append(email);
        form.append(emailLabel);

        form.append(element(
            'small',
            'El correo es obligatorio si activas los avisos por correo. Si escribes una dirección, debe ser válida.'
        ));

        const save = element(
            'button',
            'Guardar preferencias',
            'primary-button'
        );

        save.type = 'submit';
        form.append(save);

        box.append(form, feedback);

        const applyPreferences = preferences => {
            internal.checked = Boolean(preferences.internal);
            push.checked = Boolean(preferences.push);
            mail.checked = Boolean(preferences.mail);
            email.value = preferences.email || '';
        };

        applyPreferences(initial);

        const userId = String(currentProfile?.id ?? '');
        let busy = false;
        let enable;
        let disable;

        const assertAccount = () => {
            if (!currentProfile ||
                String(currentProfile.id) !== userId) {
                throw new Error(
                    'La sesión ha cambiado. Abre de nuevo Mis notificaciones.'
                );
            }
        };

        const setBusy = (value, saving = false) => {
            busy = value;
            form.setAttribute('aria-busy', String(value));

            for (const control of [
                internal, push, mail, email, save
            ]) {
                control.disabled = value;
            }

            save.textContent =
                value && saving ? 'Guardando…' : 'Guardar preferencias';

            if (enable) {
                enable.disabled = value || !config.configured;
            }

            if (disable) {
                disable.disabled = value;
            }
        };

        const readPreferences = () => {
            const address = email.value.trim();

            const emailPattern =
                /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$/;

            if (mail.checked && !address) {
                throw new Error(
                    'Escribe tu correo para activar los avisos por correo.'
                );
            }

            if (address.length > 254 ||
                (address && !emailPattern.test(address))) {
                throw new Error(
                    'Revisa la dirección de correo. Si no deseas guardarla, deja el campo vacío y desmarca los avisos por correo.'
                );
            }

            return {
                internal: internal.checked,
                push: push.checked,
                mail: mail.checked,
                email: address
            };
        };

        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (busy) return;

            try {
                assertAccount();
                const values = readPreferences();

                setBusy(true, true);
                showMessage('Guardando…');

                const saved = await apiRequest(
                    '/api/notificaciones/preferencias',
                    {
                        method: 'PUT',
                        body: JSON.stringify(values)
                    }
                );

                assertAccount();
                applyPreferences(saved);

                showMessage(
                    'Preferencias guardadas',
                    false,
                    true
                );
            } catch (error) {
                showMessage(
                    error.message || 'No se pudieron guardar las preferencias.',
                    true,
                    true
                );
            } finally {
                setBusy(false);
            }
        });

        enable = actionButton(
            'Activar push en este dispositivo',
            async () => {
                if (busy) return;

                let subscription = null;
                let createdSubscription = false;
                let activated = false;
                let operationVersion = null;

                try {
                    assertAccount();

                    if (!config.configured) {
                        throw new Error(
                            'Las notificaciones del dispositivo no están disponibles todavía.'
                        );
                    }

                    if (!window.isSecureContext ||
                        !('serviceWorker' in navigator) ||
                        !('PushManager' in window) ||
                        !('Notification' in window)) {
                        throw new Error(
                            'Este navegador no permite activar las notificaciones aquí. En iPhone o iPad, añade MariGex a la pantalla de inicio y ábrela desde su icono.'
                        );
                    }

                    const raw = String(config.publicKey || '')
                        .replace(/-/g, '+')
                        .replace(/_/g, '/');

                    let key;

                    try {
                        key = Uint8Array.from(
                            atob(raw + '='.repeat((4 - raw.length % 4) % 4)),
                            character => character.charCodeAt(0)
                        );
                    } catch {
                        throw new Error(
                            'Las notificaciones del dispositivo no están disponibles. Contacta con la administración.'
                        );
                    }

                    if (key.length !== 65 || key[0] !== 4) {
                        throw new Error(
                            'Las notificaciones del dispositivo no están disponibles. Contacta con la administración.'
                        );
                    }

                    operationVersion = ++pushBindingVersion;

                    const assertOperation = () => {
                        assertAccount();

                        if (operationVersion !== pushBindingVersion) {
                            throw new Error(
                                'La operación fue cancelada porque cambió la vinculación del dispositivo.'
                            );
                        }
                    };

                    setBusy(true);
                    showMessage(
                        'Activando notificaciones del dispositivo…'
                    );

                    const permission =
                        await Notification.requestPermission();

                    assertOperation();

                    if (permission !== 'granted') {
                        throw new Error(
                            'No se concedió permiso. Puedes permitir las notificaciones desde los ajustes del navegador.'
                        );
                    }

                    const registration = await pushRegistration();
                    assertOperation();

                    subscription =
                        await registration.pushManager.getSubscription();

                    assertOperation();

                    if (subscription) {
                        const existingKey =
                            subscription.options.applicationServerKey;

                        if (!existingKey) {
                            throw new Error(
                                'Desactiva Push en este dispositivo y vuelve a activarlo para actualizar su configuración.'
                            );
                        }

                        const existing = new Uint8Array(existingKey);

                        if (existing.length !== key.length ||
                            !key.every(
                                (value, index) => value === existing[index]
                            )) {
                            throw new Error(
                                'Desactiva Push en este dispositivo y vuelve a activarlo para actualizar su configuración.'
                            );
                        }
                    } else {
                        subscription =
                            await registration.pushManager.subscribe({
                                userVisibleOnly: true,
                                applicationServerKey: key
                            });

                        createdSubscription = true;
                    }

                    assertOperation();

                    await pushOwner(registration, userId);
                    assertOperation();

                    localStorage.setItem(
                        'marigex-push-user',
                        userId
                    );

                    await apiRequest(
                        '/api/notificaciones/push/activar',
                        {
                            method: 'POST',
                            body: JSON.stringify(subscription)
                        }
                    );

                    assertOperation();
                    activated = true;

                    push.checked = true;

                    showMessage(
                        'Push activado en este dispositivo. Los demás cambios del formulario se guardan con “Guardar preferencias”.',
                        false,
                        true
                    );
                } catch (error) {
                    let message = error.message ||
                        'No se pudo activar Push en este dispositivo.';

                    const sameAccount =
                        currentProfile &&
                        String(currentProfile.id) === userId;

                    if (createdSubscription &&
                        !activated &&
                        sameAccount &&
                        operationVersion === pushBindingVersion) {
                        try {
                            await disconnectPush();
                        } catch (cleanupError) {
                            message += ' ' + cleanupError.message;
                        }
                    }

                    showMessage(message, true, true);
                } finally {
                    setBusy(false);
                }
            }
        );

        disable = actionButton(
            'Desactivar push en este dispositivo',
            async () => {
                if (busy) return;

                try {
                    assertAccount();
                    setBusy(true);

                    showMessage(
                        'Desactivando notificaciones del dispositivo…'
                    );

                    await disconnectPush();

                    showMessage(
                        'Push desactivado en este dispositivo. La preferencia de tu cuenta y los demás dispositivos no cambian.',
                        false,
                        true
                    );
                } catch (error) {
                    showMessage(
                        error.message ||
                            'No se pudo confirmar la desactivación.',
                        true,
                        true
                    );
                } finally {
                    setBusy(false);
                }
            }
        );

        box.append(
            element(
                'p',
                'La casilla Push controla los avisos de tu cuenta. Para recibirlos en este equipo, activa también este dispositivo.'
            ),
            enable,
            disable
        );

        if (configurationUnavailable) {
            box.append(element(
                'p',
                'No se pudo comprobar la disponibilidad de los avisos externos. Puedes guardar tus preferencias y volver a abrir esta pantalla para intentarlo de nuevo.'
            ));
        } else {
            if (!config.configured) {
                box.append(element(
                    'p',
                    'Las notificaciones del dispositivo no están disponibles todavía.'
                ));
            }

            if (!config.mailConfigured) {
                box.append(element(
                    'p',
                    'Los avisos por correo no están disponibles todavía. Puedes guardar tu preferencia para cuando se habiliten.'
                ));
            }
        }

        const isAppleMobile =
            /iPad|iPhone|iPod/.test(navigator.userAgent) ||
            (navigator.platform === 'MacIntel' &&
                navigator.maxTouchPoints > 1);

        const standalone =
            window.matchMedia('(display-mode: standalone)').matches ||
            navigator.standalone === true;

        if (isAppleMobile && !standalone) {
            box.append(element(
                'p',
                'Para recibir notificaciones en este iPhone o iPad, añade MariGex a la pantalla de inicio y ábrela desde su icono.'
            ));
        }

        setBusy(false);
        showMessage('Preferencias cargadas.');
    } catch (error) {
        showMessage(
            error.message || 'No se pudieron cargar las preferencias.',
            true
        );
    }
}

/* PARA QUÉ SIRVE:
   Mantiene el panel de notificaciones dentro de la pantalla del teléfono.
   ARCHIVO: src/main/resources/static/experiencia.js
   SECCIÓN: positionNotifications()
*/

function positionNotifications() {
    if (!notificationMenu.classList.contains('show')) return;

    const viewport = window.visualViewport;
    const margin = 8;

    const viewportLeft = viewport?.offsetLeft ?? 0;
    const viewportTop = viewport?.offsetTop ?? 0;

    const viewportWidth = Math.min(
        viewport?.width ?? window.innerWidth,
        document.documentElement.clientWidth
    );
 
    const viewportHeight = viewport?.height ?? window.innerHeight;

    const availableWidth = Math.max(0, viewportWidth - margin * 2);
    const availableHeight = Math.max(0, viewportHeight - margin * 2);

    const panelWidth = Math.min(360, availableWidth);
    const anchor = notificationBtn.getBoundingClientRect();

    const minLeft = viewportLeft + margin;
    const maxLeft = minLeft + availableWidth - panelWidth;

    const left = Math.max(
        minLeft,
        Math.min(anchor.right - panelWidth, maxLeft)
    );

    const minTop = viewportTop + margin;
    const viewportBottom = viewportTop + viewportHeight - margin;

    const preferredHeight = Math.min(220, availableHeight);
    const maxTop = Math.max(minTop, viewportBottom - preferredHeight);

    const top = Math.max(
        minTop,
        Math.min(anchor.bottom + margin, maxTop)
    );

    Object.assign(notificationMenu.style, {
        left: `${left}px`,
        top: `${top}px`,
        right: 'auto',
        bottom: 'auto',
        width: `${panelWidth}px`,
        maxWidth: `${availableWidth}px`,
        maxHeight: `${Math.max(0, viewportBottom - top)}px`
    });
}

notificationBtn.addEventListener('click', positionNotifications);

window.addEventListener('resize', positionNotifications);
window.addEventListener('scroll', positionNotifications, {
    passive: true
});

window.visualViewport?.addEventListener(
    'resize',
    positionNotifications
);

window.visualViewport?.addEventListener(
    'scroll',
    positionNotifications,
    { passive: true }
);
document.addEventListener('keydown',e=>{if(e.key==='Escape'){notificationMenu.classList.remove('show');notificationBtn.setAttribute('aria-expanded','false');}});
function openPushInbox() {
    if(!currentProfile)return;
    notificationMenu.classList.add('show');notificationBtn.setAttribute('aria-expanded','true');positionNotifications();void loadUserNotifications();
}
navigator.serviceWorker?.addEventListener('message',event=>{if(event.data?.type==='OPEN_NOTIFICATIONS')openPushInbox();});
