/* The language row in about and settings, and its list (round 11, LP3; Pini chose it on 2.10.2026).
   Each language is written in its own script and title font, so anyone finds theirs even in the wrong language.
   Choosing saves it in this browser and reloads; "from the browser" forgets the choice (site/js/i18n.js). */
(function(){
  var NAMES={he:'עברית',en:'English',ru:'Русский',ka:'ქართული'};
  var row=document.getElementById('abLang'),sheet=document.getElementById('langSheet');
  if(!row||!sheet||!window.I18N)return;
  I18N.ready.then(function(){
    var val=document.getElementById('abLangVal');
    val.textContent=NAMES[I18N.lang];val.lang=I18N.lang;val.dir=I18N.lang==='he'?'rtl':'ltr';
    document.getElementById('abLangSub').textContent=T(I18N.chosen?'about.language_manual':'about.language_auto');
    if(I18N.lang==='en')row.querySelector('.ab-lang-en').hidden=true;
    var cur=sheet.querySelector('input[value="'+I18N.lang+'"]');if(cur)cur.checked=true;
  });
  var fonts=false;
  row.addEventListener('click',function(){
    // the other scripts' title fonts, only when the list opens
    if(!fonts){fonts=true;var l=document.createElement('link');l.rel='stylesheet';
      l.href='https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=Oswald:wght@600&family=Noto+Sans+Georgian:wdth,wght@62.5,800&display=swap';document.head.appendChild(l);}
    if(sheet.showModal)sheet.showModal();else sheet.setAttribute('open','');
  });
  sheet.addEventListener('change',function(e){if(e.target.name==='lang'&&e.target.value!==I18N.lang)I18N.set(e.target.value);});
  document.getElementById('langAuto').addEventListener('click',function(){I18N.set(null);});
  // a tap on the dimmed page closes the list
  sheet.addEventListener('click',function(e){if(e.target===sheet)sheet.close();});
})();
