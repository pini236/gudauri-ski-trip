/* User settings shared by the site and the games (about and settings page, #about).
   Stored in this browser only. Sound off: every audio context stays silent. Vibration off: navigator.vibrate does nothing. */
(function(){
  var P={sound:true,haptics:true,analytics:true};
  try{var s=JSON.parse(localStorage.getItem('gud-prefs')||'{}');if(typeof s.sound==='boolean')P.sound=s.sound;if(typeof s.haptics==='boolean')P.haptics=s.haptics;if(typeof s.analytics==='boolean')P.analytics=s.analytics;}catch(e){}
  if(!P.haptics&&navigator.vibrate){try{navigator.vibrate=function(){return false;};}catch(e){}}
  ['AudioContext','webkitAudioContext'].forEach(function(n){var O=window[n];if(!O||P.sound)return;
    var Q=function(){var c=new O();try{c.suspend();}catch(e){}c.resume=function(){return Promise.resolve();};return c;};Q.prototype=O.prototype;window[n]=Q;});
  window.GUD_PREFS=P;
  window.setGudPref=function(k,v){P[k]=v;try{localStorage.setItem('gud-prefs',JSON.stringify(P));}catch(e){}};
  // the records of the games, so they can be cleared from the settings
  window.GUD_GAME_KEYS=['school-','snw2-','ski-','lake-','mg-','scr2-','gud-best'];
})();
