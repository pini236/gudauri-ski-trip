/* Usage statistics (PostHog) and error reports (Sentry) for the site, by the contract in docs/GROWTH.md
   ("מדידת שימוש"): the same event names and properties as the apps. Decision 37: a random id kept in this browser
   (localStorage, no cookies), no identify, no autocapture, no recordings or heatmaps, servers in the EU, IP not kept.
   The switch in settings (prefs "analytics", on unless turned off) stops everything: with it off nothing is loaded.
   Code calls track('event', {props}); it does nothing when the switch is off or the scripts did not load. */
(function(){
  var POSTHOG_KEY='phc_njMEqbLpeiRxvqRxpop9m8pP4xwb6PHnqjuvaTxu9Ch3'; // public by design: it can only send events
  var SENTRY_DSN='https://9e0fede583a2521cfddd6efff76aa9aa@o4512183732404224.ingest.de.sentry.io/4512183998873680'; // gudi-web (EU); public by design
  var P=window.GUD_PREFS||{};
  var here=!/^(localhost|127\.)/.test(location.hostname)&&!navigator.webdriver,on=P.analytics!==false&&here,started=false;
  var queue=[],ph=null;
  // the games' high scores for the group's table (js/account.js sends them, only once you are in a group):
  // kept in this browser whatever the switch says, since nothing leaves the browser here. Ski school counts its stars
  // (the best of each lesson, added up); every other game its best score.
  function best(g,props){try{var k='gud-best',b=JSON.parse(localStorage.getItem(k)||'{}'),s=Math.max(0,Math.floor(Number(props.score)||0));
    if(g==='school'){var l=b.schoolLv||{};l[props.level]=Math.max(l[props.level]||0,s);b.schoolLv=l;s=Object.keys(l).reduce(function(a,x){return a+l[x];},0);}
    if(s>(b[g]||0)){b[g]=s;localStorage.setItem(k,JSON.stringify(b));return true;}}catch(e){}return false;}
  // "best" in game_end is a new record of the score (docs/GROWTH.md). Snowball fight and fresh snow report another
  // record of their own, so here it comes from the score kept above (S-16)
  var OWN_BEST={snowball:1,fresh:1};
  window.track=function(name,props){if(name==='game_end'&&props&&props.game){var nb=best(props.game,props);if(OWN_BEST[props.game])props.best=nb;}if(!on)return;if(ph&&name==='theme_set')ph.register({theme:theme()}); /* X-2 */if(ph)ph.capture(name,props||{});else if(queue.length<50)queue.push([name,props||{}]);};
  window.GUD_TELEMETRY={get on(){return on;}};
  // the switch in settings: its subtitle follows the state; turning it off stops sending at once (prefs.js keeps it)
  document.addEventListener('DOMContentLoaded',function(){
    var b=document.querySelector('[data-pref="analytics"]'),txt=document.getElementById('abAnaTxt');if(!b||!txt)return;
    var paint=function(){var v=(window.GUD_PREFS||{}).analytics!==false;txt.textContent=window.T?T(v?'about.analytics_on':'about.analytics_off'):txt.textContent;};
    (window.I18N?I18N.ready:Promise.resolve()).then(paint);
    // off: nothing more is sent, not even this change. On again: it starts now, without a reload, and says so
    b.addEventListener('click',function(){setTimeout(function(){paint();var v=(window.GUD_PREFS||{}).analytics!==false;
      if(!v){on=false;if(ph&&ph.opt_out_capturing)ph.opt_out_capturing();if(window.Sentry&&Sentry.close)Sentry.close();return;}
      if(!here||on)return;on=true;if(ph&&ph.opt_in_capturing)ph.opt_in_capturing();start();track('settings_change',{setting:'analytics',on:true});},0);});
  });

  function deviceClass(){var w=Math.min(screen.width,screen.height);return w<600?'phone':w<1000?'tablet':'desktop';}
  function theme(){try{return localStorage.getItem('gud-daynight')||'auto';}catch(e){return 'auto';}}
  function load(src,cb){var s=document.createElement('script');s.src=src;s.async=true;s.crossOrigin='anonymous';s.onload=cb;document.head.appendChild(s);}

  // addresses carry secrets: the invite code or link token (/join/<code>, /j/<token>, #join/...) and the group id
  // (#group/<id>). Nothing past the page itself leaves the browser: the hash keeps only its first part, the
  // path only the screen, and the query only utm_* (docs/GROWTH.md, what is never sent)
  function cleanUrl(u){if(typeof u!=='string'||!u)return u;try{var x=new URL(u,location.href),h=(x.hash||'').slice(1).split('/')[0],q=new URLSearchParams();
      x.searchParams.forEach(function(v,k){if(/^utm_/.test(k))q.set(k,v);});
      var p=/^\/(j|join)\//.test(x.pathname)?'/'+x.pathname.split('/')[1]:x.pathname;
      return x.origin+p+(q.toString()?'?'+q:'')+(h?'#'+h:'');}catch(e){return '';}}
  var URL_KEYS=['$current_url','$pathname','$referrer','$initial_current_url','$initial_referrer','$initial_pathname','$host'];
  function cleanProps(o){if(!o)return;URL_KEYS.forEach(function(k){if(typeof o[k]==='string'&&k!=='$host')o[k]=k==='$pathname'||k==='$initial_pathname'?cleanUrl(o[k]).replace(/^https?:\/\/[^/]+/,'').split(/[?#]/)[0]:cleanUrl(o[k]);});}
  window.GUD_CLEAN_URL=cleanUrl; // for the tests
  function sentry(){Sentry.init({dsn:SENTRY_DSN,sendDefaultPii:false,tracesSampleRate:0,environment:'web',
      beforeSend:function(ev){if(ev.request){delete ev.request.cookies;delete ev.request.headers;delete ev.request.query_string;if(ev.request.url)ev.request.url=cleanUrl(ev.request.url);}if(ev.user)delete ev.user.ip_address;
        (ev.breadcrumbs||[]).forEach(function(b){var d=b&&b.data;if(!d)return;['from','to','url'].forEach(function(k){if(typeof d[k]==='string')d[k]=cleanUrl(d[k]);});});
        return ev;}});}
  function start(){
  if(window.Sentry&&Sentry.init)sentry();else if(SENTRY_DSN)load('https://browser.sentry-cdn.com/8.38.0/bundle.min.js',function(){if(window.Sentry)sentry();});
  if(started)return;started=true;
  load('https://eu-assets.i.posthog.com/static/array.js',function(){
    if(!window.posthog||!posthog.init)return;
    posthog.init(POSTHOG_KEY,{api_host:'https://eu.i.posthog.com',persistence:'localStorage',person_profiles:'identified_only',
      autocapture:false,capture_pageview:false,capture_pageleave:false,disable_session_recording:true,enable_heatmaps:false,
      disable_surveys:true,capture_performance:false,capture_dead_clicks:false,advanced_disable_flags:true,ip:false,
      before_send:function(ev){if(ev){cleanProps(ev.properties);cleanProps(ev.$set_once);cleanProps(ev.$set);}return ev;},
      loaded:function(p){
        if(p.has_opted_out_capturing&&p.has_opted_out_capturing())p.opt_in_capturing(); // the switch was turned back on
        var I=window.I18N||{};
        p.register({platform:'web',app_version:document.documentElement.getAttribute('data-version')||'web',build:'web',
          lang:I.lang||'he',lang_source:I.chosen?'manual':'auto',theme:theme(),device_class:deviceClass()});
        ph=p;queue.splice(0).forEach(function(e){p.capture(e[0],e[1]);});
      }});
  });
  }
  if(on)start();
  // app_open once, screen_view on every page change (#home, #map, #meet, #games, #about, #trip; a game page names itself)
  // app_open once per visit (this tab): a game page or the privacy page opened from the site is not another open
  var cs=document.currentScript,q=new URLSearchParams(location.search),game=(cs&&cs.getAttribute('data-game'))||'',page=(cs&&cs.getAttribute('data-screen'))||'';
  var opened=false;try{opened=!!sessionStorage.getItem('gud-opened');sessionStorage.setItem('gud-opened','1');}catch(e){}
  if(!opened)track('app_open',{source:q.get('utm_medium')==='share'?'share_link':q.get('utm_source')?'link':'direct'});
  function screenView(){var h=(location.hash||'#home').slice(1).split('/')[0]||'home';
    track('screen_view',game?{screen:'game',game:game}:page?{screen:page}:{screen:['home','map','meet','games','about','trip','signin','account','join','group'].indexOf(h)>=0?h:'home'});}
  screenView._h=(location.hash||'#home').slice(1).split('/')[0];screenView();if(!game&&!page)addEventListener('hashchange',function(){var last=screenView._h,h=(location.hash||'#home').slice(1).split('/')[0];if(h!==last){screenView._h=h;screenView();}});
})();
