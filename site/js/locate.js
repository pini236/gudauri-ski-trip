// "Where am I" (round 19, decision 58; docs/ARCHITECTURE.md m-7): which run or lift a position is on. The position never
// leaves the device: this only reads it. One rule for the site and the app, tested against the shared readings in
// tools/fixtures/location-m7.json (the rule is written out there too):
//   outside the terrain model: outside; an approximate location: approx; accuracy over 50 m: low;
//   within 25 m of a lift's cable and going up along it in three readings in a row on the same lift: lift;
//   accuracy over 30 m: low; the nearest named run within 30 m: run (the run before stays while it is within 30 m and
//   less than 10 m farther); otherwise free.
(function(){
'use strict';
const lat0=42.51,lon0=44.495,kx=111320*Math.cos(lat0*Math.PI/180),ky=111320;
const P=(la,lo)=>[(lo-lon0)*kx,-(la-lat0)*ky];
const pts=g=>g.map(([la,lo])=>P(la,lo));
// distance from a point to a polyline, and how far along it the nearest point is
function near(L,x,y){let best=Infinity,at=0,acc=0;
  for(let i=1;i<L.length;i++){const [ax,ay]=L[i-1],[bx,by]=L[i],dx=bx-ax,dy=by-ay,l2=dx*dx+dy*dy,len=Math.sqrt(l2);
    const t=l2?Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/l2)):0,d=Math.hypot(ax+dx*t-x,ay+dy*t-y);
    if(d<best){best=d;at=acc+len*t;}acc+=len;}
  return {d:best,at,len:acc};}
function make(D,M){
  const d=M.d||M.dem,x0=d.x0,x1=d.x1,y0=d.y0,y1=d.y1;
  const runs=D.pistes.filter(p=>p.named).map(p=>({key:p.key,color:p.color,lines:p.segs.map(s=>pts(s.g))}));
  // each lift from its lower end, by the terrain model, so "along it" grows going up
  const lifts=D.lifts.filter(l=>l.name&&l.status!=='inactive'&&l.g&&l.g.length>1).map(l=>{let L=pts(l.g);
    if(M.elev(L[0][0],L[0][1])>M.elev(L[L.length-1][0],L[L.length-1][1]))L=L.slice().reverse();return {name:l.name,L};});
  let prevRun=null,trail=[];
  const runDist=r=>Math.min(...r.lines.map(L=>near(L,X,Y).d));
  let X=0,Y=0;
  function feed(o){
    [X,Y]=P(o.lat,o.lon);const acc=o.accuracy==null?Infinity:o.accuracy,base={x:X,y:Y,acc};
    if(X<x0||X>x1||Y<y0||Y>y1){prevRun=null;trail=[];return {...base,state:'outside'};}
    if(o.approximate){prevRun=null;trail=[];return {...base,state:'approx'};}
    if(acc>50){trail=[];return {...base,state:'low'};}
    // the lift: the nearest cable within 25 m, and the position along it growing in three readings in a row
    let lift=null;for(const l of lifts){const n=near(l.L,X,Y);if(n.d<=25&&(!lift||n.d<lift.d))lift={name:l.name,d:n.d,at:n.at};}
    if(lift){const last=trail[trail.length-1];
      trail=last&&last.name===lift.name&&lift.at>last.at?[...trail,lift]:[lift];
      if(trail.length>=3){prevRun=null;return {...base,state:'lift',lift:lift.name};}}
    else trail=[];
    if(acc>30)return {...base,state:'low'};
    let best=null;for(const r of runs){const dd=runDist(r);if(dd<=30&&(!best||dd<best.d))best={r,d:dd};}
    if(best&&prevRun&&prevRun!==best.r){const pd=runDist(prevRun);if(pd<=30&&pd-best.d<10)best={r:prevRun,d:pd};}
    if(best){prevRun=best.r;return {...base,state:'run',run:best.r.key,color:best.r.color};}
    prevRun=null;return {...base,state:'free'};
  }
  return {feed,reset(){prevRun=null;trail=[];}};
}
window.GudLocate={make};
})();
