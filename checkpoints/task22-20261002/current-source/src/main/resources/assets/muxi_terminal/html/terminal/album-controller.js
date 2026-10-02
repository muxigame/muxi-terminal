(function(root,factory){if(typeof module==='object' && module.exports)module.exports=factory();else root.MuxiAlbumController=factory();})(typeof globalThis==='object'?globalThis:this,()=>{
  function create({invoke,onChange=()=>{}}){
    let epoch=0,closed=true,selection=null,ticket=null;
    let state={photos:[],recycled:false,busy:false,selected:null,image:'',confirm:null,error:''};
    const current=e=>!closed && epoch===e;
    function update(value){state={...state,...value};onChange({...state});}
    async function load(recycled=false){
      closed=false;const e=++epoch;selection=null;ticket=null;
      update({photos:[],recycled,busy:true,selected:null,image:'',confirm:null,error:''});
      try{const photos=await invoke(recycled?'album.recycled':'album.photos');if(!current(e))return false;update({photos,busy:false});return true;}
      catch(error){if(current(e))update({busy:false,error:error.message});throw error;}
    }
    async function thumb(id){const e=epoch,recycled=state.recycled;const image=await invoke((recycled?'album.recycled-thumb:':'album.thumb:')+id);return current(e)?image:null;}
    async function open(id){
      if(closed || state.busy)return false;
      const photo=state.photos.find(p=>p.id===id);if(!photo)throw new Error('照片已不存在，请刷新相册。');
      const e=epoch,which=selection={};ticket=null;
      update({selected:photo,image:'',confirm:null,busy:true,error:''});
      try{const image=await invoke((state.recycled?'album.recycled-photo:':'album.photo:')+id);
        if(!current(e) || selection!==which)return false;update({image,busy:false});return true;}
      catch(error){if(current(e) && selection===which)update({busy:false,error:error.message});throw error;}
    }
    function closePhoto(){selection=null;ticket=null;update({selected:null,image:'',confirm:null,busy:false});return invoke('album.cancel-delete').catch(()=>{});}
    async function prepareDelete(){
      if(closed || state.busy || state.recycled || !state.selected)return false;
      const e=epoch,id=state.selected.id,which=selection;
      update({busy:true,error:''});
      try{const next=await invoke('album.prepare-delete:'+id);
        if(!current(e) || selection!==which)return false;
        if(!next || next.id!==id || typeof next.token!=='string')throw new Error('删除确认无效。');
        ticket=next;update({confirm:next,busy:false});return true;
      }catch(error){if(current(e))update({busy:false,error:error.message});throw error;}
    }
    function cancelDelete(){ticket=null;update({confirm:null});return invoke('album.cancel-delete').catch(()=>{});}
    async function confirmDelete(){
      if(closed || state.busy || !ticket || ticket.id!==state.selected?.id)throw new Error('请先确认要移入回收区的照片。');
      const e=epoch,accepted=ticket;ticket=null;update({busy:true,error:''});
      try{await invoke('album.recycle:'+accepted.token);if(!current(e))return false;await load(false);return true;}
      catch(error){if(current(e))update({confirm:null,busy:false,error:'未完成移入回收区，请重新确认。'+error.message});throw error;}
    }
    async function restore(){
      if(closed || state.busy || !state.recycled || !state.selected)return false;
      const e=epoch,id=state.selected.id;update({busy:true,error:''});
      try{await invoke('album.restore:'+id);if(!current(e))return false;await load(true);return true;}
      catch(error){if(current(e))update({busy:false,error:'恢复失败；同名照片不会被覆盖。'+error.message});throw error;}
    }
    function dispose(){closed=true;++epoch;selection=null;ticket=null;update({photos:[],selected:null,image:'',confirm:null,busy:false});return invoke('album.cancel-delete').catch(()=>{});}
    return {load,thumb,open,closePhoto,prepareDelete,cancelDelete,confirmDelete,restore,dispose,getState:()=>({...state})};
  }
  return {create};
});
