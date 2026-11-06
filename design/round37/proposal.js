// Round 37: the iPhone app sign (App Store) next to the Google Play sign on the home page. Drawn over the real site (design/round37/shots.mjs).
// Not product code: the canvas shows it, the site session implements it after Pini approves. The Apple badge is a labelled placeholder (the official one is not in the repo).
(function(){
  const S={
    he:{android:'אפליקציה לאנדרואיד',play:'ב-<bdi>Google Play</bdi>',ios:'אפליקציה לאייפון',store:'ב-<bdi>App Store</bdi>',scanA:'סורקים בטלפון אנדרואיד',scanI:'סורקים באייפון'},
    en:{android:'Android app',play:'on Google Play',ios:'iPhone app',store:'on the App Store',scanA:'Scan with an Android phone',scanI:'Scan with an iPhone'}};
  const ext='<svg class="r37-ext" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M5 2H2v8h8V7M7 2h3v3M10 2 5.5 6.5"/></svg>';
  const apple='<span class="r37-apple" title="מקום שמור לתג הרשמי של אפל"><i>Download on the</i><b>App Store</b></span>';
  function css(){
    if(document.getElementById('r37-css'))return;
    const s=document.createElement('style');s.id='r37-css';s.textContent=`
      .r37-ext{display:inline-block;width:12px;height:12px;margin-inline-start:6px;vertical-align:-1px}
      .post .board-app.r37{width:80%;box-sizing:border-box}
      .board-app .r37-sub{display:flex;align-items:center;font-size:13px;opacity:.92}
      .r37-two{display:flex;flex-wrap:wrap;gap:22px 28px;width:92%;box-sizing:border-box;padding:14px 16px;background:var(--paper);border:1.5px solid var(--rule);border-top:4px solid var(--ink);box-shadow:0 2px 8px var(--shadow)}
      .r37-col{display:flex;flex-direction:column;gap:10px;align-items:flex-start;font-size:14px;color:var(--muted)}
      .r37-col svg{width:112px;height:112px;padding:6px;background:#fff;box-sizing:border-box}
      .r37-col b{font:600 14.5px var(--f-body);color:var(--ink)}
      .r37-col img{height:44px;width:auto;margin-inline-start:-5px;display:block}
      .r37-apple{height:44px;box-sizing:border-box;padding:0 14px;display:flex;flex-direction:column;justify-content:center;background:#000;color:#fff;border-radius:8px;outline:1px dashed #9aa6b5;outline-offset:2px;font-family:-apple-system,"Helvetica Neue",Arial,sans-serif;direction:ltr;text-align:left;line-height:1}
      .r37-apple i{font:normal 9px/1 -apple-system,Arial,sans-serif}
      .r37-apple b{font:600 19px/1.05 -apple-system,Arial,sans-serif;color:#fff}`;document.head.appendChild(s);}
  function qr(q){return `<svg viewBox="0 0 ${q.n} ${q.n}" shape-rendering="crispEdges" aria-label="QR"><path fill="#13233A" d="${q.d}"/></svg>`;}
  window.R37={apply(o){
    css();const L=S[o.lang||'he'],b=document.getElementById('appBoard'),post=b.parentNode;
    const h=document.getElementById('appHow');if(h)h.hidden=true;const q=document.getElementById('appQr');if(q)q.hidden=true;
    post.appendChild(b);b.hidden=false;b.removeAttribute('target');b.href='#';b.classList.add('r37');
    b.innerHTML=`<b>${L.android}</b><span class="r37-sub">${L.play}${ext}</span>`;
    const i=b.cloneNode(true);i.removeAttribute('id');i.classList.add('r37');i.innerHTML=`<b>${L.ios}</b><span class="r37-sub">${L.store}${ext}</span>`;post.insertBefore(i,b.nextSibling);
    if(o.mode==='ios')b.hidden=true;
    if(o.mode==='android')i.hidden=true;
    if(o.mode==='desk'){const c=document.createElement('div');c.className='r37-two';
      c.innerHTML=`<div class="r37-col">${qr(o.qrPlay)}<b>${L.scanA}</b><img alt="Google Play" src="${o.badge[o.lang||'he']}"></div><div class="r37-col">${qr(o.qrIos)}<b>${L.scanI}</b>${apple}</div>`;
      post.insertBefore(c,i.nextSibling);}
    return o.mode;}};
})();
