/* /app, the short link to the app (round 28, decision 75; round 37). Android goes straight on to Google Play, an iPhone
   or iPad to the App Store (no campaign parameters there: they need a provider token), and a computer stays and is
   shown both badges. The Play link carries where the share came from: /app?s=whatsapp becomes
   utm_source=whatsapp (default: share), utm_medium=app_link, so installs from a shared link count apart from the
   home page sign (home_board, home_qr). Without scripts the page still shows the official badge and the site link. */
(function(){
  var PLAY='https://play.google.com/store/apps/details?id=io.github.pini236.skiapp';
  var src='share';
  try{src=(new URLSearchParams(location.search).get('s')||'').replace(/[^A-Za-z0-9_-]/g,'').slice(0,32)||'share';}catch(e){}
  var url=PLAY+'&referrer='+encodeURIComponent('utm_source='+src+'&utm_medium=app_link');
  var ua=navigator.userAgent||'';
  var ios=/iPhone|iPad|iPod/.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  var android=/Android/.test(ua);
  var APPLE='https://apps.apple.com/app/gudi-gudauri-ski-map/id6825506333';
  function $(id){return document.getElementById(id);}
  var lang=document.documentElement.lang;
  $('alBadge').href=url;
  if(['he','en','ru','ka'].indexOf(lang)>=0)$('alBadgeImg').src='img/badges/play-'+lang+'.png';
  // i18n.js names the page after the site in the other languages; this page keeps the store name
  if(window.I18N)I18N.ready.then(function(){if(lang!=='he')document.title=T('applink.title');});
  // a phone goes straight to its own store; a computer stays and shows both badges (no automatic jump)
  if(ios||android){
    $(ios?'alBadge':'alBadgeIos').hidden=true;
    $('alSite').hidden=true;
    $('alGoing').hidden=false;
    $(ios?'alGoingPlay':'alGoingIos').hidden=true;
    location.replace(ios?APPLE:url);
  }
})();
