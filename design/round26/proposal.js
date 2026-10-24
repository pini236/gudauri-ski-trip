// Round 26: the "Android app" sign on the home page links to Google Play. Drawn over the real site (design/round26/shots.mjs).
// Not product code: the canvas shows it, the site session implements it after Pini approves.
(function(){
  const S={
    he:{board:'אפליקציה לאנדרואיד',store:'ב-<bdi>Google Play</bdi>',ios:'אפליקציה לאייפון',iosSub:'ב-<bdi>App Store</bdi>',scan:'סורקים בטלפון אנדרואיד',or:'או',soon:'<bdi>App Store</bdi>, בהמשך'},
    en:{board:'Android app',store:'on Google Play',ios:'iPhone app',iosSub:'on the App Store',scan:'Scan with an Android phone',or:'or',soon:'App Store, later'}};
  function css(){
    if(document.getElementById('r26-css'))return;
    const s=document.createElement('style');s.id='r26-css';s.textContent=`
      .r26-ext{display:inline-block;width:12px;height:12px;margin-inline-start:6px;vertical-align:-1px}
      .board-app .r26-sub{display:flex;align-items:center;font-size:13px;opacity:.92}
      .r26-badge{display:block;width:92%;box-sizing:border-box;padding:0}
      .r26-badge img{display:block;height:64px;width:auto;margin-inline-start:-6px}
      .r26-qr{display:flex;gap:16px;align-items:center;width:92%;box-sizing:border-box;padding:14px 16px;background:var(--paper);border:1.5px solid var(--rule);border-top:4px solid var(--ink);box-shadow:0 2px 8px var(--shadow)}
      .r26-qr svg{flex:none;width:112px;height:112px;padding:6px;background:#fff}
      .r26-qr .t{display:flex;flex-direction:column;gap:10px;font-size:14px;color:var(--muted)}
      .r26-qr .t b{font:600 15px var(--f-body);color:var(--ink)}
      .r26-qr .t img{height:44px;width:auto;margin-inline-start:-5px;display:block}
      .r26-qr .badges{display:flex;gap:10px;align-items:center}
      .r26-qr .soon{height:44px;padding:0 10px;display:flex;align-items:center;border:1.5px dashed var(--rule);font-size:12px;color:var(--muted);white-space:nowrap}
      .board-ios{background:var(--ink);color:var(--paper);width:80%;box-sizing:border-box}
      .post .board-app.r26{width:80%}
      .board-ios b{font-size:30px}`;document.head.appendChild(s);}
  const ext='<svg class="r26-ext" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M5 2H2v8h8V7M7 2h3v3M10 2 5.5 6.5"/></svg>';
  function qr(q){return `<svg viewBox="0 0 ${q.n} ${q.n}" shape-rendering="crispEdges" aria-label="QR"><path fill="#13233A" d="${q.d}"/></svg>`;}
  window.R26={apply(o){
    css();const L=S[o.lang||'he'],b=document.getElementById('appBoard'),how=document.getElementById('appHow'),post=b.parentNode;
    if(o.mode==='today'){b.hidden=false;how.hidden=false;how.open=!!o.open;document.getElementById('appMeta').textContent=o.lang==='en'?'Download (8.5 MB)':'להורדה (8.5 MB)';return 'today';}
    how.hidden=true;
    if(o.mode==='none'){b.hidden=true;return 'none';}
    if(o.mode==='badge'){b.hidden=true;const a=document.createElement('a');a.className='r26-badge';a.href='#';a.innerHTML=`<img alt="Google Play" src="${o.badge[o.lang||'he']}">`;post.appendChild(a);return 'badge';}
    // the sign, in our language: the store name is words, not Google's logo (their rules keep the logo for the official badge)
    if(o.mode!=='badge')post.appendChild(b);
    b.hidden=false;b.removeAttribute('download');b.href='#';
    b.classList.add('r26');b.innerHTML=`<b>${L.board}</b><span class="r26-sub">${L.store}${ext}</span>`;
    if(o.ios){const i=b.cloneNode(true);i.className='board board-ios';i.removeAttribute('id');i.innerHTML=`<b>${L.ios}</b><span class="r26-sub">${L.iosSub}${ext}</span>`;post.insertBefore(i,b.nextSibling);if(o.ios==='only')b.hidden=true;}
    if(o.mode==='desk'){const c=document.createElement('div');c.className='r26-qr';
      c.innerHTML=`${qr(o.qr)}<div class="t"><b>${L.scan}</b><span>${L.or}</span><div class="badges"><img alt="Google Play" src="${o.badge[o.lang||'he']}">${o.future?`<span class="soon">${L.soon}</span>`:''}</div></div>`;
      post.insertBefore(c,b.nextSibling);}
    return o.mode;}};
})();
