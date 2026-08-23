package dev.pam.gpu
import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.View
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import dev.pam.nativeapp.views.NativeViewFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import org.json.JSONObject

class GpuViewFactory(@Suppress("UNUSED_PARAMETER")context:Context):NativeViewFactory{
 override fun create(context:Context,emit:(ByteArray)->Unit):View=PamGpuView(context,emit)
 override fun update(view:View,properties:Map<String,WireValue>)=(view as PamGpuView).update(properties)
 override fun release(view:View)=(view as PamGpuView).releaseGpu()
}
private class PamGpuView(context:Context,emit:(ByteArray)->Unit):GLSurfaceView(context){
 private val renderer=GpuRenderer(emit);private var revision=-1L
 init{setEGLContextClientVersion(3);setRenderer(renderer);preserveEGLContextOnPause=true}
 fun update(values:Map<String,WireValue>){renderMode=if((values["renderMode"]as?WireValue.Integer)?.value==2L)RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY;val next=(values["revision"]as?WireValue.Integer)?.value?:0;if(next==revision)return;revision=next;val shader=(values["glsl"]as?WireValue.Text)?.value.orEmpty();val uniforms=(values["uniforms"]as?WireValue.Text)?.value?:"{}";queueEvent{renderer.configure(shader,uniforms)};requestRender()}
 fun releaseGpu(){queueEvent{renderer.release()};onPause()}
}
private class GpuRenderer(private val emit:(ByteArray)->Unit):GLSurfaceView.Renderer{
 private var requestedShader="";private var requestedUniforms="{}";private var program=0;private var started=System.nanoTime();private var width=1;private var height=1
 private val vertices=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0)}
 fun configure(shader:String,uniforms:String){requestedShader=shader;requestedUniforms=uniforms;if(shader.isNotEmpty()&&GLES30.glGetString(GLES30.GL_VERSION)!=null)build()}
 override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){GLES30.glClearColor(0f,0f,0f,0f);if(requestedShader.isNotEmpty())build()}
 override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){width=w.coerceAtLeast(1);height=h.coerceAtLeast(1);GLES30.glViewport(0,0,width,height)}
 override fun onDrawFrame(gl:GL10?){if(program==0){GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);return};GLES30.glUseProgram(program);val position=GLES30.glGetAttribLocation(program,"a_position");if(position>=0){GLES30.glEnableVertexAttribArray(position);GLES30.glVertexAttribPointer(position,2,GLES30.GL_FLOAT,false,0,vertices)};GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"u_resolution"),width.toFloat(),height.toFloat());GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"u_time"),(System.nanoTime()-started)/1_000_000_000f);runCatching{val values=JSONObject(requestedUniforms);for(name in values.keys()){GLES30.glUniform1f(GLES30.glGetUniformLocation(program,name),values.getDouble(name).toFloat())}};GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);if(position>=0)GLES30.glDisableVertexAttribArray(position)}
 private fun build(){runCatching{val vertex=compile(GLES30.GL_VERTEX_SHADER,"#version 300 es\nin vec2 a_position;out vec2 v_uv;void main(){v_uv=a_position*.5+.5;gl_Position=vec4(a_position,0.,1.);}");val fragment=compile(GLES30.GL_FRAGMENT_SHADER,requestedShader);val next=GLES30.glCreateProgram();GLES30.glAttachShader(next,vertex);GLES30.glAttachShader(next,fragment);GLES30.glLinkProgram(next);val status=IntArray(1);GLES30.glGetProgramiv(next,GLES30.GL_LINK_STATUS,status,0);if(status[0]==0)error(GLES30.glGetProgramInfoLog(next));GLES30.glDeleteShader(vertex);GLES30.glDeleteShader(fragment);if(program!=0)GLES30.glDeleteProgram(program);program=next;started=System.nanoTime();send(1)}.onFailure{send(3,it.message.orEmpty())}}
 private fun compile(type:Int,source:String):Int{val shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader);val status=IntArray(1);GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,status,0);if(status[0]==0){val message=GLES30.glGetShaderInfoLog(shader);GLES30.glDeleteShader(shader);error(message)};return shader}
 fun release(){if(program!=0){GLES30.glDeleteProgram(program);program=0}}
 private fun send(event:Long,message:String="")=emit(WireMap.encode(mapOf("event" to WireValue.Integer(event),"message" to WireValue.Text(message))))
}
