/* Usage statistics (PostHog) and error reports (Sentry) for the site, by the contract in docs/GROWTH.md
   ("מדידת שימוש"): the same event names and properties as the apps. Decision 37: a random id kept in this browser
   (localStorage, no cookies), no identify, no autocapture, no recordings or heatmaps, servers in the EU, IP not kept.
   The switch in settings (prefs "analytics", on unless turned off) stops everything: with it off nothing is loaded.
   Code calls track('event', {props}); it does nothing when the switch is off or the scripts did not load. */
(function(){
  var POSTHOG_KEY='phc_njMEqbLpeiRxvqRxpop9m8pP4xwb6PHnqjuvaTxu9Ch3'; // public by design: it can only send events
  var SENTRY_DSN=''; // the site's Sentry project; empty = no error reports
  var P=window.GUD_PREFS||{};
  var on=P.analytics!==false&&!/^(localhost|127\.)/.test(location.hostname)&&!navigator.webdriver;
  var queue=[],ph=null;
  window.track=function(name,props){if(!on)return;if(ph)ph.capture(name,props||{});else if(queue.length<50)queue.push([name,props||{}]);};
  window.GUD_TELEMETRY={get on(){return on;}};
  // the switch in settings: its subtitle follows the state; turning it off stops sending at once (prefs.js keeps it)
  document.addEventListener('DOMContentLoaded',function(){
    var b=document.querySelector('[data-pref="analytics"]'),txt=document.getElementById('abAnaTxt');if(!b||!txt)return;
    var paint=function(){var v=(window.GUD_PREFS||{}).analytics!==false;txt.textContent=window.T?T(v?'about.analytics_on':'about.analytics_off'):txt.textContent;};
    (window.I18N?I18N.ready:Promise.resolve()).then(paint);
    b.addEventListener('click',function(){setTimeout(function(){paint();var v=(window.GUD_PREFS||{}).analytics!==false;
      if(!v&&window.posthog&&posthog.opt_out_capturing)posthog.opt_out_capturing();if(!v){on=false;}if(!v&&window.Sentry&&Sentry.close)Sentry.close();},0);});
  });
  if(!on)return;

  function deviceClass(){var w=Math.min(screen.width,screen.height);return w<600?'phone':w<1000?'tablet':'desktop';}
  function theme(){try{return localStorage.getItem('gud-daynight')||'auto';}catch(e){return 'auto';}}
  function load(src,cb){var s=document.createElement('script');s.src=src;s.async=true;s.crossOrigin='anonymous';s.onload=cb;document.head.appendChild(s);}

  load('https://eu-assets.i.posthog.com/static/array.js',function(){
    if(!window.posthog||!posthog.init)return;
    posthog.init(POSTHOG_KEY,{api_host:'https://eu.i.posthog.com',persistence:'localStorage',person_profiles:'identified_only',
      autocapture:false,capture_pageview:false,capture_pageleave:false,disable_session_recording:true,enable_heatmaps:false,
      disable_surveys:true,capture_performance:false,capture_dead_clicks:false,advanced_disable_flags:true,ip:false,
      loaded:function(p){
        if(p.has_opted_out_capturing&&p.has_opted_out_capturing())p.opt_in_capturing(); // the switch was turned back on
        var I=window.I18N||{};
        p.register({platform:'web',app_version:document.documentElement.getAttribute('data-version')||'web',build:'web',
          lang:I.lang||'he',lang_source:I.chosen?'manual':'auto',theme:theme(),device_class:deviceClass()});
        ph=p;queue.splice(0).forEach(function(e){p.capture(e[0],e[1]);});
      }});
  });
  // app_open once, screen_view on every page change (#home, #map, #meet, #games, #about; a game page names itself)
  var q=new URLSearchParams(location.search),game=(document.currentScript&&document.currentScript.getAttribute('data-game'))||'';
  track('app_open',{source:q.get('utm_medium')==='share'?'share_link':q.get('utm_source')?'link':'direct'});
  function screenView(){var h=(location.hash||'#home').slice(1).split('/')[0]||'home';
    track('screen_view',game?{screen:'game',game:game}:{screen:['home','map','meet','games','about'].indexOf(h)>=0?h:'home'});}
  screenView._h=(location.hash||'#home').slice(1).split('/')[0];screenView();if(!game)addEventListener('hashchange',function(){var last=screenView._h,h=(location.hash||'#home').slice(1).split('/')[0];if(h!==last){screenView._h=h;screenView();}});
  if(SENTRY_DSN)load('https://browser.sentry-cdn.com/8.38.0/bundle.min.js',function(){
    if(!window.Sentry)return;
    Sentry.init({dsn:SENTRY_DSN,sendDefaultPii:false,tracesSampleRate:0,environment:'web',
      beforeSend:function(ev){if(ev.request){delete ev.request.cookies;delete ev.request.headers;}if(ev.user)delete ev.user.ip_address;return ev;}});
  });
})();
