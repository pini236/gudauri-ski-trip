package com.pini.gudauri.map

import android.graphics.*
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.pini.gudauri.data.*
import com.pini.gudauri.data.Point
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class CameraState(val x:Float, val z:Float, val distance:Float=9000f,
    val azimuth:Float=0f, val pitch:Float=.44f, val topView:Boolean=false)
data class MapLabel(val text:String, val x:Float, val y:Float, val color:Int, val priority:Int)
data class Pick(val piste:String?=null, val lift:Long?=null)
data class MapSnapshot(val labels:List<MapLabel>, val heading:Float, val metersPerPixel:Float, val ready:Boolean=true)
private data class Vertex(val x:Float,val y:Float,val z:Float)
private data class Feature(val pick:Pick, val category:String, val named:Boolean,
    val vertices:List<List<Vertex>>, val buffer:Int, val count:Int, val color:FloatArray,
    val width:Float, val label:String?, val labelPoint:Vertex?)

/** Native GPU renderer. No browser, JS engine, network request, or continuous idle loop. */
class MountainRenderer(private val data:MountainData, private val density:Float,
    private val publish:(MapSnapshot)->Unit, private val picked:(Pick)->Unit,
    private val failed:(String)->Unit) : GLSurfaceView.Renderer {
    private val terrain=data.terrain
    val home:CameraState
    @Volatile var camera:CameraState
    @Volatile var selected:String?=null
    @Volatile var hidden:Set<String> = emptySet()
    @Volatile var dark=false
    @Volatile var pendingPick:Pair<Float,Float>?=null
    private var width=1; private var height=1
    private var terrainProgram=0; private var lineProgram=0; private var dotProgram=0
    private var meshBuffer=0; private var indexBuffer=0; private var meshCount=0; private var texture=0
    private var stationBuffer=0; private var stationCount=0
    private val features=mutableListOf<Feature>()
    private val mvp=FloatArray(16); private val projection=FloatArray(16); private val view=FloatArray(16)
    private val eye=FloatArray(3)
    private val pisteColors=mapOf("green" to intArrayOf(27,138,76),"blue" to intArrayOf(31,95,196),"red" to intArrayOf(209,52,43),"black" to intArrayOf(19,35,58))
    private var lastPublish=0L
    init {
        val main=data.pistes.filter { p -> p.segments.flatMap { it.points }.map { it.z }.average() > -167 }.flatMap { p -> p.segments.flatMap { it.points } }
        val x=(main.minOf { it.x }+main.maxOf { it.x })/2
        val z=(main.minOf { it.z }+main.maxOf { it.z })/2+250
        home=CameraState(x,z)
        camera=home
    }
    override fun onSurfaceCreated(gl:GL10?, config:EGLConfig?) {
        try {
            glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
            terrainProgram=program(TERRAIN_VERTEX,TERRAIN_FRAGMENT)
            lineProgram=program(LINE_VERTEX,LINE_FRAGMENT)
            dotProgram=program(DOT_VERTEX,DOT_FRAGMENT)
            buildTerrain(); buildFeatures()
        } catch(e:Exception) { failed(e.message ?: "לא ניתן להפעיל תלת־ממד במכשיר הזה") }
    }
    override fun onSurfaceChanged(gl:GL10?, w:Int,h:Int) { width=max(1,w); height=max(1,h); glViewport(0,0,width,height) }
    override fun onDrawFrame(gl:GL10?) {
        val bg=if(dark) floatArrayOf(.053f,.082f,.133f) else floatArrayOf(.863f,.906f,.945f)
        glClearColor(bg[0],bg[1],bg[2],1f); glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if(terrainProgram==0) return
        val state=camera
        cameraMatrices(state)
        glUseProgram(terrainProgram); uniformMatrix(terrainProgram)
        glUniform3fv(glGetUniformLocation(terrainProgram,"eye"),1,eye,0)
        glUniform3fv(glGetUniformLocation(terrainProgram,"fogColor"),1,bg,0)
        // Orthographic altitude is fixed; zoom controls map scale, not fog distance.
        glUniform1f(glGetUniformLocation(terrainProgram,"fogNear"),if(state.topView)100000f else state.distance*1.1f+2000)
        glUniform1f(glGetUniformLocation(terrainProgram,"fogFar"),if(state.topView)200000f else state.distance*3.6f+18000)
        glBindBuffer(GL_ARRAY_BUFFER,meshBuffer); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,indexBuffer)
        attribute(0,3,32,0); attribute(1,3,32,12); attribute(2,2,32,24)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture)
        glDrawElements(GL_TRIANGLES,meshCount,GL_UNSIGNED_INT,0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,0)
        glDepthMask(false)
        glUseProgram(lineProgram); uniformMatrix(lineProgram)
        glUniform2f(glGetUniformLocation(lineProgram,"resolution"),width/2f,height/2f)
        features.forEach { f ->
            if(f.category in hidden || !f.named && "unnamed" in hidden) return@forEach
            val on=selected==null || f.pick.piste==selected
            val size=f.width + if(f.pick.piste!=null && f.pick.piste==selected) 2.4f else 0f
            glBindBuffer(GL_ARRAY_BUFFER,f.buffer)
            attribute(0,3,40,0); attribute(1,3,40,12); attribute(2,3,40,24); attribute(3,1,40,36)
            setLine(floatArrayOf(1f,1f,1f), (size+3.2f)*density, if(on)1f else .2f, -.00015f)
            if(f.pick.piste==selected) setLine(floatArrayOf(1f,.89f,.54f),(size+5f)*density,1f,-.00015f)
            glDrawArrays(GL_TRIANGLES,0,f.count)
            setLine(f.color,size*density,if(on)1f else .28f,-.0003f)
            glDrawArrays(GL_TRIANGLES,0,f.count)
        }
        glDisableVertexAttribArray(3); glDisableVertexAttribArray(1); glDisableVertexAttribArray(2)
        if("lifts" !in hidden) {
            glUseProgram(dotProgram); uniformMatrix(dotProgram)
            glUniform1f(glGetUniformLocation(dotProgram,"pointSize"),8*density)
            glBindBuffer(GL_ARRAY_BUFFER,stationBuffer); attribute(0,3,12,0)
            glDrawArrays(GL_POINTS,0,stationCount)
        }
        glDepthMask(true)
        pendingPick?.let { (x,y) -> pendingPick=null; findPick(x,y)?.let(picked) }
        publish(MapSnapshot(labels(),state.azimuth,if(state.topView)state.distance/height else state.distance*.73f/height))
    }
    private fun cameraMatrices(s:CameraState) {
        val aspect=width.toFloat()/height
        val y0=terrain.elevation(s.x,s.z)
        if(s.topView) {
            eye[0]=s.x; eye[1]=y0+20000; eye[2]=s.z+.1f
            Matrix.orthoM(projection,0,-s.distance*aspect/2,s.distance*aspect/2,-s.distance/2,s.distance/2,10f,45000f)
            Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],s.x,y0,s.z,0f,0f,-1f)
        } else {
            val cp=cos(s.pitch)
            eye[0]=s.x+s.distance*sin(s.azimuth)*cp
            eye[2]=s.z+s.distance*cos(s.azimuth)*cp
            eye[1]=max(y0+s.distance*sin(s.pitch),terrain.elevation(eye[0],eye[2])+60)
            Matrix.perspectiveM(projection,0,40f,aspect,max(10f,s.distance/200),s.distance*6+30000)
            Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],s.x,y0,s.z,0f,1f,0f)
        }
        Matrix.multiplyMM(mvp,0,projection,0,view,0)
    }
    private fun setLine(color:FloatArray,width:Float,opacity:Float,offset:Float) {
        glUniform3fv(glGetUniformLocation(lineProgram,"color"),1,color,0)
        glUniform1f(glGetUniformLocation(lineProgram,"lineWidth"),width)
        glUniform1f(glGetUniformLocation(lineProgram,"opacity"),opacity)
        glUniform1f(glGetUniformLocation(lineProgram,"depthOffset"),offset)
    }
    private fun uniformMatrix(p:Int) = glUniformMatrix4fv(glGetUniformLocation(p,"mvp"),1,false,mvp,0)
    private fun attribute(index:Int,size:Int,stride:Int,offset:Int) { glEnableVertexAttribArray(index); glVertexAttribPointer(index,size,GL_FLOAT,false,stride,offset) }
    private fun upload(values:FloatArray):Int {
        val b=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(values); b.position(0)
        val ids=IntArray(1); glGenBuffers(1,ids,0); glBindBuffer(GL_ARRAY_BUFFER,ids[0]); glBufferData(GL_ARRAY_BUFFER,values.size*4,b,GL_STATIC_DRAW); return ids[0]
    }
    private fun buildTerrain() {
        // Full-resolution height data for picking/stats; bounded mesh for the GPU.
        val cols=(0 until terrain.nx step 2).toMutableList().also { if(it.last()!=terrain.nx-1)it.add(terrain.nx-1) }
        val rows=(0 until terrain.ny step 2).toMutableList().also { if(it.last()!=terrain.ny-1)it.add(terrain.ny-1) }
        val vertices=FloatArray(cols.size*rows.size*8)
        var k=0
        rows.forEach { r -> cols.forEach { c ->
            val x=terrain.x0+c*terrain.sx; val z=terrain.z0+r*terrain.sz; val h=terrain.elevation(x,z)
            val gx=(terrain.elevation(x+terrain.sx,z)-terrain.elevation(x-terrain.sx,z))/(2*terrain.sx)
            val gz=(terrain.elevation(x,z+terrain.sz)-terrain.elevation(x,z-terrain.sz))/(2*terrain.sz)
            val norm=sqrt(gx*gx+gz*gz+1)
            floatArrayOf(x,h,z,-gx/norm,1/norm,-gz/norm,c.toFloat()/(terrain.nx-1),r.toFloat()/(terrain.ny-1)).forEach { vertices[k++]=it }
        } }
        meshBuffer=upload(vertices)
        val indices=IntArray((cols.size-1)*(rows.size-1)*6); k=0
        for(r in 0 until rows.lastIndex) for(c in 0 until cols.lastIndex) {
            val a=r*cols.size+c; val b=a+1; val e=a+cols.size; val f=e+1
            intArrayOf(a,e,b,b,e,f).forEach { indices[k++]=it }
        }
        meshCount=indices.size
        val buffer=ByteBuffer.allocateDirect(indices.size*4).order(ByteOrder.nativeOrder()).asIntBuffer().put(indices); buffer.position(0)
        val id=IntArray(1); glGenBuffers(1,id,0); indexBuffer=id[0]
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,indexBuffer); glBufferData(GL_ELEMENT_ARRAY_BUFFER,indices.size*4,buffer,GL_STATIC_DRAW)
        val bitmap=terrainTexture()
        glGenTextures(1,id,0); texture=id[0]; glBindTexture(GL_TEXTURE_2D,texture)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0); glGenerateMipmap(GL_TEXTURE_2D); bitmap.recycle()
    }
    private fun terrainTexture():Bitmap {
        val w=1536; val h=((terrain.z1-terrain.z0)/(terrain.x1-terrain.x0)*w).roundToInt()
        val small=Bitmap.createBitmap(terrain.nx,terrain.ny,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(terrain.nx*terrain.ny)
        for(r in 0 until terrain.ny) for(c in 0 until terrain.nx) {
            val x=terrain.x0+c*terrain.sx; val z=terrain.z0+r*terrain.sz
            val gx=(terrain.elevation(x+terrain.sx,z)-terrain.elevation(x-terrain.sx,z))/(2*terrain.sx)
            val gz=(terrain.elevation(x,z+terrain.sz)-terrain.elevation(x,z-terrain.sz))/(2*terrain.sz)
            val slope=atan(hypot(gx,gz))*180/PI; val rock=((slope-36)/18).coerceIn(0.0,1.0)*.75
            pixels[r*terrain.nx+c]=Color.rgb((246*(1-rock)+150*rock).toInt(),(249*(1-rock)+153*rock).toInt(),(252*(1-rock)+160*rock).toInt())
        }
        small.setPixels(pixels,0,terrain.nx,0,0,terrain.nx,terrain.ny)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); val canvas=Canvas(bitmap)
        canvas.drawBitmap(small,null,Rect(0,0,w,h),Paint(Paint.FILTER_BITMAP_FLAG)); small.recycle()
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeJoin=Paint.Join.ROUND; strokeCap=Paint.Cap.ROUND }
        fun path(points:List<Point>,close:Boolean=false):Path = Path().apply { points.forEachIndexed { i,p ->
            val x=(p.x-terrain.x0)/(terrain.x1-terrain.x0)*w; val y=(p.z-terrain.z0)/(terrain.z1-terrain.z0)*h
            if(i==0)moveTo(x,y) else lineTo(x,y)
        }; if(close)close() }
        terrain.contours.filter { it.height%100==0 }.forEach { paint.color=if(it.height%500==0)0x50465A73 else 0x25465A73; paint.strokeWidth=if(it.height%500==0)1.3f else .65f; canvas.drawPath(path(it.points),paint) }
        paint.style=Paint.Style.FILL; paint.color=0xBCD6CBBE.toInt(); terrain.village.forEach { canvas.drawPath(path(it,true),paint) }
        paint.color=0xFF8DB6D8.toInt(); terrain.water.forEach { canvas.drawPath(path(it,true),paint) }
        paint.style=Paint.Style.STROKE; paint.strokeWidth=1.2f; paint.color=0xA078A0C8.toInt(); terrain.rivers.forEach { canvas.drawPath(path(it),paint) }
        terrain.roads.sortedByDescending { it.kind }.forEach { paint.color=0xB88A7F76.toInt(); paint.strokeWidth=if(it.kind==0)1.6f else .7f; canvas.drawPath(path(it.points),paint) }
        return bitmap
    }
    private fun drape(points:List<Point>,lift:Float):List<Vertex> {
        val out=mutableListOf<Vertex>()
        points.forEachIndexed { i,p ->
            if(i>0) { val prev=points[i-1]; val n=ceil(hypot(p.x-prev.x,p.z-prev.z)/18).toInt()
                for(j in 1 until n) { val t=j.toFloat()/n; val x=prev.x+(p.x-prev.x)*t; val z=prev.z+(p.z-prev.z)*t; out.add(Vertex(x,terrain.elevation(x,z)+lift,z)) } }
            out.add(Vertex(p.x,terrain.elevation(p.x,p.z)+lift,p.z))
        }; return out
    }
    private fun buildFeatures() {
        features.clear()
        fun add(pick:Pick,category:String,named:Boolean,lines:List<List<Vertex>>,color:FloatArray,width:Float,label:String?) {
            if(lines.isEmpty())return
            var count=0; lines.forEach { count+=max(0,it.size-1)*6 }
            val values=FloatArray(count*10); var i=0
            fun vertex(l:List<Vertex>,j:Int,side:Float) {
                val q=l[j]; val a=l[max(0,j-1)]; val b=l[min(l.lastIndex,j+1)]
                floatArrayOf(q.x,q.y,q.z,a.x,a.y,a.z,b.x,b.y,b.z,side).forEach { values[i++]=it }
            }
            lines.forEach { l -> for(j in 0 until l.lastIndex) { vertex(l,j,-1f);vertex(l,j,1f);vertex(l,j+1,-1f);vertex(l,j+1,-1f);vertex(l,j,1f);vertex(l,j+1,1f) } }
            val longest=lines.maxBy { it.size }
            features.add(Feature(pick,category,named,lines,upload(values),count,color,width,label,longest[(longest.size*.45f).toInt()]))
        }
        data.pistes.forEach { p ->
            val rgb=pisteColors.getValue(p.color).map { it/255f }.toFloatArray()
            add(Pick(piste=p.key),p.color,p.named,p.segments.filterNot { it.area }.map { drape(it.points,7f) },rgb,if(p.kind=="ski-way")2.8f else if(p.named)4f else 2.6f,if(p.named)p.key else null)
        }
        val stations=mutableListOf<Float>()
        data.lifts.forEach { l ->
            val line=drape(l.points,10f)
            val first=line.first(); val last=line.last(); var dist=0f
            val total=l.points.zipWithNext().sumOf { (a,b)->hypot(a.x-b.x,a.z-b.z).toDouble() }.toFloat()
            val cable=line.mapIndexed { i,q -> if(i>0)dist+=hypot(q.x-line[i-1].x,q.z-line[i-1].z); q.copy(y=max(q.y,first.y+(last.y-first.y)*(dist/max(total,1f)))) }
            add(Pick(lift=l.id),"lifts",true,listOf(cable),floatArrayOf(.227f,.271f,.337f),2f,l.name?.let { "⇡ $it" })
            listOf(cable.first(),cable.last()).forEach { stations.addAll(listOf(it.x,it.y,it.z)) }
        }
        stationBuffer=upload(stations.toFloatArray()); stationCount=stations.size/3
    }
    private fun project(v:Vertex):FloatArray? {
        val a=floatArrayOf(v.x,v.y,v.z,1f); val b=FloatArray(4); Matrix.multiplyMV(b,0,mvp,0,a,0)
        if(b[3]<=0 || b[2]/b[3]>1)return null
        return floatArrayOf((b[0]/b[3]+1)*width/2,(1-b[1]/b[3])*height/2)
    }
    private fun visible(v:Vertex):Boolean {
        if(camera.topView)return true
        for(i in 4 until 40) { val t=i/40f; val x=eye[0]+(v.x-eye[0])*t; val y=eye[1]+(v.y-eye[1])*t; val z=eye[2]+(v.z-eye[2])*t
            if(terrain.elevation(x,z)>y+8)return false }
        return true
    }
    private fun labels():List<MapLabel> {
        val labels=mutableListOf<MapLabel>()
        fun add(text:String,v:Vertex,color:Int,priority:Int) { val p=project(v) ?: return; if(p[0]<0||p[0]>width||p[1]<0||p[1]>height||!visible(v))return
            labels.add(MapLabel(text,p[0],p[1],color,priority)) }
        terrain.peaks.forEach { p -> add("${if(p.pass)"⌃" else "▲"} ${p.name} ${p.height}",Vertex(p.point.x,terrain.elevation(p.point.x,p.point.z)+10,p.point.z),Color.rgb(61,74,92),100) }
        terrain.places.filter { it.name in setOf("Gudauri","Kobi") }.forEach { p -> add(if(p.name=="Gudauri")"גודאורי" else p.name,Vertex(p.point.x,terrain.elevation(p.point.x,p.point.z)+12,p.point.z),Color.rgb(75,90,111),60) }
        features.forEach { f -> if(f.category in hidden || !f.named && "unnamed" in hidden)return@forEach
            if(f.pick.piste!=null && selected!=null && f.pick.piste!=selected)return@forEach
            if(f.pick.piste!=null && selected==null && camera.distance>11500)return@forEach
            if(f.label!=null && f.labelPoint!=null) {
                val c=Color.rgb((f.color[0]*255).toInt(),(f.color[1]*255).toInt(),(f.color[2]*255).toInt())
                add(f.label,f.labelPoint,c,if(f.pick.piste==selected)1000 else if(f.pick.lift!=null)45 else 40)
            }
        }; return labels.sortedByDescending { it.priority }
    }
    private fun findPick(x:Float,y:Float):Pick? {
        var best:Pick?=null; var distance=18*density
        features.forEach { f ->
            if(f.category in hidden || !f.named && "unnamed" in hidden)return@forEach
            f.vertices.forEach { line ->
                for(i in 0 until line.lastIndex step 2) {
                    val a=project(line[i]) ?: continue; val b=project(line[min(i+2,line.lastIndex)]) ?: continue
                    val vx=b[0]-a[0]; val vy=b[1]-a[1]; val l=vx*vx+vy*vy
                    val t=if(l==0f)0f else (((x-a[0])*vx+(y-a[1])*vy)/l).coerceIn(0f,1f)
                    val delta=hypot(x-a[0]-vx*t,y-a[1]-vy*t)
                    if(delta<distance && visible(line[i])) { distance=delta;best=f.pick }
                }
            }
        }; return best
    }
    fun focus(piste:Piste?=null,lift:Lift?=null):CameraState {
        val points=piste?.segments?.flatMap { it.points } ?: lift?.points ?: return home
        val x=(points.minOf { it.x }+points.maxOf { it.x })/2; val z=(points.minOf { it.z }+points.maxOf { it.z })/2
        val extent=max(points.maxOf { it.x }-points.minOf { it.x },points.maxOf { it.z }-points.minOf { it.z })
        val distance=if(camera.topView)max(900f,extent*1.5f*max(1f,height.toFloat()/width)) else max(1600f,extent*2.3f*max(1f,height.toFloat()/width*.7f))
        return camera.copy(x=x,z=z,distance=distance,pitch=.62f)
    }
    private fun program(v:String,f:String):Int {
        fun shader(type:Int,source:String):Int { val shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader)
            val ok=IntArray(1);glGetShaderiv(shader,GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) { glGetShaderInfoLog(shader) }; return shader }
        val vs=shader(GL_VERTEX_SHADER,v); val fs=shader(GL_FRAGMENT_SHADER,f); val program=glCreateProgram()
        glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program)
        val ok=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,ok,0);check(ok[0]!=0) { glGetProgramInfoLog(program) }
        glDeleteShader(vs);glDeleteShader(fs); return program
    }
    companion object {
        private val TERRAIN_VERTEX="""#version 300 es
            layout(location=0) in vec3 position; layout(location=1) in vec3 normal; layout(location=2) in vec2 uv;
            uniform mat4 mvp; out vec3 n; out vec2 tex; out vec3 world;
            void main(){gl_Position=mvp*vec4(position,1.);n=normal;tex=uv;world=position;}
        """.trimIndent()
        private val TERRAIN_FRAGMENT="""#version 300 es
            precision highp float; in vec3 n; in vec2 tex; in vec3 world; uniform sampler2D image;
            uniform vec3 eye; uniform vec3 fogColor; uniform float fogNear; uniform float fogFar; out vec4 frag;
            void main(){float light=.67+.33*max(0.,dot(normalize(n),normalize(vec3(-.35,.62,1.))));
            vec3 c=texture(image,tex).rgb*light;float fog=smoothstep(fogNear,fogFar,distance(eye,world));frag=vec4(mix(c,fogColor,fog),1.);}
        """.trimIndent()
        private val LINE_VERTEX="""#version 300 es
            layout(location=0) in vec3 position;layout(location=1) in vec3 prev;layout(location=2) in vec3 next;layout(location=3) in float side;
            uniform mat4 mvp;uniform vec2 resolution;uniform float lineWidth;uniform float depthOffset;
            void main(){vec4 c=mvp*vec4(position,1.),p=mvp*vec4(prev,1.),n=mvp*vec4(next,1.);
            vec2 a=c.xy/c.w*resolution,b=p.xy/p.w*resolution,e=n.xy/n.w*resolution;
            vec2 d1=a-b,d2=e-a;if(length(d1)<.0001)d1=d2;if(length(d2)<.0001)d2=d1;
            d1=normalize(d1);d2=normalize(d2);vec2 t=d1+d2;if(length(t)<.001)t=d1;else t=normalize(t);
            vec2 nn=vec2(-t.y,t.x);float ml=1./max(dot(nn,vec2(-d1.y,d1.x)),.6);
            c.xy+=nn*side*lineWidth*.5*ml/resolution*c.w;c.z+=depthOffset*c.w;gl_Position=c;}
        """.trimIndent()
        private val LINE_FRAGMENT="""#version 300 es
            precision mediump float;uniform vec3 color;uniform float opacity;out vec4 frag;
            void main(){frag=vec4(color,opacity);}
        """.trimIndent()
        private val DOT_VERTEX="""#version 300 es
            layout(location=0) in vec3 position;uniform mat4 mvp;uniform float pointSize;
            void main(){gl_Position=mvp*vec4(position,1.);gl_PointSize=pointSize;}
        """.trimIndent()
        private val DOT_FRAGMENT="""#version 300 es
            precision mediump float;out vec4 frag;void main(){float d=length(gl_PointCoord-.5);if(d>.5)discard;frag=vec4(d>.33?vec3(1.):vec3(.227,.271,.337),1.);}
        """.trimIndent()
    }
}
