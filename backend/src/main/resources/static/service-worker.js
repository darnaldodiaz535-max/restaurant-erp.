const CACHE_NAME='marigex-shell-inventario-enviar-20261006-1';
const DEVICE_CACHE='marigex-device';
const SHELL=['/','/index.html','/style.css','/scrip.js','/gestion.js','/notificaciones-horarios.js','/experiencia.js','/manifest.json','/icons/icon-192.png','/icons/icon-512.png'];
self.addEventListener('install',event=>{
    event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(SHELL)));
    self.skipWaiting();
});
self.addEventListener('activate',event=>{
    event.waitUntil((async()=>{
        for(const key of await caches.keys())if((key.startsWith('marigex-shell-')||key.startsWith('restaurant-erp-'))&&key!==CACHE_NAME)await caches.delete(key);
        await self.clients.claim();
    })());
});
self.addEventListener('fetch',event=>{
    const url=new URL(event.request.url);
    // Never cache APIs, sessions, private photos or writes.
    if(event.request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;
    if(!SHELL.includes(url.pathname)&&event.request.mode!=='navigate')return;
    event.respondWith((async()=>{
        const cache=await caches.open(CACHE_NAME);
        try{
            const response=await fetch(event.request);
            if(response.ok && !response.redirected && SHELL.includes(url.pathname))await cache.put(url.pathname,response.clone());
            return response;
        }catch{
            return await cache.match(event.request.mode==='navigate'?'/index.html':url.pathname)
                ||new Response('Sin conexión. Vuelve a intentarlo cuando tengas internet.',{status:503});
        }
    })());
});
self.addEventListener('message',event=>{
    if(event.data?.type!=='PUSH_OWNER')return;
    event.waitUntil((async()=>{
        const cache=await caches.open(DEVICE_CACHE);
        if(event.data.userId)await cache.put('/__push-owner',new Response(String(event.data.userId)));
        else await cache.delete('/__push-owner');
        event.ports[0]?.postMessage('ok');
    })());
});
self.addEventListener('push',event=>{
    event.waitUntil((async()=>{
        let data;try{data=event.data?.json();}catch{return;}
        if(!data)return;
        const owner=await (await caches.open(DEVICE_CACHE)).match('/__push-owner');
        if(!owner || await owner.text()!==String(data.userId))return;
        await self.registration.showNotification(data.title||'MariGex',{
            body:data.body||'Tienes un aviso nuevo.',icon:'/icons/icon-192.png',
            tag:data.tag||'marigex',data:{url:'/',userId:String(data.userId)},renotify:false
        });
    })());
});
self.addEventListener('notificationclick',event=>{
    event.notification.close();
    event.waitUntil((async()=>{
        const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
        const existing=windows.find(w=>new URL(w.url).origin===self.location.origin);
        if(existing){await existing.focus();existing.postMessage({type:'OPEN_NOTIFICATIONS'});}
        else await self.clients.openWindow('/?avisos=1');
    })());
});
