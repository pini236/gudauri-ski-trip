package com.pini.gudauri.map

import android.animation.ValueAnimator
import android.content.Context
import android.opengl.GLSurfaceView
import android.provider.Settings
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import com.pini.gudauri.data.*
import kotlin.math.*

class MountainSurface(context:Context,data:MountainData,
    snapshot:(MapSnapshot)->Unit,picked:(Pick)->Unit,failed:(String)->Unit):GLSurfaceView(context) {
    val mountain=MountainRenderer(data,resources.displayMetrics.density,
        { value -> post { snapshot(value) } },{ value -> post { picked(value) } },{ error -> post { failed(error) } })
    private val terrain=data.terrain
    private var animator:ValueAnimator?=null
    private var lastX=0f;private var lastY=0f;private var lastDistance=0f;private var lastAngle=0f
    private var movement=0f
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8,8,8,8,24,0)
        preserveEGLContextOnPause=true
        setRenderer(mountain)
        renderMode=RENDERMODE_WHEN_DIRTY
        contentDescription="מפת המסלולים. אצבע אחת לסיבוב, שתי אצבעות לזום והזזה. בחירת מסלול זמינה גם ברשימה."
    }
    fun focus(piste:Piste?=null,lift:Lift?=null) = animate(mountain.focus(piste,lift))
    fun home()=animate(mountain.home.copy(topView=mountain.camera.topView))
    fun kobi() { val q=DataParser.project(42.532,44.4945);animate(mountain.camera.copy(x=q.x,z=q.z,distance=9000f,azimuth=(PI*.9).toFloat(),pitch=.6f)) }
    fun mode(top:Boolean)=animate(mountain.camera.copy(topView=top,azimuth=if(top)0f else mountain.camera.azimuth,distance=if(top)mountain.camera.distance*.8f else mountain.camera.distance*1.25f))
    fun zoom(factor:Float)=animate(mountain.camera.copy(distance=(mountain.camera.distance*factor).coerceIn(350f,32000f)))
    fun north()=animate(mountain.camera.copy(azimuth=0f))
    fun selection(key:String?) { if(mountain.selected!=key){mountain.selected=key;requestRender()} }
    fun filters(hidden:Set<String>) { if(mountain.hidden!=hidden){mountain.hidden=hidden;requestRender()} }
    fun theme(dark:Boolean) { if(mountain.dark!=dark){mountain.dark=dark;requestRender()} }
    private fun animate(target:CameraState) {
        animator?.cancel()
        val source=mountain.camera
        val motion=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        if(motion==0f) { mountain.camera=target;requestRender();return }
        var daz=(target.azimuth-source.azimuth)%(2*PI.toFloat())
        if(daz>PI)daz-=2*PI.toFloat();if(daz < -PI)daz+=2*PI.toFloat()
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=650;interpolator=AccelerateDecelerateInterpolator()
            addUpdateListener { a -> val t=a.animatedValue as Float
                fun mix(x:Float,y:Float)=x+(y-x)*t
                mountain.camera=target.copy(x=mix(source.x,target.x),z=mix(source.z,target.z),distance=mix(source.distance,target.distance),pitch=mix(source.pitch,target.pitch),azimuth=source.azimuth+daz*t)
                requestRender()
            };start()
        }
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        fun centroid()= if(event.pointerCount>=2) (event.getX(0)+event.getX(1))/2 to (event.getY(0)+event.getY(1))/2 else event.x to event.y
        val (x,y)=centroid()
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true);animator?.cancel();lastX=x;lastY=y;movement=0f;lastDistance=0f }
            MotionEvent.ACTION_POINTER_DOWN -> { lastX=x;lastY=y;movement+=100
                lastDistance=hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));lastAngle=atan2(event.getY(1)-event.getY(0),event.getX(1)-event.getX(0)) }
            MotionEvent.ACTION_MOVE -> {
                val dx=x-lastX;val dy=y-lastY;movement+=abs(dx)+abs(dy)
                var s=mountain.camera
                if(event.pointerCount>=2) {
                    val d=hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));val angle=atan2(event.getY(1)-event.getY(0),event.getX(1)-event.getX(0))
                    if(lastDistance>1 && d>1)s=s.copy(distance=(s.distance*lastDistance/d).coerceIn(350f,32000f),azimuth=if(s.topView)0f else s.azimuth+angle-lastAngle)
                    lastDistance=d;lastAngle=angle;s=pan(s,dx,dy)
                } else if(s.topView)s=pan(s,dx,dy)
                else s=s.copy(azimuth=s.azimuth-dx/resources.displayMetrics.density*.006f,pitch=(s.pitch+dy/resources.displayMetrics.density*.004f).coerceIn(.12f,1.45f))
                mountain.camera=s;lastX=x;lastY=y;requestRender()
            }
            MotionEvent.ACTION_POINTER_UP -> { val remaining=if(event.actionIndex==0)1 else 0;lastX=event.getX(remaining);lastY=event.getY(remaining);lastDistance=0f;movement+=100 }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false)
                if(movement<8*resources.displayMetrics.density) { performClick();mountain.pendingPick=event.x to event.y;requestRender() }
            }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false);lastDistance=0f }
        };return true
    }
    private fun pan(s:CameraState,dx:Float,dy:Float):CameraState {
        val scale=if(s.topView)s.distance/max(1,height) else s.distance*.0012f*(700f/max(400,height))
        val ca=cos(s.azimuth);val sa=sin(s.azimuth)
        val pitch=if(s.topView)1f else max(.35f,sin(s.pitch+.3f))
        return s.copy(x=(s.x-dx*scale*ca-dy*scale*sa/pitch).coerceIn(terrain.x0,terrain.x1),z=(s.z+dx*scale*sa-dy*scale*ca/pitch).coerceIn(terrain.z0,terrain.z1))
    }
    override fun performClick():Boolean { super.performClick();return true }
    override fun onDetachedFromWindow() { animator?.cancel();super.onDetachedFromWindow() }
}
