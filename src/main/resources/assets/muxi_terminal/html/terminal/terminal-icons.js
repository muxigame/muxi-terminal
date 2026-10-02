/* Shared terminal icons. Built-in apps declare item/resource/semantic descriptors only. */
(function(global){
  'use strict';
  const MAX_ENTRIES=96,MAX_BYTES=4*1024*1024,MAX_QUEUE=64;
  const cache=new Map(),pending=new Map(),attached=new Map(),queries=new Map(),scopes=new WeakMap(),queue=[];
  let bytes=0,active=0,revision=-1,generation=0,disposed=false;
  const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function descriptor(value){
    if(!value || !['item','resource','semantic'].includes(value.kind))throw new Error('Invalid icon kind');
    const id=String(value.id||'');
    if(value.kind==='semantic'){if(id!=='experience-levels')throw new Error('Unknown semantic icon');}
    else if(!/^[a-z0-9_.-]+:[a-z0-9_][a-z0-9_./-]*$/.test(id)||id.length>256)throw new Error('Invalid icon id');
    return {kind:value.kind,id};
  }
  function invoke(command){
    if(disposed||typeof global.muxiTerminalQuery!=='function')return Promise.reject(new Error('Native icons unavailable'));
    return new Promise((resolve,reject)=>{
      let id,settled=false;
      const finish=(ok,value)=>{
        if(settled)return;settled=true;clearTimeout(timeout);queries.delete(id);
        ok?resolve(value):reject(value);
      };
      const timeout=setTimeout(()=>{try{global.muxiTerminalCancel?.(id);}catch{}finish(false,new Error('Icon request timed out'));},8000);
      try{
        id=global.muxiTerminalQuery({request:command,persistent:false,
          onSuccess:response=>{try{finish(true,JSON.parse(response));}catch{finish(false,new Error('Invalid icon response'));}},
          onFailure:(code,message)=>finish(false,new Error(String(code)+': '+message))});
        if(!settled)queries.set(id,()=>{try{global.muxiTerminalCancel?.(id);}catch{}finish(false,new Error('Icon service disposed'));});
      }catch(error){finish(false,error);}
    });
  }
  function trim(){
    while(cache.size>MAX_ENTRIES||bytes>MAX_BYTES){const key=cache.keys().next().value;bytes-=cache.get(key).size;cache.delete(key);}
  }
  function remember(key,src){
    const size=src?src.length*2:0;if(size>MAX_BYTES)return;
    if(cache.has(key)){bytes-=cache.get(key).size;cache.delete(key);}
    cache.set(key,{src,size,expires:src?Infinity:Date.now()+5000});bytes+=size;trim();
  }
  function drain(){
    while(!disposed&&active<4&&queue.length){
      const job=queue.shift();active++;
      invoke('icons.get:'+JSON.stringify(job.desc)).then(result=>{
        if(disposed||job.generation!==generation)return null;
        if(!result || !/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(result.src||'') || result.src.length>350000)return null;
        if(revision!==result.revision){invalidate(result.revision);return null;}
        return result.src;
      }).catch(()=>null).then(src=>{
        if(!disposed&&job.generation===generation)remember(job.key,src);
        job.resolve(src);
      }).finally(()=>{active--;if(pending.get(job.key)===job.promise)pending.delete(job.key);drain();});
    }
  }
  function request(value){
    let desc;try{desc=descriptor(value);}catch{return Promise.resolve(null);}
    if(disposed||desc.kind==='semantic')return Promise.resolve(null);
    const key=desc.kind+':'+desc.id;
    if(cache.has(key)){
      const entry=cache.get(key);if(entry.expires>Date.now()){cache.delete(key);cache.set(key,entry);return Promise.resolve(entry.src);}
      bytes-=entry.size;cache.delete(key);
    }
    if(pending.has(key))return pending.get(key);
    if(queue.length>=MAX_QUEUE)return Promise.resolve(null);
    let resolve;const promise=new Promise(done=>{resolve=done;});
    pending.set(key,promise);queue.push({key,desc,promise,resolve,generation});drain();return promise;
  }
  function html(value){
    let desc,invalid=false;try{desc=descriptor(value);}catch{desc={kind:'invalid',id:''};invalid=true;}
    const label=String(value?.label||'');
    const semantic=desc.kind==='semantic';
    return `<span class="terminal-icon${semantic?' ti-experience-levels':''}"${label?' role="img" aria-label="'+escape(label)+'"':' aria-hidden="true"'}><span class="ti-fallback" aria-hidden="true">${semantic?'':escape(label.slice(0,2)||'?')}</span>${semantic||invalid?'':`<img hidden alt="" data-terminal-icon-kind="${desc.kind}" data-terminal-icon-id="${escape(desc.id)}">`}</span>`;
  }
  function fallback(img){img.hidden=true;const node=img.parentElement?.querySelector('.ti-fallback,.mod-fallback');if(node)node.hidden=false;}
  function hydrate(root=global.document){
    if(disposed||!root?.querySelectorAll)return Promise.resolve();
    const nodes=[...(root.matches?.('img[data-terminal-icon-kind]')?[root]:[]),...root.querySelectorAll('img[data-terminal-icon-kind]')];
    const epoch=generation,scope=scopes.get(root)||0;
    async function hydrateOne(img){
      if(disposed||epoch!==generation||scope!==(scopes.get(root)||0)||!img.isConnected)return;
      const desc={kind:img.dataset.terminalIconKind,id:img.dataset.terminalIconId};
      const previous=attached.get(img),key=desc.kind+':'+desc.id;
      if(previous?.key===key&&previous.generation===generation)return previous.promise;
      const state={key,generation,promise:null};attached.set(img,state);fallback(img);
      state.promise=request(desc).then(src=>{
        if(disposed||attached.get(img)!==state||state.generation!==generation||!img.isConnected)return;
        if(!src){fallback(img);return;}
        img.onload=()=>{if(attached.get(img)!==state)return;img.hidden=false;const node=img.parentElement?.querySelector('.ti-fallback,.mod-fallback');if(node)node.hidden=true;};
        img.onerror=()=>{if(attached.get(img)!==state)return;if(cache.has(key)){bytes-=cache.get(key).size;cache.delete(key);}fallback(img);};
        img.src=src;
      });return state.promise;
    }
    let cursor=0;
    return Promise.all(Array.from({length:Math.min(4,nodes.length)},async()=>{
      while(cursor<nodes.length){const img=nodes[cursor++];await hydrateOne(img);}
    }));
  }
  function release(root){
    if(root)scopes.set(root,(scopes.get(root)||0)+1);
    else{
      generation++;pending.clear();for(const cancel of [...queries.values()])cancel();
      for(const job of queue.splice(0))job.resolve(null);
    }
    for(const img of attached.keys())if(!root||img===root||root.contains?.(img)){
      attached.delete(img);img.onload=null;img.onerror=null;img.removeAttribute('src');fallback(img);
    }
    if(!root){cache.clear();bytes=0;}
  }
  function invalidate(next=revision){
    if(disposed)return;
    revision=next;generation++;cache.clear();bytes=0;pending.clear();
    for(const job of queue.splice(0))job.resolve(null);
    release();queueMicrotask(()=>hydrate());
  }
  function dispose(){
    if(disposed)return;disposed=true;generation++;clearInterval(timer);observer?.disconnect();release();pending.clear();for(const cancel of [...queries.values()])cancel();
    for(const job of queue.splice(0))job.resolve(null);
  }
  const observer=global.MutationObserver?new global.MutationObserver(()=>{
    for(const img of attached.keys())if(!img.isConnected)release(img);
  }):null;
  observer?.observe(global.document.documentElement,{childList:true,subtree:true});
  const timer=setInterval(()=>{
    if(disposed||!attached.size)return;
    invoke('icons.state').then(state=>{if(typeof state?.revision==='number'&&state.revision!==revision)invalidate(state.revision);}).catch(()=>{});
  },2000);
  global.addEventListener?.('pagehide',dispose,{once:true});
  global.TerminalIcons=Object.freeze({html,request,hydrate,release,invalidate,dispose,
    stats:()=>({entries:cache.size,bytes,attached:attached.size,pending:pending.size,queued:queue.length,active,queries:queries.size,revision,disposed})});
})(window);
