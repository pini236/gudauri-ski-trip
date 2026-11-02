// Run names beside the line, curving with it (round 21). Pure geometry, no DOM, so Node can test it too.
// windows: label.a of a run, [s,t0,t1,cL,cR] (tools/build-labels.py); segs: the run's segments as [[x,y],...] in metres.
// wM, hM: the text's length and height in metres at this zoom. off: metres from the line's centre to the text's near edge.
// Returns {d, side, win} (d: an SVG path for a textPath, the baseline) or null when no window has room: then the name waits for a closer zoom.
(function(root){
  function cut(L,a,b){ // the part of polyline L between distances a and b
    const o=[];let acc=0;
    for(let i=1;i<L.length;i++){
      const p=L[i-1],q=L[i],l=Math.hypot(q[0]-p[0],q[1]-p[1]);
      if(acc+l>=a&&acc<=b&&l>0){
        const u0=Math.max(0,(a-acc)/l),u1=Math.min(1,(b-acc)/l);
        if(!o.length)o.push([p[0]+(q[0]-p[0])*u0,p[1]+(q[1]-p[1])*u0]);
        o.push([p[0]+(q[0]-p[0])*u1,p[1]+(q[1]-p[1])*u1]);
      }
      acc+=l;
    }
    return o;
  }
  const len=L=>{let d=0;for(let i=1;i<L.length;i++)d+=Math.hypot(L[i][0]-L[i-1][0],L[i][1]-L[i-1][1]);return d;};
  function shift(L,k){ // every point k metres to the left of the direction of travel (screen, y down)
    return L.map((p,i)=>{const a=L[Math.max(i-1,0)],b=L[Math.min(i+1,L.length-1)];let tx=b[0]-a[0],ty=b[1]-a[1];const t=Math.hypot(tx,ty)||1;tx/=t;ty/=t;return [p[0]+ty*k,p[1]-tx*k];});
  }
  function fit(windows,segs,wM,hM,off){
    if(!windows)return null;
    const needL=off+hM*.8+hM*.2,needR=off+hM*1.05+hM*.2; // the text's far edge plus a margin must stay clear of the other lines
    for(const w of windows){
      const [s,t0,t1,cL,cR]=w,L=segs[s];if(!L||!(t0<t1))continue;
      const tot=len(L),a=tot*t0/1000,b=tot*t1/1000;
      if(b-a<wM*1.1)continue;
      const mid=(a+b)/2;let sub=cut(L,mid-wM*.55,mid+wM*.55);if(sub.length<2)continue;
      let left=cL,right=cR;
      if(sub[0][0]>sub[sub.length-1][0]){sub=sub.slice().reverse();left=cR;right=cL;} // read left to right
      // above the line (the left of the travel direction when it runs east) is preferred
      let side=null;
      if(left>=needL)side='L';else if(right>=needR)side='R';
      if(!side)continue;
      const pts=side==='L'?shift(sub,off):shift(sub,-(off+hM*.8));
      return {d:pts.map((p,i)=>(i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join(''),side,win:w};
    }
    return null;
  }
  const api={fit,cut};
  if(typeof module!=='undefined')module.exports=api;else root.GudLabelFit=api;
})(typeof window!=='undefined'?window:globalThis);
