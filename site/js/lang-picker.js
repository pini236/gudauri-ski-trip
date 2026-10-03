/* The language button in the top bar of every page (3.10.2026, Pini: a fixed switch at the top; on desktop in the top bar, on
   phones in the page header), the language row in about and settings, and the one list they open (round 11, LP3).
   Each language is written in its own script and title font, so anyone finds theirs even in the wrong language.
   Choosing saves it in this browser and reloads; "from the browser" forgets the choice (site/js/i18n.js). */
(function(){
  var NAMES={he:'עברית',en:'English',ru:'Русский',ka:'ქართული'};
  var CODES={he:'עב',en:'EN',ru:'RU',ka:'KA'};
  var row=document.getElementById('abLang'),sheet=document.getElementById('langSheet');
  if(!sheet||!window.I18N)return;
  var fonts=false;
  function open(){
    // the other scripts' title fonts, only when the list opens
    if(!fonts){fonts=true;var l=document.createElement('link');l.rel='stylesheet';
      l.href='https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=Oswald:wght@600&family=Noto+Sans+Georgian:wdth,wght@62.5,800&display=swap';document.head.appendChild(l);}
    if(sheet.showModal)sheet.showModal();else sheet.setAttribute('open','');
  }
  // one button in each header: .in-top in the desktop top bar, .in-page in the header of a page on phones
  var GLOBE='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.6 2.7 3.9 5.7 3.9 9s-1.3 6.3-3.9 9c-2.6-2.7-3.9-5.7-3.9-9S9.4 5.7 12 3z"/></svg>';
  var btns=[];
  function add(host,before,cls){
    if(!host)return;
    var b=document.createElement('button');b.type='button';b.className='lang-btn '+cls;b.setAttribute('aria-haspopup','dialog');b.setAttribute('translate','no');
    b.innerHTML=GLOBE+'<b></b>';
    b.addEventListener('click',open);
    host.insertBefore(b,before||null);btns.push(b);
  }
  var top=document.querySelector('.topbar');
  if(top)add(top,top.querySelector('.dn'),'in-top');
  var ht=document.querySelector('.home-top');
  if(ht)add(ht,ht.querySelector('.dn-home'),'in-page');
  document.querySelectorAll('.mhead,.ac-head,.tf-head').forEach(function(h){add(h,h.querySelector('a'),'in-page');});
  I18N.ready.then(function(){
    btns.forEach(function(b){b.setAttribute('aria-label',T('about.language')+' · Language');b.querySelector('b').textContent=CODES[I18N.lang];});
    var cur=sheet.querySelector('input[value="'+I18N.lang+'"]');if(cur)cur.checked=true;
    if(!row)return;
    var val=document.getElementById('abLangVal');
    val.textContent=NAMES[I18N.lang];val.lang=I18N.lang;val.dir=I18N.lang==='he'?'rtl':'ltr';
    document.getElementById('abLangSub').textContent=T(I18N.chosen?'about.language_manual':'about.language_auto');
    if(I18N.lang==='en')row.querySelector('.ab-lang-en').hidden=true;
  });
  if(row)row.addEventListener('click',open);
  sheet.addEventListener('change',function(e){if(e.target.name==='lang'&&e.target.value!==I18N.lang){track('lang_set',{lang:e.target.value,previous:I18N.lang});I18N.set(e.target.value);}});
  document.getElementById('langAuto').addEventListener('click',function(){track('lang_set',{lang:'auto',previous:I18N.lang});I18N.set(null);});
  // a tap on the dimmed page closes the list
  sheet.addEventListener('click',function(e){if(e.target===sheet)sheet.close();});
})();
