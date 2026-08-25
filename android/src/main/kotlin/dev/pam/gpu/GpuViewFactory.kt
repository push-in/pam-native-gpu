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

class GpuViewFactory(@Suppress("UNUSED_PARAMETER") context: Context) : NativeViewFactory {
    override fun create(context: Context, emit: (ByteArray) -> Unit): View = PamGpuView(context, emit)
    override fun update(view: View, properties: Map<String, WireValue>) = (view as PamGpuView).update(properties)
    override fun release(view: View) = (view as PamGpuView).releaseGpu()
}

private class PamGpuView(context: Context, emit: (ByteArray) -> Unit) : GLSurfaceView(context) {
    private val renderer = GpuRenderer(emit)
    private var revision = -1L

    init { setEGLContextClientVersion(3); setRenderer(renderer); preserveEGLContextOnPause = true }

    fun update(values: Map<String, WireValue>) {
        renderMode = if ((values["renderMode"] as? WireValue.Integer)?.value == 2L) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
        val next = (values["revision"] as? WireValue.Integer)?.value ?: 0
        if (next == revision) return
        revision = next
        val shader = (values["glsl"] as? WireValue.Text)?.value.orEmpty()
        val uniforms = (values["uniforms"] as? WireValue.Text)?.value ?: "{}"
        queueEvent { renderer.configure(shader, uniforms) }
        requestRender()
    }

    fun releaseGpu() { queueEvent { renderer.release() }; onPause() }
}

private class GpuRenderer(private val emit: (ByteArray) -> Unit) : GLSurfaceView.Renderer {
    private var requestedShader = ""
    private var requestedUniforms = emptyList<Pair<String, Float>>()
    private var uniformLocations = IntArray(0)
    private var program = 0
    private var positionLocation = -1
    private var resolutionLocation = -1
    private var timeLocation = -1
    private var started = System.nanoTime()
    private var width = 1
    private var height = 1
    private val vertices = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }

    fun configure(shader: String, uniforms: String) {
        requestedShader = shader
        requestedUniforms = parseUniforms(uniforms)
        if (shader.isNotEmpty() && GLES30.glGetString(GLES30.GL_VERSION) != null) build()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { GLES30.glClearColor(0f, 0f, 0f, 0f); if (requestedShader.isNotEmpty()) build() }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width.coerceAtLeast(1); this.height = height.coerceAtLeast(1); GLES30.glViewport(0, 0, this.width, this.height) }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0) { GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); return }
        GLES30.glUseProgram(program)
        if (positionLocation >= 0) { GLES30.glEnableVertexAttribArray(positionLocation); GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT, false, 0, vertices) }
        if (resolutionLocation >= 0) GLES30.glUniform2f(resolutionLocation, width.toFloat(), height.toFloat())
        if (timeLocation >= 0) GLES30.glUniform1f(timeLocation, (System.nanoTime() - started) / 1_000_000_000f)
        requestedUniforms.forEachIndexed { index, (_, value) -> uniformLocations.getOrElse(index) { -1 }.takeIf { it >= 0 }?.let { GLES30.glUniform1f(it, value) } }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        if (positionLocation >= 0) GLES30.glDisableVertexAttribArray(positionLocation)
    }

    private fun build() {
        var vertex = 0; var fragment = 0; var next = 0
        runCatching {
            vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER); fragment = compile(GLES30.GL_FRAGMENT_SHADER, requestedShader); next = GLES30.glCreateProgram()
            check(next != 0) { "OpenGL ES could not allocate a program." }
            GLES30.glAttachShader(next, vertex); GLES30.glAttachShader(next, fragment); GLES30.glLinkProgram(next)
            val status = IntArray(1); GLES30.glGetProgramiv(next, GLES30.GL_LINK_STATUS, status, 0); check(status[0] != 0) { GLES30.glGetProgramInfoLog(next) }
            if (program != 0) GLES30.glDeleteProgram(program)
            program = next; next = 0
            positionLocation = GLES30.glGetAttribLocation(program, "a_position"); resolutionLocation = GLES30.glGetUniformLocation(program, "u_resolution"); timeLocation = GLES30.glGetUniformLocation(program, "u_time")
            uniformLocations = requestedUniforms.map { (name) -> GLES30.glGetUniformLocation(program, name) }.toIntArray()
            started = System.nanoTime(); send(1)
        }.onFailure { send(3, it.message.orEmpty()) }
        if (next != 0) GLES30.glDeleteProgram(next); if (vertex != 0) GLES30.glDeleteShader(vertex); if (fragment != 0) GLES30.glDeleteShader(fragment)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type); check(shader != 0) { "OpenGL ES could not allocate a shader." }
        GLES30.glShaderSource(shader, source); GLES30.glCompileShader(shader)
        val status = IntArray(1); GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { val message = GLES30.glGetShaderInfoLog(shader); GLES30.glDeleteShader(shader); error(message) }
        return shader
    }

    fun release() { if (program != 0) GLES30.glDeleteProgram(program); program = 0; uniformLocations = IntArray(0) }
    private fun send(event: Long, message: String = "") = emit(WireMap.encode(mapOf("event" to WireValue.Integer(event), "message" to WireValue.Text(message.take(4_096)))))

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
in vec2 a_position;
out vec2 v_uv;
void main(){v_uv=a_position*.5+.5;gl_Position=vec4(a_position,0.,1.);}"""
        private val UNIFORM_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")

        internal fun parseUniforms(json: String): List<Pair<String, Float>> {
            val document = JSONObject(json); val names = document.keys().asSequence().toList().sorted()
            require(names.size <= 64) { "GPU programs support at most 64 uniforms." }
            return names.map { name -> require(UNIFORM_NAME.matches(name)) { "Invalid GPU uniform name." }; val value = document.getDouble(name).toFloat(); require(value.isFinite()) { "GPU uniforms must be finite." }; name to value }
        }
    }
}
