// Weather by altitude (round 19, decision 58; docs/ARCHITECTURE.md m-6, server/CONTRACT.md "/api/weather"). The server
// fetches the forecast and decides the closure risk; the clients only read it. One rule for the site and the app,
// tested against the shared answer and readings in tools/fixtures/weather-m6.json:
//   state: fresh up to 12 hours after updated, stale up to 48, then none; the values of the hour that has begun (an
//   hour not in the answer is none); between the points by elevation, linear, the nearest point outside them, rounded
//   half up (Math.round), and the wind direction of the nearer point (the upper one at the middle); null stays null.
(function(){
'use strict';
const H=36e5,KEYS=['temp','wind','gust'];
// Gudauri time (UTC+4, no summer time) as the answer writes its hours, "2027-01-12T11:00"
const hourOf=ms=>new Date(ms+4*H).toISOString().slice(0,13)+':00';
const r=v=>v==null?null:Math.round(v);
function state(a,ms){if(!a||!a.updated)return 'none';const age=ms-Date.parse(a.updated);
  return age<=12*H?'fresh':age<=48*H?'stale':'none';}
function point(a,id){return a&&a.points?a.points.find(p=>p.id===id)||null:null;}
// the raw values of a point in the hour that has begun (not rounded)
function raw(p,ms){if(!p)return null;const i=p.hourly&&p.hourly.time?p.hourly.time.indexOf(hourOf(ms)):-1;if(i<0)return null;
  const h=p.hourly;return {temp:h.temp[i],wind:h.wind[i],gust:h.gust[i],dir:h.dir[i],snow24:p.snow24,code:h.code?h.code[i]:null};}
function now(a,id,ms){const v=raw(point(a,id),ms);if(!v)return null;return {temp:r(v.temp),wind:r(v.wind),gust:r(v.gust),dir:v.dir,snow24:r(v.snow24)};}
function at(a,alt,ms){const ps=(a&&a.points||[]).map(p=>({p,v:raw(p,ms)})).filter(x=>x.v).sort((x,y)=>x.p.elevation-y.p.elevation);
  if(!ps.length)return null;
  let lo=ps[0],hi=ps[ps.length-1];
  if(alt<=lo.p.elevation)hi=lo;else if(alt>=hi.p.elevation)lo=hi;
  else for(let i=1;i<ps.length;i++)if(ps[i].p.elevation>=alt){lo=ps[i-1];hi=ps[i];break;}
  const f=hi===lo?0:(alt-lo.p.elevation)/(hi.p.elevation-lo.p.elevation),mix=(x,y)=>x==null||y==null?null:Math.round(x+(y-x)*f);
  const o={};KEYS.forEach(k=>o[k]=mix(lo.v[k],hi.v[k]));o.snow24=mix(lo.v.snow24,hi.v.snow24);o.dir=(f>=.5?hi:lo).v.dir;return o;}
// the daily estimate per lift for a date ("YYYY-MM-DD"), as the server decided it
function risk(a,date){const d=a&&a.ridge&&a.ridge.daily;if(!d)return {};const i=d.date.indexOf(date);if(i<0)return {};
  const o={};for(const k in d.risk)if(d.risk[k]&&d.risk[k][i]!=null)o[k]=d.risk[k][i];return o;}
// the eight wind directions, from where it blows (the arrow points where it blows to)
const dir8=deg=>['n','ne','e','se','s','sw','w','nw'][Math.round((((deg%360)+360)%360)/45)%8];
window.GudWeather={state,now,at,risk,dir8,hourOf,point};
})();
