/* Camera lifecycle independent of the shared shell. Uses only the private native transport. */
(function(root,factory){if(typeof module==='object' && module.exports)module.exports=factory();else root.MuxiCameraController=factory();})(typeof globalThis==='object'?globalThis:this,()=>{
  function create({invoke,onState=()=>{},onError=()=>{},schedule=setTimeout,cancel=clearTimeout}){
    let epoch=0,timer=null,closed=true,latest={active:false,busy:false,preview:'',sequence:0};
    const current=e=>!closed && e===epoch;
    function clear(){if(timer!==null)cancel(timer);timer=null;}
    async function poll(e){
      try{const state=await invoke('camera.state');if(!current(e))return;latest=state;onState(state);}
      catch(error){if(current(e))onError(error);}
      if(current(e))timer=schedule(()=>poll(e),500);
    }
    async function start(){
      clear();closed=false;const e=++epoch;latest={active:false,busy:false,preview:'',sequence:0};onState(latest);
      try{await invoke('camera.begin');if(current(e))await poll(e);}
      catch(error){if(current(e)){closed=true;onError(error);}throw error;}
    }
    function stop(){clear();closed=true;++epoch;latest={...latest,active:false,preview:''};onState(latest);return invoke('camera.stop').catch(()=>{});}
    async function command(request){
      if(closed || !latest.active)throw new Error('预览已暂停，请先恢复预览。');
      if(latest.busy)throw new Error('正在处理照片，请稍候。');
      const e=epoch;latest={...latest,busy:true};onState(latest);
      try{await invoke(request);if(current(e)){const state=await invoke('camera.state');if(current(e)){latest=state;onState(state);}}}
      catch(error){if(current(e)){latest={...latest,busy:false};onState(latest);onError(error);}throw error;}
    }
    return {start,stop,mode:value=>{
      if(!['forward','selfie'].includes(value))return Promise.reject(new Error('镜头模式无效。'));
      return command('camera.mode:'+value);
    },shutter:()=>latest.preview?command('camera.shutter'):Promise.reject(new Error('等待第一帧预览后再拍照。')),
    getState:()=>({...latest}),dispose:stop};
  }
  return {create};
});
