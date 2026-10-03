// Accounts and groups on the site (round 12, stage 13.9). The server and its rules: server/CONTRACT.md.
// A guest never touches the server: the supabase library loads only when there is a session, or when someone signs in
// or opens an invitation (joining is what makes an anonymous identity, CONTRACT, "זהות"). What the pages show is kept in
// this browser too, so they open at once and without a connection.
window.ACCOUNT=(function(){
  // from js/app.js, at start
  let MYTRIP,esc,MEET,renderTicket,countdown;
  const URL_='https://vanuhuzuhnljvcoihvys.supabase.co',KEY='sb_publishable_BpJiIkRbIob7U6xa_Xt_9A_AJqtoupz',REGION='eu-central-1';
  // the web client from the Google console (server/README.md, "כניסה עם גוגל"); empty until Pini sends it
  const GOOGLE_CLIENT_ID='116975370454-2f0dqvfn3obp9i0j24r8pn71h230c6q8.apps.googleusercontent.com';
  const APPLE=false; // Sign in with Apple on the web: once the Apple developer account exists
  const SB_KEY='sb-vanuhuzuhnljvcoihvys-auth-token',CACHE='gud-acct',GAMES=['descent','school','fresh','snowball','merge'];
  const $=id=>document.getElementById(id);
  const ICON={people:'<circle cx="9" cy="8" r="3.2"/><path d="M3 19c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5"/><circle cx="17" cy="9" r="2.5"/><path d="M16 13.6c2.8.3 5 2.2 5 5.4"/>',
    out:'<path d="M14 5h5v14h-5M10 8l-4 4 4 4M6 12h10"/>',check:'<path d="M5 12l5 5 9-10"/>',lock:'<rect x="5" y="11" width="14" height="9"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    plus:'<path d="M12 5v14M5 12h14"/>',x:'<path d="M6 6l12 12M18 6L6 18"/>',cloud:'<path d="M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.6 1.5A3.5 3.5 0 0 0 7 18z"/>'};
  const ic=(n,s=20)=>`<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">${ICON[n]}</svg>`;
  const tr=(n,p)=>{try{track(n,p);}catch(e){}};
  const letter=n=>(String(n||'').trim()[0]||'?').toUpperCase();
  const dm=iso=>iso?`${+iso.slice(8,10)}.${+iso.slice(5,7)}`:'';

  // ---- the connection
  let sb=null,libP=null;
  const hasSession=()=>{try{return !!localStorage.getItem(SB_KEY);}catch(e){return false;}};
  function lib(){
    if(window.supabase)return Promise.resolve();
    if(!libP)libP=new Promise((ok,no)=>{const s=document.createElement('script');s.src='js/vendor/supabase-2.117.2.js';s.onload=ok;s.onerror=()=>{libP=null;no(Object.assign(new Error('offline'),{code:'offline'}));};document.head.appendChild(s);});
    return libP;}
  async function client(){await lib();if(!sb){sb=supabase.createClient(URL_,KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:false}});
    // a session that ends (a refresh the server refused) is never replaced by a new guest in silence (CONTRACT)
    sb.auth.onAuthStateChange((ev,s)=>{if(ev==='SIGNED_OUT'&&!s&&state.uid&&!signingOut){wipe();state.ended=true;paint();}});}
    return sb;}
  const err=code=>Object.assign(new Error(code),{code});
  async function api(action,body){
    const c=await client();let r;
    try{r=await c.functions.invoke('api/'+action,{body:body||{},region:REGION});}catch(e){throw err('offline');}
    if(r.error){let code=r.error.name==='FunctionsFetchError'?'offline':'server_error';
      try{const j=await r.error.context.json();if(j&&j.error)code=j.error;}catch(e){}
      throw err(code);}
    return r.data;}
  // a direct write to a table answers {code, message}: a limit comes back as its own code (P0001), like the API's (server/CONTRACT.md)
  function restCode(r){const e=r.error,m=e.message||'',c=e.code||'';if(/fetch/i.test(m))return 'offline';
    if(r.status===401||c==='PGRST301'||/jwt/i.test(m))return 'not_signed_in';if(c==='P0001'&&/^[a-z_]+$/.test(m))return m;
    if(c==='42501')return 'not_allowed';if(/^2[23]/.test(c))return 'invalid_input';return 'server_error';}
  async function rest(q){const r=await q;if(r.error)throw err(restCode(r));return r.data;}
  async function user(){const c=await client();const {data}=await c.auth.getSession();return data.session&&data.session.user||null;}
  async function guest(){const c=await client();if(await user())return;const r=await c.auth.signInAnonymously();if(r.error)throw err('server_error');}

  // ---- what we know, kept in this browser
  let state=load(),signingOut=false;
  function load(){try{return JSON.parse(localStorage.getItem(CACHE)||'null')||{};}catch(e){return {};}}
  function keep(){try{localStorage.setItem(CACHE,JSON.stringify(state));}catch(e){}}
  function wipe(){const ended=state.ended;state={};if(ended)state.ended=true;try{localStorage.removeItem(CACHE);Object.keys(localStorage).filter(k=>k.startsWith('gud-group-')).forEach(k=>localStorage.removeItem(k));}catch(e){}
    const t=MYTRIP.get();if(t&&t.sid){delete t.sid;MYTRIP.set(t);}}
  const signedIn=()=>!!state.uid&&hasSession();
  const registered=()=>signedIn()&&!state.anon;
  async function refresh(){
    if(!hasSession()){if(state.uid)wipe();paint();return;}
    try{const u=await user();if(!u){wipe();paint();return;}
      const c=await client();
      const [me]=await rest(c.from('profiles').select('display_name').eq('id',u.id));
      const mine=await rest(c.from('group_members').select('group_id,role,display_name,trip_id').eq('user_id',u.id));
      const ids=mine.map(m=>m.group_id);
      const gs=ids.length?await rest(c.from('groups').select('id,name,starts_on,ends_on').in('id',ids)):[];
      const all=ids.length?await rest(c.from('group_members').select('group_id').in('group_id',ids)):[];
      const ident=(u.identities||[]).map(i=>i.provider);
      state={uid:u.id,anon:!!u.is_anonymous,name:(me&&me.display_name)||(mine[0]&&mine[0].display_name)||'',providers:ident.filter(p=>p==='google'||p==='apple'),
        groups:gs.map(g=>{const m=mine.find(x=>x.group_id===g.id);return {...g,role:m.role,me:m.display_name,trip:m.trip_id,count:all.filter(a=>a.group_id===g.id).length};})
          .sort((a,b)=>(a.starts_on||'9').localeCompare(b.starts_on||'9'))};
      keep();
      if(!state.anon&&!MYTRIP.get())await pullTrip(c);
      if(state.groups.length)await sendBests();}
    catch(e){/* no connection: what was kept is shown */}
    paint();}

  // ---- the home page, the pass and the settings
  function paint(){
    const g=signedIn()&&state.groups&&state.groups[0];
    const sub=$('groupBoardSub');if(sub)sub.textContent=g?`${g.name} · ${T('group.members_n',{n:g.count})}`:T('home.board_group_sub');
    paintPass();paintTop();paintAbout();
    if(window.MEET_GROUP)window.MEET_GROUP();}
  function paintPass(){
    const me=signedIn()&&state.name;
    document.querySelectorAll('[data-f="pax"]').forEach(e=>{
      if(!MYTRIP.get())return;
      e.innerHTML=me?`<button type="button" class="bp-who" data-who>${esc(state.name)}</button>`:`<a class="bp-who guest" href="#signin">${esc(T('ticket.pax_guest'))}</a>`;});}
  function paintTop(){
    let a=document.querySelector('.tb-acct');
    if(!a){a=document.createElement('a');a.className='tb-acct';document.querySelector('.topbar .dn').after(a);}
    const me=signedIn();a.classList.toggle('me',me);a.href=me?'#account':'#signin';
    a.innerHTML=me?esc(letter(state.name)):`${ic('people',18)}<span>${esc(T('acct.signin_title'))}</span>`;
    a.setAttribute('aria-label',me?`${T('acct.title')}: ${state.name}`:T('acct.signin_title'));}
  function paintAbout(){
    const box=$('abMe');if(!box)return;const me=signedIn();
    const prov=state.providers&&state.providers[0];
    box.innerHTML=`<h2>${esc(T(me?'acct.title':'acct.signin_title'))}</h2><div class="ab-pass"><div class="ab-pass-top"><span>${esc(T(me?'acct.pass_top_me':'acct.pass_top_guest'))}</span><span>${esc(me?(prov?T('acct.'+prov+'_name'):T('acct.signed_guest')):T('acct.no_account'))}</span></div>
      <div class="ab-pass-body"><span class="ab-av${me?'':' guest'}">${me?esc(letter(state.name)):'?'}</span><div><b>${esc(me?state.name:T('ticket.pax_guest'))}</b><span>${esc(me?(state.anon?T('acct.guest_member'):T('acct.pop_sub')):T('acct.pass_guest_sub'))}</span>
      <span class="ab-links">${me?`<a href="#account">${esc(T('acct.manage'))}</a>`+(state.anon?'':`<button type="button" class="out" data-signout>${ic('out',18)}${esc(T('acct.sign_out_short'))}</button>`):`<a href="#signin">${esc(T('acct.signin_title'))}</a>`}</span></div></div></div>`;}
  // tapping your name on the pass (P7): a small card with the account and signing out
  document.addEventListener('click',e=>{
    const who=e.target.closest('[data-who]'),pop=document.querySelector('.who-pop');
    if(pop&&!e.target.closest('.who-pop')){pop.remove();if(!who)return;}
    if(who){e.stopPropagation();const wrap=who.closest('.ticket-wrap')||document.querySelector('.ticket-wrap'),r=who.getBoundingClientRect(),w=wrap.getBoundingClientRect();
      const prov=state.providers&&state.providers[0];
      wrap.insertAdjacentHTML('beforeend',`<div class="who-pop" role="dialog" aria-label="${esc(T('ticket.pax'))}" style="top:${Math.round(r.bottom-w.top+14)}px">
        <div class="ac-who"><span class="ac-av" style="width:44px;height:44px;font-size:28px">${esc(letter(state.name))}</span><span><b>${esc(state.name)}</b><small>${esc(prov?T('acct.signed_'+prov)+' · '+T('acct.pop_sub'):T('acct.signed_guest'))}</small></span></div>
        <div class="wp-row"><a href="#account">${esc(T('acct.manage'))}</a>${state.anon?`<a href="#signin">${esc(T('acct.signin_title'))}</a>`:`<button type="button" data-signout>${ic('out',18)}${esc(T('acct.sign_out_short'))}</button>`}</div></div>`);}
    const out=e.target.closest('[data-signout]');if(out)signOut();});
  async function signOut(){
    signingOut=true;try{const c=await client();await c.auth.signOut({scope:'local'});}catch(e){try{localStorage.removeItem(SB_KEY);}catch(x){}}
    signingOut=false;wipe();tr('sign_out');document.querySelector('.who-pop')?.remove();paint();if(/^#(account|group)/.test(location.hash))location.hash='#home';}

  // ---- sign in (W4), with Google's own button (Google Identity Services: an ID token, no redirect)
  function loadScript(src){return new Promise((ok,no)=>{const s=document.createElement('script');s.src=src;s.async=true;s.onload=ok;s.onerror=()=>no(err('offline'));document.head.appendChild(s);});}
  async function sha256(s){const b=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s));return [...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,'0')).join('');}
  async function withIdToken(provider,token,nonce){
    const c=await client();const u=await user();
    if(u&&u.is_anonymous){ // a guest keeps their place: Google joins the same identity
      const r=await c.auth.linkIdentity({provider,token,nonce});
      if(r.error){ // that Google account already has an identity: merge the guest into it (CONTRACT)
        const {ticket}=await api('create_merge_ticket');signingOut=true;await c.auth.signOut({scope:'local'});signingOut=false;
        const s=await c.auth.signInWithIdToken({provider,token,nonce});if(s.error)throw err('server_error');await api('merge_guest',{ticket});}}
    else{const s=await c.auth.signInWithIdToken({provider,token,nonce});if(s.error)throw err('server_error');}
    tr('sign_in',{method:provider});state.ended=false;await refresh();pushTrip();
    let next='#account';try{next=sessionStorage.getItem('gud-after-signin')||next;sessionStorage.removeItem('gud-after-signin');}catch(e){}
    if(location.hash===next)route(next);else location.hash=next;} // signed in from the page you were on (/account): draw it again
  async function providerButtons(host,del){
    host.innerHTML='';const soon=!GOOGLE_CLIENT_ID;
    if(GOOGLE_CLIENT_ID){const g=document.createElement('div');g.className='ac-gsi';host.appendChild(g);
      try{if(!(window.google&&google.accounts&&google.accounts.id))await loadScript('https://accounts.google.com/gsi/client');const raw=crypto.randomUUID(),hashed=await sha256(raw);
        google.accounts.id.initialize({client_id:GOOGLE_CLIENT_ID,nonce:hashed,callback:r=>withIdToken('google',r.credential,raw).catch(e=>showErr(host.closest('.ac'),e))});
        google.accounts.id.renderButton(g,{type:'standard',theme:'outline',size:'large',text:'continue_with',width:Math.min(host.clientWidth||360,400),locale:I18N.lang});}
      catch(e){showErr(host.closest('.ac'),e);}}
    else host.insertAdjacentHTML('beforeend',`<button type="button" class="ac-btn google" disabled><span class="ac-mark">G</span>${esc(T(del?'acct.delete_google':'acct.google'))}</button>`);
    // Apple waits for the developer account: its button says so ("soon") instead of looking broken
    host.insertAdjacentHTML('beforeend',`<button type="button" class="ac-btn apple${APPLE?'':' soon'}" disabled><span class="ac-mark">A</span>${esc(T(del?'acct.delete_apple':'acct.apple'))}${APPLE?'':`<small class="ac-soon">${esc(T('acct.apple_soon'))}</small>`}</button>`);
    return soon||!APPLE;}
  function showErr(root,e){const p=root&&root.querySelector('[data-err]');if(!p)return;
    const code=e&&e.code||'server_error';const own=T('group.err_'+code);
    p.textContent=code==='offline'?T('acct.offline'):code==='not_signed_in'||code==='no_session'?T('acct.session_over'):own!=='group.err_'+code?own:T('acct.error');p.hidden=false;}
  const clearErr=root=>{const p=root.querySelector('[data-err]');if(p)p.hidden=true;};

  // ---- the pages
  const PAGES={signin:'signinPage',account:'accountPage',del:'deletePage',join:'joinPage',group:'groupPage'};
  let live=null;
  function route(h){
    const m=/^#(signin|account|join|group)(?:\/(.*))?$/.exec(h||''),which=m?(m[1]==='account'&&m[2]==='delete'?'del':m[1]):'';
    Object.entries(PAGES).forEach(([k,id])=>{const p=$(id);if(p)p.hidden=k!==which;});
    if(which!=='group'&&live){live.unsubscribe();live=null;}
    if(!which)return false;
    window.scrollTo(0,0);const pg=$(PAGES[which]);clearErr(pg);
    SNOW.scan(pg);
    ({signin:openSignin,account:openAccount,del:openDelete,join:()=>openJoin(decodeURIComponent(m[2]||'')),group:()=>openGroup(m[2]||'')})[which]();
    return true;}

  async function openSignin(){
    if(registered()){location.replace('#account');return;}
    const soon=await providerButtons(document.querySelector('#signinPage [data-providers]'));
    const n=$('signinSoon');n.hidden=!soon||!!GOOGLE_CLIENT_ID;n.innerHTML=`${ic('lock',18)}<span>${esc(T('acct.soon'))}</span>`;}

  function openAccount(){
    if(!signedIn()){location.replace('#signin');return;}
    const card=$('acMe');
    const show=()=>{card.innerHTML=`<div class="ac-who"><span class="ac-av">${esc(letter(state.name))}</span><span style="display:flex;flex-direction:column;min-width:0"><b class="ac-name">${esc(state.name||T('ticket.pax_guest'))}</b><span class="ac-sub">${esc(T('acct.name_hint'))} · <button type="button" class="ac-link" data-rename style="display:inline-flex;align-items:center;min-height:44px;margin:-10px 0;background:none;border:0;padding:0 4px;font:inherit;color:var(--glacier);font-weight:700;cursor:pointer">${esc(T('acct.change'))}</button></span></span></div>`;};
    show();
    card.onclick=e=>{if(!e.target.closest('[data-rename]'))return;
      card.innerHTML=`<form class="ac-form" data-rn><label class="ac-fld"><span>${esc(T('acct.name_hint'))}</span><input type="text" name="n" maxlength="40" value="${esc(state.name)}"></label><button type="submit" class="ac-btn">${esc(T('acct.save'))}</button></form>`;
      const f=card.querySelector('form');f.n.focus();
      f.onsubmit=async ev=>{ev.preventDefault();const n=f.n.value.trim();if(!n||n.length>40)return;
        try{const c=await client();await rest(c.from('profiles').update({display_name:n}).eq('id',state.uid));state.name=n;keep();paint();show();}catch(x){showErr($('accountPage'),x);}};};
    const gn=$('acGuestNote');gn.hidden=!state.anon;gn.innerHTML=`${ic('lock',18)}<span>${esc(T('acct.guest_member'))}</span>`;
    const p=state.providers||[];
    $('acWays').innerHTML=['google','apple'].map(k=>`<div class="ac-row"><span class="ac-mark" style="width:28px;height:28px">${k==='google'?'G':'A'}</span><span class="ac-txt"><b>${esc(T('acct.'+k+'_name'))}</b><small>${esc(T(p.includes(k)?'acct.connected':'acct.not_connected'))}</small></span>${p.includes(k)?`<span class="ac-ok">${ic('check',22)}</span>`:k==='apple'&&!APPLE?`<small class="ac-soon">${esc(T('acct.apple_soon'))}</small>`:`<a href="#signin">${esc(T('acct.connect'))}</a>`}</div>`).join('');
    const gs=state.groups||[];
    $('acGroups').innerHTML=gs.length?gs.map(g=>`<div class="ac-row"><span class="ac-av sm">${esc(letter(g.name))}</span><span class="ac-txt"><b>${esc(g.name)}</b><small>${esc(T('group.members_n',{n:g.count}))}${g.role==='admin'?' · '+esc(T('acct.role_admin')):''}</small></span><a href="#group/${g.id}">${esc(T('acct.open'))}</a></div>`).join('')
      :`<p class="ac-lead" style="margin:0">${esc(T('acct.no_groups'))}</p>`;
    $('acOut').hidden=!!state.anon;$('acOut').onclick=signOut;
    refresh().then(()=>{if(!$('accountPage').hidden&&signedIn()&&!card.querySelector('form'))openAccount();});}

  let delArm=0;
  async function openDelete(){
    const box=$('delBtns');const n=$('delSoon');n.hidden=true;
    if(signedIn()){
      box.innerHTML=`<button type="button" class="ac-btn solid-red" id="delGo">${esc(T('acct.delete_now'))}</button>`;
      $('delGo').onclick=async ev=>{const b=ev.currentTarget;
        if(Date.now()-delArm>6000){delArm=Date.now();b.textContent=T('acct.delete_confirm');return;}
        b.disabled=true;try{await api('delete_my_account');tr('account_delete');signingOut=true;try{const c=await client();await c.auth.signOut({scope:'local'});}catch(x){}signingOut=false;
          wipe();try{localStorage.removeItem(SB_KEY);}catch(x){}paint();box.innerHTML=`<p class="ac-note ac-ok">${ic('check',18)}<span>${esc(T('acct.deleted'))}</span></p>`;}
        catch(x){b.disabled=false;showErr($('deletePage'),x);}};}
    else{try{sessionStorage.setItem('gud-after-signin','#account/delete');}catch(e){}
      const soon=await providerButtons(box,true);n.hidden=!soon||!!GOOGLE_CLIENT_ID;n.innerHTML=`${ic('lock',18)}<span>${esc(T('acct.soon'))}</span>`;}}

  // ---- an invitation (W7): /j/<token> and /join/<code> land here
  let joinCode='',preview=null;
  async function openJoin(code){
    code=code.replace(/[\s-]/g,'');if(/^[a-z]{6}$/i.test(code))code=code.toUpperCase();joinCode=code;preview=null;
    const card=$('joinCard'),f=$('joinForm'),pg=$('joinPage');
    ['joinForm','joinNoSignup','joinReclaim','joinApp'].forEach(id=>$(id).hidden=true);$('joinNames').hidden=true;
    card.querySelectorAll(':scope>:not(.snowcap)').forEach(x=>x.remove());
    card.insertAdjacentHTML('beforeend',`<p>${esc(T('join.loading'))}</p>`);
    if(!code){location.replace('#group');return;}
    let r;
    try{await guest();r=await api('invite_preview',{code});}
    catch(e){card.querySelector('p').remove();showErr(pg,e);return;}
    card.querySelectorAll(':scope>:not(.snowcap)').forEach(x=>x.remove());
    if(r.status!=='ok'){card.insertAdjacentHTML('beforeend',`<p>${esc(T('join.st_'+r.status))}</p>`);return;}
    preview=r;
    if(r.already_member){await refresh();location.replace('#group/'+r.group_id);return;}
    card.insertAdjacentHTML('beforeend',`<small>${esc(T('join.invited_to'))}</small><p class="ac-big">${esc(r.name)}</p><p>${esc(T('group.members_n',{n:(r.members||[]).length}))}${r.starts_on?' · '+esc(T('join.dates',{from:dm(r.starts_on),to:dm(r.ends_on||r.starts_on)})):''}</p>`);
    f.hidden=false;$('joinNoSignup').hidden=false;
    if(!f.name.value&&state.name)f.name.value=state.name;
    $('joinReclaim').hidden=!(r.members||[]).length;
    if(/^[A-Z]{6}$/i.test(code)){$('joinApp').hidden=false;$('joinCode').textContent=code.toUpperCase();}}
  $('joinForm').addEventListener('submit',async e=>{e.preventDefault();const pg=$('joinPage');clearErr(pg);
    const name=e.target.name.value.trim();if(!name||name.length>40){e.target.name.focus();return;}
    const b=e.target.querySelector('button');b.disabled=true;
    try{const r=await api('join_group',{code:joinCode,display_name:name});
      if(r.status==='joined'||r.status==='already_member'){tr('group_join',{via:joinCode.length>8?'link':'code'});await refresh();await pushTrip();location.hash='#group/'+r.group_id;}
      else{const p=pg.querySelector('[data-err]');p.textContent=T('join.st_'+r.status);p.hidden=false;if(r.status==='pending')$('joinForm').hidden=true;}}
    catch(x){showErr(pg,x);}b.disabled=false;});
  $('joinReclaimBtn').addEventListener('click',()=>{const box=$('joinNames');if(!preview)return;box.hidden=false;
    box.innerHTML=preview.members.map(m=>`<button type="button" data-mid="${esc(m.user_id)}">${esc(m.display_name)}</button>`).join('');});
  $('joinNames').addEventListener('click',async e=>{const b=e.target.closest('[data-mid]');if(!b)return;const pg=$('joinPage');clearErr(pg);
    try{const r=await api('request_reclaim',{code:joinCode,member_id:b.dataset.mid});const p=pg.querySelector('[data-err]');p.textContent=T('join.st_'+r.status);p.hidden=false;$('joinNames').hidden=true;}
    catch(x){showErr(pg,x);}});

  // ---- the group page (W8, W10)
  let G=null,tab='flights',gid='';
  const gKey=id=>'gud-group-'+id;
  async function openGroup(id){
    const pg=$('groupPage');
    if(!signedIn()||!(state.groups||[]).length){showNone();if(signedIn())refresh().then(()=>{if(!$('groupPage').hidden&&(state.groups||[]).length)openGroup(id);});return;}
    gid=(state.groups.find(g=>g.id===id)||state.groups[0]).id;
    $('grNone').hidden=true;$('grSome').hidden=false;
    $('grNote').innerHTML=`${ic('lock',18)}<span>${esc(T('group.private_note'))}</span>`;
    try{G=JSON.parse(localStorage.getItem(gKey(gid))||'null');}catch(e){G=null;}
    if(G)draw();else{$('grTitle').textContent=state.groups.find(g=>g.id===gid).name;$('grMain').innerHTML='';$('grSide').innerHTML='';}
    await reload();
    if(!live&&sb){const ch=sb.channel('group:'+gid);const f='group_id=eq.'+gid;let t=0;const again=()=>{clearTimeout(t);t=setTimeout(reload,400);};
      ['group_members','meetups','invites','join_requests'].forEach(tb=>ch.on('postgres_changes',{event:'*',schema:'public',table:tb,filter:f},again));
      ch.on('postgres_changes',{event:'DELETE',schema:'public',table:'group_members'},again).on('postgres_changes',{event:'DELETE',schema:'public',table:'meetups'},again)
        .on('postgres_changes',{event:'*',schema:'public',table:'trips'},again).on('postgres_changes',{event:'*',schema:'public',table:'groups',filter:'id=eq.'+gid},again);
      ch.subscribe();live=ch;}}
  function showNone(){
    $('grTitle').textContent=T('group.empty_title');$('grNone').hidden=false;$('grSome').hidden=true;$('grNote').innerHTML='';
    const f=$('grCreate'),reg=registered();f.querySelector('.ac-needs').hidden=reg;
    f.querySelectorAll('input,button').forEach(x=>x.disabled=!reg);
    if(reg&&!f.me.value)f.me.value=state.name||'';}
  async function reload(){
    if(!gid)return;const pg=$('groupPage');
    try{const c=await client();
      const [g]=await rest(c.from('groups').select('id,name,starts_on,ends_on').eq('id',gid));
      if(!g){await refresh();location.replace('#group');return;}
      const members=await rest(c.from('group_members').select('user_id,role,display_name,trip_id,joined_at').eq('group_id',gid).order('joined_at'));
      const tids=members.map(m=>m.trip_id).filter(Boolean);
      const trips=tids.length?await rest(c.from('trips').select('id,owner_id,out_date,out_flight,out_from,out_to,out_departs,ret_date,ret_flight,ret_departs,entered_by').in('id',tids)):[];
      const meetups=await rest(c.from('meetups').select('id,station,meet_at,note').eq('group_id',gid).order('meet_at'));
      const inv=await rest(c.from('invites').select('id,code,token,revoked_at,expires_at,requires_approval,max_uses,uses,created_at').eq('group_id',gid).is('revoked_at',null));
      // only an invite that still works is shown and shared (CONTRACT): not used up, and before it ends; with no end set,
      // a day after the group's trip ends, or 90 days after it was made
      const ends=v=>v.expires_at?Date.parse(v.expires_at):g.ends_on?Date.parse(g.ends_on+'T00:00:00Z')+864e5:Date.parse(v.created_at)+90*864e5;
      const live_=inv.filter(v=>!(v.max_uses&&v.uses>=v.max_uses)&&!(ends(v)<=Date.now()));
      const me=members.find(m=>m.user_id===state.uid),admin=me&&me.role==='admin';
      const reqs=admin?await rest(c.from('join_requests').select('id,display_name,kind,reclaim_user_id,status').eq('group_id',gid).eq('status','pending')):[];
      G={g,members,trips,meetups,invite:live_[live_.length-1]||null,reqs,scores:G&&G.g&&G.g.id===gid?G.scores:null,at:Date.now()};
      try{localStorage.setItem(gKey(gid),JSON.stringify(G));}catch(e){}
      clearErr(pg);draw();if(tab==='scores')loadScores();}
    catch(e){if(G)draw();showErr(pg,e);}}
  function draw(){
    if(!G)return;const {g,members}=G;
    $('grTitle').textContent=g.name;
    $('grMeta').textContent=[T('group.members_n',{n:members.length}),g.starts_on?T('join.dates',{from:dm(g.starts_on),to:dm(g.ends_on||g.starts_on)}):''].filter(Boolean).join(' · ');
    // tabs for a screen reader and the keyboard: one tab in the Tab order, the arrows move between them
    $('grTabs').setAttribute('role','tablist');$('grMain').setAttribute('role','tabpanel');$('grMain').setAttribute('aria-labelledby','grTab-'+tab);
    document.querySelectorAll('#grTabs [data-tab]').forEach(b=>{const on=b.dataset.tab===tab;b.id='grTab-'+b.dataset.tab;b.setAttribute('role','tab');b.setAttribute('aria-selected',String(on));b.setAttribute('aria-controls','grMain');b.tabIndex=on?0:-1;});
    $('grMain').innerHTML=({flights:drawFlights,meetups:drawMeetups,scores:drawScores,members:drawMembers})[tab]();
    $('grSide').innerHTML=drawInvite();}
  $('grTabs').addEventListener('click',e=>{const b=e.target.closest('[data-tab]');if(!b)return;tab=b.dataset.tab;draw();if(tab==='scores'&&!(G&&G.scores))loadScores();});
  $('grTabs').addEventListener('keydown',e=>{const all=[...$('grTabs').querySelectorAll('[data-tab]')].filter(b=>!b.hidden),i=all.indexOf(document.activeElement);if(i<0)return;
    const fwd=I18N.ltr?'ArrowRight':'ArrowLeft',back=I18N.ltr?'ArrowLeft':'ArrowRight';
    const j=e.key===fwd?(i+1)%all.length:e.key===back?(i-1+all.length)%all.length:e.key==='Home'?0:e.key==='End'?all.length-1:-1;
    if(j<0)return;e.preventDefault();all[j].click();all[j].focus();});
  function drawFlights(){
    const {members,trips}=G,byId=Object.fromEntries(trips.map(t=>[t.id,t])),me=members.find(m=>m.user_id===state.uid)||{};
    const groups=new Map();members.forEach(m=>{const t=byId[m.trip_id];if(!t)return;const k=[t.out_date,t.out_flight||'',t.out_from||'',t.out_to||''].join('|');
      if(!groups.has(k))groups.set(k,{t,people:[]});groups.get(k).people.push(m);});
    const cards=[...groups.values()].sort((a,b)=>(a.t.out_date+(a.t.out_departs||'')).localeCompare(b.t.out_date+(b.t.out_departs||''))).map(({t,people},i)=>{
      const mine=people.some(p=>p.user_id===state.uid);
      return `<div class="ac-flight${i?' other':''}"><div class="fs"><span>${esc(T('trip.out'))}${t.out_flight?' · <span dir="ltr">'+esc(t.out_flight)+'</span>':''}</span><span dir="ltr">${esc(dm(t.out_date))}</span></div>
        <div class="fb"><span><small>${esc(T('ticket.from'))}</small><b dir="ltr">${esc(t.out_from||'—')}</b></span><span class="mid"><small>${esc(T('trip.number'))}</small><b dir="ltr">${esc(t.out_flight||'—')}</b>${t.out_departs?`<small>${esc(T('group.departs',{t:t.out_departs.slice(0,5)}))}</small>`:''}</span><span style="text-align:end"><small>${esc(T('ticket.to'))}</small><b dir="ltr">${esc(t.out_to||'—')}</b></span></div>
        <div class="fp">${people.map(p=>`<span${p.user_id===state.uid?' class="me"':''}${byId[p.trip_id]&&byId[p.trip_id].entered_by&&byId[p.trip_id].entered_by!==p.user_id?` title="${esc(T('group.entered_by_admin'))}"`:''}>${esc(p.display_name)}</span>`).join('')}</div>
        ${mine?'':`<div class="fa"><button type="button" data-same="${esc(t.id)}">${esc(T('group.same_flight'))}</button></div>`}</div>`;}).join('');
    const none=members.filter(m=>!byId[m.trip_id]);
    const local=MYTRIP.get();
    const mineAct=!me.trip_id||!byId[me.trip_id]?(local?`<button type="button" class="ac-btn ghost" data-showtrip>${ic('plus')}${esc(T('group.show_my_trip'))}</button>`:`<a class="ac-btn ghost" href="#trip">${ic('plus')}${esc(T('group.add_my_trip'))}</a>`):'';
    return (cards||`<p class="ac-lead" style="margin:0">${esc(T('group.no_flights'))}</p>`)
      +(none.length?`<p class="ac-note"><span><b>${esc(T('group.no_flight'))}:</b> ${none.map(m=>esc(m.display_name)).join(', ')}</span></p>`:'')+mineAct;}
  function gTime(iso){const d=new Date(new Date(iso).getTime()+4*36e5);return {hm:d.toISOString().slice(11,16),day:d.toISOString().slice(0,10)};}
  function dayName(iso){const d=new Date(iso+'T12:00:00Z');try{return (I18N.lang==='he'?new Intl.DateTimeFormat('he',{weekday:'narrow',timeZone:'UTC'}).format(d)+' ':I18N.date(d,{weekday:'short',timeZone:'UTC'})+' ')+dm(iso);}catch(e){return dm(iso);}}
  function drawMeetups(){
    const list=G.meetups.map(m=>{const {hm,day}=gTime(m.meet_at),name=(MEET&&MEET.station(m.station))||m.station;
      return `<div class="ac-meet"><span class="mt num">${hm}</span><span style="flex:1"><b dir="auto">${esc(name)}</b>${esc(dayName(day))}${m.note?' · '+esc(m.note):''}</span><button type="button" class="ac-x" data-delmeet="${esc(m.id)}" aria-label="${esc(T('group.meetup_delete'))}" style="min-width:44px;min-height:44px;background:none;border:0;color:var(--muted);cursor:pointer">${ic('x',18)}</button></div>`;}).join('');
    return (list||`<p class="ac-lead" style="margin:0">${esc(T('group.no_meetups'))}</p>`)+`<a class="ac-btn ghost" href="#meet">${ic('plus')}${esc(T('group.meetup_new'))}</a>`;}
  async function loadScores(){
    try{const out={};for(const game of GAMES)out[game]=await api('group_leaderboard',{group_id:gid,game});G.scores=out;try{localStorage.setItem(gKey(gid),JSON.stringify(G));}catch(e){}if(tab==='scores')draw();}
    catch(e){showErr($('groupPage'),e);}}
  function drawScores(){
    if(!G.scores)return `<p class="ac-lead" style="margin:0">${esc(T('acct.loading'))}</p>`;
    const boards=GAMES.filter(k=>(G.scores[k]||[]).length).map(k=>`<div class="ac-board"><h3>${esc(T('games.'+k+'_name'))}</h3><ol>${G.scores[k].map(s=>`<li${s.user_id===state.uid?' class="me"':''}><span>${esc(s.display_name)}</span><b class="num">${Number(s.best).toLocaleString('en-US')}</b></li>`).join('')}</ol></div>`).join('');
    return (boards||`<p class="ac-lead" style="margin:0">${esc(T('group.scores_none'))}</p>`)+`<p class="ac-note">${esc(T('group.scores_note'))}</p>`;}
  // the members tab (W8, and Q10 in the app): requests and admin actions for admins (round 14)
  let adm={menu:'',panel:''},armed={};
  const arm=(k)=>{if(Date.now()-(armed[k]||0)<6000)return true;armed[k]=Date.now();return false;};
  const fld=(label,inner)=>`<label class="ac-fld"><span>${esc(T(label))}</span>${inner}</label>`;
  const AIR=['TLV','TBS','KUT'],airSel=(n,v)=>`<select name="${n}" class="ac-sel">${AIR.map(a=>`<option value="${a}"${a===v?' selected':''}>${a} · ${esc(T('ticket.city_'+a.toLowerCase()))}</option>`).join('')}</select>`;
  function tripForm(m){
    const t=G.trips.find(x=>x.id===m.trip_id)||{},hm=v=>v?String(v).slice(0,5):'';
    return `<form class="ac-card ac-form ac-sub" data-mtripform="${esc(m.user_id)}"><h3>${esc(T('group.trip_for',{name:m.display_name}))}</h3>
      <div class="ac-grid">${fld('trip.date',`<input type="date" name="od" required value="${esc(t.out_date||'')}">`)}${fld('trip.number',`<input type="text" name="of" dir="ltr" maxlength="12" autocapitalize="characters" value="${esc(t.out_flight||'')}">`)}</div>
      <div class="ac-grid">${fld('ticket.from',airSel('ofr',t.out_from||'TLV'))}${fld('ticket.to',airSel('oto',t.out_to||'TBS'))}</div>
      <div class="ac-grid">${fld('ticket.departs',`<input type="time" name="odp" value="${esc(hm(t.out_departs))}">`)}${fld('trip.back',`<input type="date" name="rd" value="${esc(t.ret_date||'')}">`)}</div>
      <p class="ac-lead" style="margin:0">${esc(T('group.trip_for_note'))}</p><button type="submit" class="ac-btn">${esc(T('acct.save'))}</button></form>`;}
  function drawMembers(){
    const me=G.members.find(m=>m.user_id===state.uid),admin=me&&me.role==='admin',reg=registered();
    const reqs=admin&&G.reqs.length?`<div class="ac-sec-box"><h2>${esc(T('group.requests'))}</h2>${G.reqs.map(r=>{const was=r.kind==='reclaim'&&G.members.find(m=>m.user_id===r.reclaim_user_id);
      return `<div class="ac-row"><span class="ac-txt"><b>${esc(r.display_name||'')}</b>${was?`<small>${esc(T('group.reclaim_req',{name:was.display_name}))}</small>`:''}</span><button type="button" class="ac-link" data-req="${esc(r.id)}" data-ok="1">${esc(T('group.approve'))}</button><button type="button" class="ac-link" data-req="${esc(r.id)}" data-ok="0" style="color:var(--muted)">${esc(T('group.reject'))}</button></div>`;}).join('')}</div>`:'';
    const list=G.members.map(m=>{const mine=m.user_id===state.uid,open=adm.menu===m.user_id,t=G.trips.find(x=>x.id===m.trip_id),own=t&&(!t.entered_by||t.entered_by===m.user_id);
      const row=`<div class="ac-row"><span class="ac-av sm" style="background:var(--ink)">${esc(letter(m.display_name))}</span><span class="ac-txt"><b>${esc(m.display_name)}</b><small>${[m.role==='admin'?T('group.admin'):'',mine?T('group.you'):''].filter(Boolean).map(esc).join(' · ')}</small></span>`
        +(admin&&!mine?`<button type="button" class="ac-more" data-mmenu="${esc(m.user_id)}" aria-expanded="${open}" aria-label="${esc(T('group.member_menu',{name:m.display_name}))}">⋮</button>`:'')+`</div>`;
      const menu=open?`<div class="ac-menu"><button type="button" data-role="${m.role==='admin'?'member':'admin'}" data-uid="${esc(m.user_id)}">${esc(T(m.role==='admin'?'group.make_member':'group.make_admin'))}</button>`
        +(own?'':`<button type="button" data-mtrip="${esc(m.user_id)}">${esc(T('group.fill_trip'))}</button>`)
        +`<button type="button" class="red" data-remove="${esc(m.user_id)}">${esc(armed['rm'+m.user_id]&&Date.now()-armed['rm'+m.user_id]<6000?T('group.remove_confirm',{name:m.display_name}):T('group.remove'))}</button></div>`:'';
      return row+menu+(adm.panel==='trip:'+m.user_id?tripForm(m):'');}).join('');
    const claim=!G.members.some(m=>m.role==='admin')&&reg?`<button type="button" class="ac-btn ghost" data-claim>${esc(T('group.claim_admin'))}</button>`:'';
    let tools='';
    if(admin){const g=G.g,v=G.invite;
      const name=adm.panel==='name'?`<form class="ac-card ac-form ac-sub" data-nameform>${fld('group.create_name',`<input type="text" name="n" maxlength="60" required value="${esc(g.name)}">`)}
        <div class="ac-grid">${fld('group.starts',`<input type="date" name="s" value="${esc(g.starts_on||'')}">`)}${fld('group.ends',`<input type="date" name="e" value="${esc(g.ends_on||'')}">`)}</div><button type="submit" class="ac-btn">${esc(T('acct.save'))}</button></form>`:'';
      const inv=adm.panel==='invite'?`<div class="ac-card ac-form ac-sub"><label class="ac-check"><input type="checkbox" data-approval${v&&v.requires_approval?' checked':''}><span>${esc(T('group.approval'))}</span></label>
        <button type="button" class="ac-btn" data-newinvite>${esc(T('group.new_invite'))}</button><p class="ac-lead" style="margin:0">${esc(T('group.new_invite_note'))}</p>
        ${v?`<button type="button" class="ac-btn quiet" data-revoke="${esc(v.id)}">${esc(T('group.revoke'))}</button>`:''}</div>`:'';
      tools=`<div class="ac-sec-box"><h2>${esc(T('group.admin_tools'))}</h2>
        <button type="button" class="ac-row ac-rowbtn" data-panel="name" aria-expanded="${adm.panel==='name'}"><span class="ac-txt"><b>${esc(T('group.edit_details'))}</b><small>${esc(g.name)}${g.starts_on?' · '+esc(T('join.dates',{from:dm(g.starts_on),to:dm(g.ends_on||g.starts_on)})):''}</small></span></button>${name}
        <button type="button" class="ac-row ac-rowbtn" data-panel="invite" aria-expanded="${adm.panel==='invite'}"><span class="ac-txt"><b>${esc(T('group.invite_settings'))}</b><small>${v?esc(v.code)+(v.requires_approval?' · '+esc(T('group.approval')):''):esc(T('group.no_invite'))}</small></span></button>${inv}
        <button type="button" class="ac-row ac-rowbtn red" data-delgroup><span class="ac-txt"><b>${esc(armed.del&&Date.now()-armed.del<6000?T('group.delete_confirm'):T('group.delete'))}</b></span></button></div>`;}
    return reqs+`<div>${list}</div>`+claim+tools+`<button type="button" class="ac-btn danger" data-leave>${ic('out')}${esc(T('group.leave'))}</button>`;}
  function drawInvite(){
    const v=G.invite;if(!v){const me=G.members.find(m=>m.user_id===state.uid);return me&&me.role==='admin'?`<p class="ac-note">${esc(T('group.no_invite'))}</p>`:'';}
    const link=location.origin+'/j/'+v.token,msg=T('group.invite_msg',{name:G.g.name,link,code:v.code});
    return `<div class="ac-invite"><span><small style="display:block;font-size:12px;color:var(--muted);font-weight:600">${esc(T('group.invite_code'))}</small><b dir="ltr">${esc(v.code)}</b></span>
      <span class="ac-iv"><a href="https://wa.me/?text=${encodeURIComponent(msg)}" target="_blank" rel="noopener">${esc(T('group.invite_wa'))}</a><button type="button" data-copy="${esc(link)}">${esc(T('group.copy'))}</button></span></div>`;}
  let leaveArm=0;
  $('groupPage').addEventListener('click',async e=>{
    const pg=$('groupPage'),t=e.target;
    const run=async(fn)=>{clearErr(pg);try{await fn();await refresh();await reload();}catch(x){showErr(pg,x);}};
    const same=t.closest('[data-same]');if(same){run(async()=>{const mt=MYTRIP.get(),r=await api('same_flight',{group_id:gid,trip_id:same.dataset.same,my_trip_id:mt&&mt.sid||undefined});await setMine(r.trip_id);
      const c=await client(),[n]=await rest(c.from('trips').select('*').eq('id',r.trip_id));if(n)asMine(n);});return;}
    if(t.closest('[data-showtrip]')){run(()=>pushTrip(true));return;}
    const dm_=t.closest('[data-delmeet]');if(dm_){run(async()=>{const c=await client();await rest(c.from('meetups').delete().eq('id',dm_.dataset.delmeet));});return;}
    const rq=t.closest('[data-req]');if(rq){run(()=>api('decide_join_request',{request_id:rq.dataset.req,approve:rq.dataset.ok==='1'}));return;}
    const cp=t.closest('[data-copy]');if(cp&&navigator.clipboard){navigator.clipboard.writeText(cp.dataset.copy).then(()=>{cp.textContent=T('common.link_copied');setTimeout(()=>{cp.textContent=T('group.copy');},2200);}).catch(()=>{});return;}
    const mm=t.closest('[data-mmenu]');if(mm){adm.menu=adm.menu===mm.dataset.mmenu?'':mm.dataset.mmenu;if(!adm.menu)adm.panel='';draw();return;}
    const pn=t.closest('[data-panel]');if(pn){adm.panel=adm.panel===pn.dataset.panel?'':pn.dataset.panel;draw();return;}
    const mt=t.closest('[data-mtrip]');if(mt){adm.panel=adm.panel==='trip:'+mt.dataset.mtrip?'':'trip:'+mt.dataset.mtrip;draw();return;}
    const ro=t.closest('[data-role]');if(ro){run(()=>api('set_member_role',{group_id:gid,user_id:ro.dataset.uid,role:ro.dataset.role}));adm.menu='';return;}
    const rm=t.closest('[data-remove]');if(rm){if(!arm('rm'+rm.dataset.remove)){draw();return;}run(()=>api('remove_member',{group_id:gid,user_id:rm.dataset.remove}));adm.menu='';return;}
    if(t.closest('[data-claim]')){run(()=>api('claim_admin',{group_id:gid}));return;}
    if(t.closest('[data-newinvite]')){const ap=pg.querySelector('[data-approval]');run(()=>api('create_invite',{group_id:gid,requires_approval:!!(ap&&ap.checked)}));return;}
    const rv=t.closest('[data-revoke]');if(rv){run(()=>api('revoke_invite',{invite_id:rv.dataset.revoke}));return;}
    if(t.closest('[data-delgroup]')){if(!arm('del')){draw();return;}clearErr(pg);try{await api('delete_group',{group_id:gid});try{localStorage.removeItem(gKey(gid));}catch(x){}G=null;adm={menu:'',panel:''};await refresh();location.hash='#group';}catch(x){showErr(pg,x);}return;}
    const lv=t.closest('[data-leave]');if(lv){if(Date.now()-leaveArm>6000){leaveArm=Date.now();lv.lastChild.textContent=T('group.leave_confirm');return;}
      clearErr(pg);try{await api('leave_group',{group_id:gid});tr('group_leave');try{localStorage.removeItem(gKey(gid));}catch(x){}G=null;await refresh();location.hash='#group';}catch(x){showErr(pg,x);}}});
  $('groupPage').addEventListener('submit',async e=>{const f=e.target,pg=$('groupPage');
    const done=async fn=>{e.preventDefault();clearErr(pg);const b=f.querySelector('[type=submit]');if(b)b.disabled=true;try{await fn();adm={menu:'',panel:''};await refresh();await reload();}catch(x){showErr(pg,x);if(b)b.disabled=false;}};
    if(f.matches('[data-nameform]'))return done(()=>api('update_group',{group_id:gid,name:f.n.value.trim(),starts_on:f.s.value||null,ends_on:f.e.value||null}));
    if(f.matches('[data-mtripform]')){const ret=f.rd.value||null;
      return done(()=>api('set_member_trip',{group_id:gid,user_id:f.dataset.mtripform,trip:{out_date:f.od.value,out_flight:f.of.value.trim().toUpperCase()||null,out_from:f.ofr.value,out_to:f.oto.value,out_departs:f.odp.value||null,
        ret_date:ret,ret_from:ret?f.oto.value:null,ret_to:ret?f.ofr.value:null}}));}});
  $('grCode').addEventListener('submit',e=>{e.preventDefault();let c=e.target.code.value.replace(/[\s-]/g,'');if(/^[a-z]{6}$/i.test(c))c=c.toUpperCase();if(c)location.hash='#join/'+encodeURIComponent(c);});
  $('grCreate').addEventListener('submit',async e=>{e.preventDefault();const f=e.target,pg=$('groupPage');clearErr(pg);
    const name=f.name.value.trim(),me=f.me.value.trim();if(!name){f.name.focus();return;}if(!me){f.me.focus();return;}
    const t=MYTRIP.get();f.querySelector('button').disabled=true;
    try{const sid=await pushTrip();const r=await api('create_group',{name,display_name:me,starts_on:t&&t.out.date||undefined,ends_on:t&&t.ret&&t.ret.date||undefined,trip_id:sid||undefined});
      tr('group_create');await refresh();location.hash='#group/'+r.group_id;}
    catch(x){showErr(pg,x);}f.querySelector('button').disabled=false;});

  // ---- your trip in the server, only once you are in a group (CONTRACT, "trips")
  function row(t){const o=t.out,r=t.ret,code=c=>/^[A-Z]{3}$/.test(c||'')?c:null,tm=v=>v||null;
    return {out_date:o.date,out_flight:o.flight||null,out_from:code(o.from),out_to:code(o.to),out_departs:tm(o.departs),out_arrives:tm(o.arrives),
      ret_date:r&&r.date||null,ret_flight:r&&r.flight||null,ret_from:r?code(o.to):null,ret_to:r?code(o.from):null,ret_departs:r?tm(r.departs):null,ret_arrives:r?tm(r.arrives):null,
      ski_from:t.ski&&t.ski.from||null,ski_to:t.ski&&t.ski.to||null};}
  async function setMine(tid){for(const g of state.groups||[])if(g.trip!==tid)await api('set_my_membership',{group_id:g.id,display_name:g.me,trip_id:tid});}
  async function pushTrip(force){
    if(!signedIn()||(!(state.groups||[]).length&&!force&&!registered()))return null;
    const t=MYTRIP.get(),c=await client();
    if(!t){if(state.groups)for(const g of state.groups)if(g.trip)await api('set_my_membership',{group_id:g.id,display_name:g.me,trip_id:null});return null;}
    let sid=t.sid;
    if(sid){const u=await rest(c.from('trips').update(row(t)).eq('id',sid).select('id'));if(!u.length)sid=null;}
    if(!sid){const [n]=await rest(c.from('trips').insert(row(t)).select('id'));sid=n.id;const t2=MYTRIP.get();if(t2){t2.sid=sid;MYTRIP.set(t2);}}
    await setMine(sid);return sid;}
  // a new browser after signing in: your trip comes back from the account
  async function pullTrip(c){
    const [t]=await rest(c.from('trips').select('*').eq('owner_id',state.uid).or(`entered_by.is.null,entered_by.eq.${state.uid}`).order('updated_at',{ascending:false}).limit(1));
    if(!t||!t.out_date||MYTRIP.get())return;
    asMine(t);}
  // a trip row from the server becomes the trip in this browser, linked to that row
  function asMine(t){const hm=v=>v?String(v).slice(0,5):'';
    MYTRIP.set({v:1,sid:t.id,out:{date:t.out_date,flight:t.out_flight||'',from:t.out_from||'',to:t.out_to||'',departs:hm(t.out_departs),arrives:hm(t.out_arrives)},
      ret:t.ret_date?{date:t.ret_date,flight:t.ret_flight||'',departs:hm(t.ret_departs),arrives:hm(t.ret_arrives)}:null,ski:t.ski_from&&t.ski_to?{from:t.ski_from,to:t.ski_to}:null});
    renderTicket();countdown(document.documentElement.dataset.theme==='dark');}
  // your high scores from the games on this site (js/telemetry.js keeps them), sent once each; the server keeps the best
  async function sendBests(){let b={},sent={};try{b=JSON.parse(localStorage.getItem('gud-best')||'{}');sent=JSON.parse(localStorage.getItem('gud-best-sent')||'{}');}catch(e){}
    for(const g of GAMES){if(!(b[g]>(sent[g]||0)))continue;try{await api('submit_score',{game:g,score:b[g]});sent[g]=b[g];}catch(e){break;}}
    try{localStorage.setItem('gud-best-sent',JSON.stringify(sent));}catch(e){}}
  function tripSaved(){if(signedIn())pushTrip().then(()=>refresh()).catch(()=>{});}

  // ---- a meetup from the meeting point page goes to the group
  window.MEET_GROUP=()=>{const b=$('meetGroup');if(b)b.hidden=!(signedIn()&&(state.groups||[]).length);};
  async function saveMeet(b,g){const s=MEET.current();if(!s)return;b.disabled=true;$('meetGroupPick')?.remove();
    try{const c=await client();await rest(c.from('meetups').insert({group_id:g.id,station:s.sid,meet_at:`${s.day}T${s.time}:00+04:00`}));
      tr('meetup_create',{reminder:false});b.textContent=T('meet.saved_group');setTimeout(()=>{b.textContent=T('meet.save_group');b.disabled=false;},2500);}
    catch(x){b.disabled=false;b.textContent=T('acct.error');setTimeout(()=>{b.textContent=T('meet.save_group');},2500);}}
  document.addEventListener('click',e=>{const b=e.target.closest('#meetGroup'),pick=e.target.closest('[data-meetgroup]');
    const gs=state.groups||[];
    if(pick){saveMeet($('meetGroup'),gs.find(g=>g.id===pick.dataset.meetgroup));return;}
    if(!b||!MEET||!MEET.current())return;
    if(gs.length===1){saveMeet(b,gs[0]);return;}
    // several groups: which one (round 14)
    if($('meetGroupPick')){$('meetGroupPick').remove();return;}
    b.insertAdjacentHTML('afterend',`<div class="ac-pick" id="meetGroupPick" role="group" aria-label="${esc(T('meet.pick_group'))}"><small>${esc(T('meet.pick_group'))}</small>${gs.map(g=>`<button type="button" class="btn ghost" data-meetgroup="${esc(g.id)}">${esc(g.name)}</button>`).join('')}</div>`);});

  // a shared link to /j/<token>, /join/<code> or /account (Google Play's account deletion page) opens here
  (function(){const p=location.pathname,m=/^\/(?:j|join)\/([^/]+)\/?$/.exec(p);
    if(m)history.replaceState(null,'','/#join/'+m[1]);else if(/^\/account\/?$/.test(p))history.replaceState(null,'','/#account/delete');})();
  addEventListener('online',()=>{if(started&&signedIn())refresh();});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&gid&&!$('groupPage').hidden)reload();});
  let started=false;
  function start(x){({MYTRIP,esc,MEET,renderTicket,countdown}=x);started=true;paint();refresh();}
  const ifStarted=f=>(...a)=>started?f(...a):undefined;
  return {start,route:ifStarted(route),paint:ifStarted(paint),paintPass:ifStarted(paintPass),tripSaved:ifStarted(tripSaved),signedIn,state:()=>state};
})();
