/* The privacy page (site/privacy.html, built by tools/build-privacy.py): which of the four languages shows.
   A file of its own, since the site's content security policy runs no script written inside a page (R-3). */
// all four languages are in the page; without script they all show, one after the other
(function(){
  var L=['he','en','ru','ka'];
  function show(l){
    L.forEach(function(x){document.getElementById(x).hidden=x!==l;document.querySelector('[data-back="'+x+'"]').hidden=x!==l;});
    document.documentElement.lang=l;document.documentElement.dir=l==='he'?'rtl':'ltr';
    document.querySelectorAll('[data-lang]').forEach(function(b){b.setAttribute('aria-pressed',String(b.dataset.lang===l));});
  }
  // like js/i18n.js: the language saved on the site, then Hebrew for a Hebrew browser, else English
  function chosen(){
    var h=location.hash.slice(1);if(L.indexOf(h)>=0)return h;
    var s='';try{s=localStorage.getItem('gud-lang')||'';}catch(e){}if(L.indexOf(s)>=0)return s;
    var list=(navigator.languages&&navigator.languages.length?navigator.languages:[navigator.language||'']).map(function(x){return String(x).toLowerCase().split('-')[0];});
    return list.indexOf('he')>=0||list.indexOf('iw')>=0?'he':'en';
  }
  document.querySelectorAll('[data-lang]').forEach(function(b){b.addEventListener('click',function(){
    show(b.dataset.lang);history.replaceState(null,'','#'+b.dataset.lang);window.scrollTo(0,0);});});
  show(chosen());window.scrollTo(0,0);
})();
