(() => {
  if(contentView)return;
  let apps=[],revision=0;
  const form=$('#webAppForm'),error=$('#webAppsError');
  function target(value){
    if(/[\x00-\x20\x7f\\]/.test(value.trim()))throw new Error('网页链接包含非法字符');
    const url=new URL(value.trim());
    if(!['http:','https:'].includes(url.protocol)||!url.hostname||url.username||url.password)throw new Error('只支持不含用户名密码的 http / https 链接');
    return url;
  }
  function preview(){try{const url=target($('#webAppUrl').value);$('#webTarget').textContent=`目标：${url.protocol}//${url.host}`;}catch{$('#webTarget').textContent='请输入完整的 http:// 或 https:// 网页链接';}}
  function edit(app){
    form.hidden=false;$('#webAppId').value=app?.id||'';$('#webAppName').value=app?.name||'';$('#webAppUrl').value=app?.url||'';
    $('#webFormTitle').textContent=app?'编辑网页应用':'添加网页应用';$('#webFormError').textContent='';preview();$('#webAppUrl').focus();
    form.scrollIntoView({block:'nearest'});
  }
  function render(){
    const root=$('#webApps');
    root.innerHTML=apps.length?apps.map(app=>{
      const url=target(app.url);
      return `<article class="web-app-row"><button class="web-app-open" data-web-open="${esc(app.id)}"><b>${esc(app.name)}</b><small>${esc(url.protocol+'//'+url.host)}</small></button><button class="secondary" data-web-edit="${esc(app.id)}">编辑</button><button class="secondary" data-web-delete="${esc(app.id)}">删除</button></article>`;
    }).join(''):'<p class="web-empty">还没有网页应用。输入链接即可添加。</p>';
  }
  async function refresh(){
    const token=++revision;
    try{const rows=await native('apps.list');if(token!==revision)return;if(!Array.isArray(rows))throw new Error('请在游戏终端中保存网页应用');apps=rows;render();error.textContent='';}
    catch(e){if(token===revision)error.textContent=e.message;}
  }
  $('#addWebApp').addEventListener('click',()=>edit());
  $('#cancelWebApp').addEventListener('click',()=>{form.hidden=true;});
  $('#webAppUrl').addEventListener('input',preview);
  form.addEventListener('submit',async event=>{
    event.preventDefault();const submit=form.querySelector('[type=submit]');
    try{
      target($('#webAppUrl').value);submit.disabled=true;
      const result=await native('apps.save:'+JSON.stringify({id:$('#webAppId').value,name:$('#webAppName').value,url:$('#webAppUrl').value}));
      if(!result?.id)throw new Error('网页应用未能保存');
      form.hidden=true;await refresh();
    }catch(e){$('#webFormError').textContent=e.message;}finally{submit.disabled=false;}
  });
  $('#webApps').addEventListener('click',async event=>{
    const button=event.target.closest('button');if(!button)return;
    const id=button.dataset.webOpen||button.dataset.webEdit||button.dataset.webDelete;
    const app=apps.find(x=>x.id===id);if(!app)return;
    try{
      if(button.dataset.webEdit){edit(app);return;}
      if(button.dataset.webDelete){
        if(button.dataset.confirm!=='1'){button.dataset.confirm='1';button.textContent='确认删除';setTimeout(()=>{if(button.isConnected){button.dataset.confirm='';button.textContent='删除';}},3000);return;}
        await native('apps.delete:'+id);await refresh();return;
      }
      if(window.terminalLaunch)await window.terminalLaunch({kind:'web',id,name:app.name,source:button});
      else await native('apps.open:'+id);
    }catch(e){error.textContent=e.message;}
  });
  window.refreshWebApps=refresh;refresh();
})();
