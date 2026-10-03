/* Snow on the signs and cards (round 3 S3, round 8 GP2, round 12 W1). Every element with data-snow="<seed>" gets a
   cap drawn to its real width, so the snow rests on its top edge from end to end and covers the top border; on a sign
   with an arrow (data-snow-arrow="<arrow width in px>") it stops where the arrow begins, on whichever side the arrow is
   in this language. Drawn again when the width changes. The colours are the --sn* tokens in css/site.css (moonlit at
   night). The seed keeps each sign's own drift. Shared by the site and the privacy page. SNOW.scan(root) after drawing
   new elements with data-snow. */
(function(){
  var T=16,uid=0; // T: where the sign's top edge is inside the drawing
  function rng(seed){var r=(seed+1)*9301%233280;return function(a,b){r=(r*9301+49297)%233280;return a+(b-a)*r/233280;};}
  // a smooth line through the points (Catmull-Rom as cubic curves)
  function curve(p){var f=function(v){return v.toFixed(1);},d='M'+f(p[0][0])+','+f(p[0][1]);
    for(var k=0;k<p.length-1;k++){var a=p[Math.max(k-1,0)],b=p[k],c=p[k+1],e=p[Math.min(k+2,p.length-1)];
      d+='C'+f(b[0]+(c[0]-a[0])/6)+','+f(b[1]+(c[1]-a[1])/6)+' '+f(c[0]-(e[0]-b[0])/6)+','+f(c[1]-(e[1]-b[1])/6)+' '+f(c[0])+','+f(c[1]);}
    return d;}
  function shape(seed,w,arrow,side,low){
    var R=rng(seed),x0=-3,x1=w+3;
    if(arrow){if(side==='left')x0=arrow-5;else x1=w-arrow+5;}
    // the top: drifts of different sizes with flatter stretches between them; the ends roll over the edge of the sign
    var hi=low?10:14,up=[[x0,T+4],[x0+3,T-2]],x=x0+R(14,24),pk=true;
    while(x<x1-18){up.push(pk?[x,T-(R(0,1)<.45?R(hi*.6,hi):R(2.5,hi*.45))]:[x,T-R(1,2.5)]);x+=pk?R(18,34):R(16,30);pk=!pk;}
    up.push([x1-3,T-2],[x1,T+4]);
    // the underside: a wavy lip a few pixels over the face
    var lo=[],xl=x1-5;while(xl>x0+8){lo.push([xl,T+R(3,6.5)]);xl-=R(22,40);}lo.push([x0+3,T+5]);
    var body=curve(up.concat(lo,[up[0]]))+'Z',drips='',n=Math.round(R(1,3.4));
    for(var i=0;i<n;i++){var dx=R(x0+26,x1-26),dl=R(5,9.5),dw=R(2.4,3.6),y0=T+2,g=function(v){return v.toFixed(1);};
      drips+='<path d="M'+g(dx-dw)+','+y0+'C'+g(dx-dw)+','+g(y0+dl*.7)+' '+g(dx-dw*.4)+','+g(y0+dl)+' '+g(dx)+','+g(y0+dl)+'C'+g(dx+dw*.4)+','+g(y0+dl)+' '+g(dx+dw)+','+g(y0+dl*.7)+' '+g(dx+dw)+','+y0+'Z"/>';}
    return {body:body,edge:curve(up),drips:drips};
  }
  function draw(el){
    var w=el.offsetWidth;if(!w||Math.abs(w-(el._snowW||0))<2)return;el._snowW=w;
    var cs=getComputedStyle(el),bt=parseFloat(cs.borderTopWidth)||0,arrow=+el.getAttribute('data-snow-arrow')||0,
      side=cs.direction==='rtl'?'left':'right',s=shape(+el.getAttribute('data-snow')||0,w,arrow,side,el.hasAttribute('data-snow-low')),id='sn'+(++uid),H=T+16;
    var html='<svg class="snowcap" width="'+w+'" height="'+H+'" viewBox="0 0 '+w+' '+H+'" style="top:'+(-T-bt)+'px" aria-hidden="true" focusable="false">'
      +'<defs><linearGradient id="'+id+'" x1="0" y1="0" x2="0" y2="1"><stop offset="0" style="stop-color:var(--sn1)"/><stop offset=".55" style="stop-color:var(--sn2)"/><stop offset="1" style="stop-color:var(--sn3)"/></linearGradient>'
      +'</defs>'
      // the shadow it casts, the drips under its lip, the snow, and its outline (light, by day only)
      +'<g class="sn-shadow" transform="translate(0 1.6)"><path d="'+s.body+'"/>'+s.drips+'</g><g class="sn-drips">'+s.drips+'</g>'
      +'<path class="sn-body" d="'+s.body+'" fill="url(#'+id+')"/><path class="sn-edge" d="'+s.edge+'"/></svg>';
    var old=el.querySelector(':scope>.snowcap');if(old)old.remove();
    el.insertAdjacentHTML('afterbegin',html);
  }
  var ro=window.ResizeObserver?new ResizeObserver(function(es){es.forEach(function(e){draw(e.target);});}):null;
  function scan(root){(root||document).querySelectorAll('[data-snow]').forEach(function(el){if(!el._snowSeen){el._snowSeen=1;if(ro)ro.observe(el);}draw(el);});}
  window.SNOW={scan:scan,draw:draw};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){scan();});else scan();
})();
