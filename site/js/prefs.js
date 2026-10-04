/* User settings shared by the site and the games (about and settings page, #about).
   Stored in this browser only. Sound off: every audio context stays silent. Vibration off: navigator.vibrate does nothing. */
(function(){
  var P={sound:true,haptics:true,analytics:true};
  try{var s=JSON.parse(localStorage.getItem('gud-prefs')||'{}');if(typeof s.sound==='boolean')P.sound=s.sound;if(typeof s.haptics==='boolean')P.haptics=s.haptics;if(typeof s.analytics==='boolean')P.analytics=s.analytics;}catch(e){}
  // both switches act at once, without a reload (S-14): vibrate and every audio context ask the setting each time
  if(navigator.vibrate){var vib=navigator.vibrate.bind(navigator);try{navigator.vibrate=function(){return P.haptics?vib.apply(null,arguments):false;};}catch(e){}}
  var ctxs=[];
  ['AudioContext','webkitAudioContext'].forEach(function(n){var O=window[n];if(!O)return;
    var Q=function(a){var c=a===undefined?new O():new O(a),go=c.resume.bind(c);ctxs.push(c);
      c.resume=function(){return P.sound?go():Promise.resolve();};if(!P.sound){try{c.suspend();}catch(e){}}return c;};
    Q.prototype=O.prototype;window[n]=Q;});
  window.GUD_PREFS=P;
  window.setGudPref=function(k,v){P[k]=v;try{localStorage.setItem('gud-prefs',JSON.stringify(P));}catch(e){}
    if(k==='sound')ctxs.forEach(function(c){try{v?c.resume():c.suspend();}catch(e){}});};
  // the records of the games, so they can be cleared from the settings
  window.GUD_GAME_KEYS=['school-','snw2-','ski-','lake-','mg-','scr2-','gud-best'];
})();
