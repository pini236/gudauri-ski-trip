/* Snow on the signs and cards (round 3 S3, round 8 GP2, round 12 W1). Every element with data-snow="<seed>" gets a
   cap drawn to its real width, so the snow rests on its top edge from end to end and covers the top border; on a sign
   with an arrow (data-snow-arrow="<arrow width in px>") it stops where the arrow begins, on whichever side the arrow is
   in this language. Three kinds: drifts (the game signs), low drifts (data-snow-low, the narrow signs on the map), and
   a pile (data-snow-pile, the white cards with a coloured top border): round 3's piles, taller toward the middle, with
   no outline and a soft shadow, since on a white card an outline or a hard shadow reads as a line under the snow.
   Drawn again when the width changes. The colours are the --sn* tokens in css/site.css (moonlit at night). The seed
   keeps each sign's own drift. Shared by the site and the privacy page. SNOW.scan(root) after drawing new elements
   with data-snow. */
(function(){
  var uid=0;
  function rng(seed){var r=(seed+1)*9301%233280;return function(a,b){r=(r*9301+49297)%233280;return a+(b-a)*r/233280;};}
  // a smooth line through the points (Catmull-Rom as cubic curves)
  function curve(p){var f=function(v){return v.toFixed(1);},d='M'+f(p[0][0])+','+f(p[0][1]);
    for(var k=0;k<p.length-1;k++){var a=p[Math.max(k-1,0)],b=p[k],c=p[k+1],e=p[Math.min(k+2,p.length-1)];
      d+='C'+f(b[0]+(c[0]-a[0])/6)+','+f(b[1]+(c[1]-a[1])/6)+' '+f(c[0]-(e[0]-b[0])/6)+','+f(c[1]-(e[1]-b[1])/6)+' '+f(c[0])+','+f(c[1]);}
    return d;}
  // T: where the sign's top edge is inside the drawing; B: where its face begins, under the top border
  function shape(seed,w,arrow,side,kind,T,B){
    var R=rng(seed),x0=-3,x1=w+3,bt=B>T,pile=kind==='pile';
    if(arrow){if(side==='left')x0=arrow-5;else x1=w-arrow+5;}
    // the ends roll over the edge of the sign, down past its top border
    var end=B+(bt?1:4),up=[[x0,end],[x0+3,T-2]],x,pk=true;
    if(pile){
      // piles, taller toward the middle, with low dips between them
      x=x0+R(12,18);
      while(x<x1-20){var mid=1-Math.abs((x-x0)/(x1-x0)-.5)*1.1;up.push(pk?[x,T-8-mid*R(10,20)]:[x,T-R(1,6)]);x+=pk?R(30,48):R(22,34);pk=!pk;}
    } else {
      // drifts of different sizes with flatter stretches between them
      var hi=kind==='low'?10:14;x=x0+R(14,24);
      while(x<x1-18){up.push(pk?[x,T-(R(0,1)<.45?R(hi*.6,hi):R(2.5,hi*.45))]:[x,T-R(1,2.5)]);x+=pk?R(18,34):R(16,30);pk=!pk;}
    }
    up.push([x1-3,T-2],[x1,end]);
    // the underside: a wavy lip a few pixels over the face, always below the top border
    var lip=pile?[4,8]:bt?[2,4.5]:[3,6.5],lo=[],xl=x1-5;
    while(xl>x0+8){lo.push([xl,B+R(lip[0],lip[1])]);xl-=R(22,40);}lo.push([x0+3,B+(bt?2:5)]);
    var body=curve(up.concat(lo,[up[0]]))+'Z',drips='',n=Math.round(R(1,3.4)),at=[],g=function(v){return v.toFixed(1);};
    for(var i=0;i<n;i++){var dx=R(x0+26,x1-26),dl=R(5,9.5)*(pile?1.25:1),dw=R(2.4,3.6)*(pile?1.35:1),y0=B+2;
      // on a pile, no two drips side by side
      if(pile&&at.some(function(a){return Math.abs(a-dx)<24;}))continue;at.push(dx);
      drips+='<path d="M'+g(dx-dw)+','+y0+'C'+g(dx-dw)+','+g(y0+dl*.7)+' '+g(dx-dw*.4)+','+g(y0+dl)+' '+g(dx)+','+g(y0+dl)+'C'+g(dx+dw*.4)+','+g(y0+dl)+' '+g(dx+dw)+','+g(y0+dl*.7)+' '+g(dx+dw)+','+y0+'Z"/>';}
    return {body:body,edge:curve(up),drips:drips};
  }
  function draw(el){
    var w=el.offsetWidth;if(!w||Math.abs(w-(el._snowW||0))<2)return;el._snowW=w;
    var cs=getComputedStyle(el),bt=parseFloat(cs.borderTopWidth)||0,arrow=+el.getAttribute('data-snow-arrow')||0,
      side=cs.direction==='rtl'?'left':'right',kind=el.hasAttribute('data-snow-pile')?'pile':el.hasAttribute('data-snow-low')?'low':'',
      pile=kind==='pile',T=pile?34:16,B=T+bt,s=shape(+el.getAttribute('data-snow')||0,w,arrow,side,kind,T,B),id='sn'+(++uid),H=B+(pile?24:16);
    var html='<svg class="snowcap" width="'+w+'" height="'+H+'" viewBox="0 0 '+w+' '+H+'" style="top:'+(-B)+'px" aria-hidden="true" focusable="false">'
      +'<defs><linearGradient id="'+id+'" x1="0" y1="0" x2="0" y2="1"><stop offset="0" style="stop-color:var(--sn1)"/><stop offset="'+(pile?'.7':'.55')+'" style="stop-color:var(--sn2)"/><stop offset="1" style="stop-color:var(--sn3)"/></linearGradient>'
      +(pile?'<filter id="'+id+'f" x="-5%" y="-50%" width="110%" height="200%"><feGaussianBlur stdDeviation="1.2"/></filter>':'')
      +'</defs>'
      // the shadow it casts (soft under a pile), the drips under its lip, the snow, and its outline (light, by day only; none on a pile)
      +'<g class="sn-shadow" transform="translate(0 '+(pile?2.2:1.6)+')"'+(pile?' filter="url(#'+id+'f)"':'')+'><path d="'+s.body+'"/>'+s.drips+'</g><g class="sn-drips">'+s.drips+'</g>'
      +'<path class="sn-body" d="'+s.body+'" fill="url(#'+id+')"/>'+(pile?'':'<path class="sn-edge" d="'+s.edge+'"/>')+'</svg>';
    var old=el.querySelector(':scope>.snowcap');if(old)old.remove();
    el.insertAdjacentHTML('afterbegin',html);
  }
  var ro=window.ResizeObserver?new ResizeObserver(function(es){es.forEach(function(e){draw(e.target);});}):null;
  function scan(root){(root||document).querySelectorAll('[data-snow]').forEach(function(el){if(!el._snowSeen){el._snowSeen=1;if(ro)ro.observe(el);}draw(el);});}
  window.SNOW={scan:scan,draw:draw};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){scan();});else scan();
})();
