/* /app, the short link to the app (round 28, decision 75). Android and computers go straight on to Google Play (on a
   computer, Play installs to the phone from the account); an iPhone or iPad, with no App Store app yet, stays and is
   offered the map on the site. The Play link carries where the share came from: /app?s=whatsapp becomes
   utm_source=whatsapp (default: share), utm_medium=app_link, so installs from a shared link count apart from the
   home page sign (home_board, home_qr). Without scripts the page still shows the official badge and the site link. */
(function(){
  var PLAY='https://play.google.com/store/apps/details?id=io.github.pini236.skiapp';
  var src='share';
  try{src=(new URLSearchParams(location.search).get('s')||'').replace(/[^A-Za-z0-9_-]/g,'').slice(0,32)||'share';}catch(e){}
  var url=PLAY+'&referrer='+encodeURIComponent('utm_source='+src+'&utm_medium=app_link');
  var ua=navigator.userAgent||'';
  var ios=/iPhone|iPad|iPod/.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  function $(id){return document.getElementById(id);}
  var lang=document.documentElement.lang;
  $('alBadge').href=url;
  if(['he','en','ru','ka'].indexOf(lang)>=0)$('alBadgeImg').src='img/badges/play-'+lang+'.png';
  // i18n.js names the page after the site in the other languages; this page keeps the store name
  if(window.I18N)I18N.ready.then(function(){if(lang!=='he')document.title=T('applink.title');});
  if(ios){
    ['alBadge','alSite'].forEach(function(i){$(i).hidden=true;});
    ['alIos','alBtn'].forEach(function(i){$(i).hidden=false;});
    return;
  }
  $('alGoing').hidden=false;
  location.replace(url);
})();
