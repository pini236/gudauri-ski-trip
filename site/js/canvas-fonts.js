/* The games draw some text on a canvas in Karantina or "IBM Plex Sans Hebrew", which have no Russian or Georgian
   letters. In another language the canvas gets the fonts of that language instead (decision 36, FT1; the same
   --f-display and --f-body as the page), so the sources in design/ stay as they are. Hebrew is not touched. */
(function(){
  var P=window.CanvasRenderingContext2D&&CanvasRenderingContext2D.prototype,d=P&&Object.getOwnPropertyDescriptor(P,'font');
  if(!d||!d.set)return;
  var lang='',disp='',body='';
  function fonts(){var l=document.documentElement.lang||'he';if(l===lang)return;lang=l;
    var cs=getComputedStyle(document.documentElement);disp=cs.getPropertyValue('--f-display').trim();body=cs.getPropertyValue('--f-body').trim();}
  Object.defineProperty(P,'font',{configurable:true,enumerable:d.enumerable,get:d.get,set:function(v){
    if(typeof v==='string'&&document.documentElement.lang&&document.documentElement.lang!=='he'){fonts();
      if(disp)v=v.replace(/"?Karantina"?/g,disp);if(body)v=v.replace(/"IBM Plex Sans Hebrew"/g,body);}
    d.set.call(this,v);}});
})();
