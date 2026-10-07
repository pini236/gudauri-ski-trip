/* Which ski resort the site shows (decision 68, a first step towards many resorts). The list is data/resorts.json.
   ?resort=<id> in the address wins (docs/ARCHITECTURE.md, the review of 6.10.2026), then the last choice in this
   browser, then the default (Gudauri). A link straight into a run or a meeting without ?resort is a Gudauri link, as
   every such link shared before there were other resorts. Switching loads the page again: nothing is torn down. */
(function(){
  'use strict';
  const KEY='gud-resort';
  const FALLBACK={id:'gudauri',name:'Gudauri',country:'GE',dir:'data/',proj:{lat0:42.51,lon0:44.495},tz:'Asia/Tbilisi',
    features:['trip','meet','status','weather','kobi','locate','group','videos','pano']};
  const q=new URLSearchParams(location.search).get('resort');
  let saved=null;try{saved=localStorage.getItem(KEY);}catch(e){}
  const deep=/^#(map\/run|meet)\//.test(location.hash);
  const R={cur:FALLBACK,id:FALLBACK.id,list:[FALLBACK],def:FALLBACK.id};
  R.has=f=>R.cur.features.includes(f);
  R.isDefault=()=>R.id===R.def;
  // the clock there now, in hours east of UTC (summer time included)
  R.tzOffset=(d)=>{try{const p=new Intl.DateTimeFormat('en-US',{timeZone:R.cur.tz,hourCycle:'h23',year:'numeric',month:'numeric',day:'numeric',hour:'numeric',minute:'numeric'}).formatToParts(d||new Date()),
    v=Object.fromEntries(p.map(x=>[x.type,+x.value])),t=d||new Date(),utc=t.getTime()-t.getUTCSeconds()*1e3-t.getUTCMilliseconds();
    return Math.round((Date.UTC(v.year,v.month-1,v.day,v.hour,v.minute)-utc)/9e5)/4;}catch(e){return 4;}};
  // a link to share: carries ?resort= for any resort but the default, so it opens on the same mountain
  R.link=(hash,extra)=>{const s=new URLSearchParams(extra||'');if(!R.isDefault())s.set('resort',R.id);const qs=s.toString();
    return location.origin+location.pathname+(qs?'?'+qs:'')+(hash||'');};
  R.go=id=>{try{localStorage.setItem(KEY,id);}catch(e){}
    const s=new URLSearchParams(location.search);if(id===R.def)s.delete('resort');else s.set('resort',id);const qs=s.toString();
    const h=/^#(map|games|about)$/.test(location.hash)?location.hash:'#home';
    location.href=location.pathname+(qs?'?'+qs:'')+h;if(location.hash===h)location.reload();};
  R.ready=fetch('data/resorts.json').then(r=>r.ok?r.json():null).catch(()=>null).then(j=>{
    if(!j||!Array.isArray(j.resorts)||!j.resorts.length)return R.cur;
    R.list=j.resorts;R.def=j.default||j.resorts[0].id;
    const want=q||(deep?null:saved);
    R.cur=R.list.find(r=>r.id===want)||R.list.find(r=>r.id===R.def)||R.list[0];R.id=R.cur.id;
    if(q&&R.cur.id===q)try{localStorage.setItem(KEY,q);}catch(e){}
    // a choice kept in this browser shows in the address, so a link copied from it opens the same resort
    if(!q&&!R.isDefault())try{const s=new URLSearchParams(location.search);s.set('resort',R.id);history.replaceState(history.state,'',location.pathname+'?'+s+location.hash);}catch(e){}
    return R.cur;});
  R.ready.then(c=>{document.documentElement.dataset.resort=c.id;});
  window.RESORT=R;
})();
