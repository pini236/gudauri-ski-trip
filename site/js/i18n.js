/* Interface language of the site (decision 32): Hebrew, English, Russian, Georgian.
   The words come from i18n/strings.json, built into i18n/<lang>.json by tools/build-site-strings.py.
   Which language: ?lang=xx (saved in this browser; ?lang=auto forgets it), then the saved choice, then the browser.
   Hebrew only when Hebrew (he or iw) is among the browser languages; every other browser gets English (3.10.2026, Pini:
   Google Play reviewers and tourists open the pages in English; Russian and Georgian are chosen with the language button). Loaded in <head>, before the page draws, so lang and dir are right from the start. */
(function(){
  var LANGS={he:{dir:'rtl',loc:'he-IL'},en:{dir:'ltr',loc:'en-GB'},ru:{dir:'ltr',loc:'ru-RU'},ka:{dir:'ltr',loc:'ka-GE'}};
  // The four languages the site offers (decision 39: no native speaker review for now). The browser picks only Hebrew or
  // English; Russian and Georgian come from ?lang or the language button.
  var RELEASED=['he','en','ru','ka'];
  var KEY='gud-lang';
  // A game page loads its own file: <script src="../../js/i18n.js" data-base="../../i18n/" data-file="game-descent">.
  var me=document.currentScript,BASE=(me&&me.getAttribute('data-base'))||'i18n/',FILE=(me&&me.getAttribute('data-file'))||'';
  function saved(){try{return localStorage.getItem(KEY)||'';}catch(e){return '';}}
  function save(v){try{if(v)localStorage.setItem(KEY,v);else localStorage.removeItem(KEY);}catch(e){}}
  function fromBrowser(){
    var list=(navigator.languages&&navigator.languages.length?navigator.languages:[navigator.language||'']).map(function(l){return String(l).toLowerCase().split('-')[0];});
    return list.indexOf('he')>=0||list.indexOf('iw')>=0?'he':'en';
  }
  var q='';try{q=(new URLSearchParams(location.search).get('lang')||'').toLowerCase();}catch(e){}
  if(q==='auto')save('');else if(LANGS[q])save(q);
  var s=saved(),lang=LANGS[q]?q:LANGS[s]?s:fromBrowser();
  var meta=LANGS[lang],root=document.documentElement;
  root.lang=lang;root.dir=meta.dir;
  if(lang!=='he')root.classList.add('i18n-wait');
  // Fonts for the other scripts (round 10, FT1): titles in Oswald (Russian) or Noto Sans Georgian; text in IBM Plex Sans.
  var FONTS={en:'IBM+Plex+Sans:wght@400;500;600;700',ru:'Oswald:wght@500;600;700&family=IBM+Plex+Sans:wght@400;500;600;700',ka:'Noto+Sans+Georgian:wdth,wght@62.5..100,400..800'};
  if(FONTS[lang]){var fl=document.createElement('link');fl.rel='stylesheet';fl.href='https://fonts.googleapis.com/css2?family='+FONTS[lang]+'&display=swap';document.head.appendChild(fl);}

  var S={},PR=null;
  try{PR=new Intl.PluralRules(meta.loc);}catch(e){}
  function fill(str,vars){return vars?String(str).replace(/\{(\w+)\}/g,function(m,k){return k in vars?vars[k]:m;}):String(str);}
  function t(key,vars){
    var v=S[key];
    if(v==null)return key;
    if(typeof v==='object'){var n=vars&&vars.n!=null?Number(vars.n):NaN,c=PR&&!isNaN(n)?PR.select(n):'other';v=v[c]!=null?v[c]:v.other;}
    return fill(v,vars);
  }
  // [data-i18n]: text; [data-i18n-html]: our own markup from the strings file; [data-i18n-attr="aria-label:key;alt:key"].
  function apply(rootEl){
    rootEl=rootEl||document;
    rootEl.querySelectorAll('[data-i18n]').forEach(function(el){el.textContent=t(el.getAttribute('data-i18n'));});
    rootEl.querySelectorAll('[data-i18n-html]').forEach(function(el){el.innerHTML=t(el.getAttribute('data-i18n-html'));});
    rootEl.querySelectorAll('[data-i18n-attr]').forEach(function(el){
      el.getAttribute('data-i18n-attr').split(';').forEach(function(p){var i=p.indexOf(':');if(i>0)el.setAttribute(p.slice(0,i).trim(),t(p.slice(i+1).trim()));});
    });
  }
  function load(l){return fetch(BASE+(FILE?FILE+'.':'')+l+'.json').then(function(r){if(!r.ok)throw new Error('i18n '+l+' '+r.status);return r.json();});}
  var dom=new Promise(function(ok){if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ok,{once:true});else ok();});
  // Resolves after the page's own words are in place, so app.js can build on top of them.
  var ready=Promise.all([load(lang).catch(function(){return lang==='he'?{strings:{}}:load('he');}).catch(function(){return {strings:{}};}),dom]).then(function(r){
    S=r[0].strings||{};
    if(lang!=='he'){apply(document);if(!FILE)document.title=t('meta.title');}
    root.classList.remove('i18n-wait');
    return r[0];
  });
  // set('ru') keeps a choice in this browser; set(null) goes back to the browser's language. The page reloads.
  function set(v){save(v&&LANGS[v]?v:'');var u=new URL(location.href);if(u.searchParams.has('lang')){u.searchParams.delete('lang');location.replace(u.toString());}else location.reload();}
  // Days and months in Georgian, for a browser that has no Georgian dates (Chrome here falls back to its own language and
  // wrote Hebrew month names on the Georgian page). Only what the site asks for: weekday, day, month, year.
  var KA_WD=['კვი','ორშ','სამ','ოთხ','ხუთ','პარ','შაბ'],KA_MO=['იან','თებ','მარ','აპრ','მაი','ივნ','ივლ','აგვ','სექ','ოქტ','ნოე','დეკ'],kaOk=false;
  try{kaOk=new Intl.DateTimeFormat('ka',{month:'short'}).resolvedOptions().locale.slice(0,2)==='ka';}catch(e){}
  function date(d,o){d=new Date(d);
    if(lang==='ka'&&!kaOk&&o&&(o.weekday||o.month)){var u=o.timeZone==='UTC',wd=u?d.getUTCDay():d.getDay(),dd=u?d.getUTCDate():d.getDate(),mo=u?d.getUTCMonth():d.getMonth(),yy=u?d.getUTCFullYear():d.getFullYear();
      var dm=o.day||o.month?(o.day?dd+' ':'')+(o.month?KA_MO[mo]:'')+(o.year?' '+yy:''):'';
      return (o.weekday?KA_WD[wd]+(dm?', ':''):'')+dm.trim();}
    return d.toLocaleDateString(meta.loc,o);}
  window.I18N={lang:lang,dir:meta.dir,locale:meta.loc,ltr:meta.dir==='ltr',released:RELEASED.slice(),t:t,apply:apply,ready:ready,set:set,
    chosen:!!(LANGS[q]||LANGS[s]),date:date};
  window.T=t;
  // For the games: L('key','עברית',vars) keeps the Hebrew next to the key, and falls back to it when a key is missing.
  window.L=function(key,he,vars){var v=S[key];return v==null?fill(he,vars):t(key,vars);};
})();
