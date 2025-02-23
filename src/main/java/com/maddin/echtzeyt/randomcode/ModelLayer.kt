package com.maddin.echtzeyt.randomcode

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.opengl.GLUtils
import androidx.core.graphics.drawable.toBitmap
import org.oscim.backend.GL
import org.oscim.backend.GLAdapter.gl
import org.oscim.core.GeoPoint
import org.oscim.core.MercatorProjection
import org.oscim.core.Tile
import org.oscim.layers.Layer
import org.oscim.renderer.GLState
import org.oscim.renderer.GLViewport
import org.oscim.renderer.LayerRenderer
import java.io.InputStream
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.roundToInt
import org.oscim.map.Map as VMap

class ModelLayerOld(map: VMap) : Layer(map) {
    private val modelRenderer = ModelRendererOld()
    init {
        mRenderer = modelRenderer
    }
    fun addModel(model: ModelRendererOld.ModelInstance) {
        modelRenderer.add(model)
    }
    val models; get() = modelRenderer.models
}

class ModelRendererOld : LayerRenderer() {
    override fun setup(): Boolean {
        synchronized(this) {
            models.forEach { it.setup() }
            isReady = true
        }
        return true
    }

    override fun update(viewport: GLViewport?) {
        viewport ?: return
        synchronized(this) {
            models.forEach { it.setup(); it.update(viewport) }
        }
    }

    private val test = FloatArray(3)

    override fun render(viewport: GLViewport?) {
        viewport ?: return

        synchronized(this) {
            models.forEach { it.render(viewport) }
        }
    }

    val models = mutableListOf<ModelInstance>()
    fun add(instance: ModelInstance) {
        synchronized(this) {
            models.add(instance)
        }
    }

    companion object {
        /*private fun checkError(tag: String?=null, ignore: Boolean=false) {
            val err = gl.error
            if (err == GL.NO_ERROR) { return }
            if (ignore) { return }
            val t = tag?.let { " during \"$tag\"" } ?: ""
            Log.e("ModelRenderer", "Got error $err from gl$t")
        }*/

        fun makeShader(type: Int, source: String): Int {
            val shaderId = gl.createShader(type)
            gl.shaderSource(shaderId, source)
            gl.compileShader(shaderId)

            return shaderId
        }

        fun makeProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
            val vertexShader = makeShader(GL.VERTEX_SHADER, vertexShaderSource)
            val fragmentShader = makeShader(GL.FRAGMENT_SHADER, fragmentShaderSource)

            val programId = gl.createProgram()
            gl.attachShader(programId, vertexShader)
            gl.attachShader(programId, fragmentShader)
            gl.linkProgram(programId)

            gl.deleteShader(vertexShader)
            gl.deleteShader(fragmentShader)

            return programId
        }

        private val VERTICES_PER_FACE = 3 // we are only working with triangles
        private val COORDS_PER_VERTEX = 3 // we are working in 3 dimensions
        private val BYTES_PER_FLOAT = 4 // float = 32 bit = 4 bytes
        private val MAP_EXTENTS = FloatArray(8)
    }

    interface Model {
        fun newInstance(uid: Any?, position: GeoPoint, scale: Double=1.0, alpha: Double=1.0): ModelInstance = ModelInstance(this, uid, position, scale, alpha)
        fun newInstance(position: GeoPoint, scale: Double=1.0, alpha: Double=1.0) = ModelInstance(this, null, position, scale, alpha)
        fun setup() {}
        fun update(viewport: GLViewport, position: GeoPoint, scale: Double, alpha: Double) {}
        fun render(viewport: GLViewport, position: GeoPoint, scale: Double, alpha: Double)
    }

    open class ModelInstance(var model: Model, var uid: Any?, var position: GeoPoint, var scale: Double, var alpha: Double) {
        var isVisible = false; protected set
        private val pos = FloatArray(3)
        fun setup() {
            model.setup()
        }
        fun update(viewport: GLViewport) {
            model.update(viewport, position, scale, alpha)
        }
        fun render(viewport: GLViewport) {
            model.render(viewport, position, scale, alpha)
        }
    }

    open class Base3DModel(faces: Iterable<Face>) : Model {
        override fun setup() {
            if (isReady) { return }

            vertexBuffer = IntBuffer.wrap(intArrayOf(0)).also { gl.genBuffers(1, it) }[0]

            GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
            gl.bufferData(GL.ARRAY_BUFFER, numCoords * BYTES_PER_FLOAT, vertexTrisBuffer, GL.STATIC_DRAW)

            normalBuffer = IntBuffer.wrap(intArrayOf(0)).also { gl.genBuffers(1, it) }[0]

            GLState.bindBuffer(GL.ARRAY_BUFFER, normalBuffer)
            gl.bufferData(GL.ARRAY_BUFFER, numCoords * BYTES_PER_FLOAT, normalTrisBuffer, GL.STATIC_DRAW)

            colorBuffer = IntBuffer.wrap(intArrayOf(0)).also { gl.genBuffers(1, it) }[0]

            GLState.bindBuffer(GL.ARRAY_BUFFER, colorBuffer)
            gl.bufferData(GL.ARRAY_BUFFER, numCoords * BYTES_PER_FLOAT, colorTrisBuffer, GL.STATIC_DRAW)

            isReady = true
        }
        override fun render(viewport: GLViewport, position: GeoPoint, scale: Double, alpha: Double) {
            GLState.useProgram(program)
            GLState.blend(true)
            GLState.test(true, false)
            val wasDepthMaskEnabled = IntBuffer.allocate(1).also { gl.getIntegerv(GL.DEPTH_WRITEMASK, it) }[0] != 0
            gl.depthMask(true)

            val ppm = 1 / MercatorProjection.groundResolution(viewport.pos) // pixels per meter -> 1 unit in 3d = 1m
            gl.uniform1f(locUnifScale, (scale * ppm).toFloat()) // pixels per meter -> 1 unit in 3d = 1m

            val x = MercatorProjection.longitudeToX(position.longitude) - viewport.pos.x
            val y = MercatorProjection.latitudeToY(position.latitude) - viewport.pos.y
            val tileScale = Tile.SIZE * viewport.pos.scale

            viewport.mvp.setTransScale((x * tileScale).toFloat(), (y * tileScale).toFloat(), 1f)
            viewport.mvp.multiplyMM(viewport.viewproj, viewport.mvp)
            viewport.mvp.setAsUniform(locUnifMvp)

            GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
            gl.vertexAttribPointer(locAttrPosition, COORDS_PER_VERTEX, GL.FLOAT, false, 0, 0)

            GLState.bindBuffer(GL.ARRAY_BUFFER, normalBuffer)
            gl.vertexAttribPointer(locAttrNormal, COORDS_PER_VERTEX, GL.FLOAT, false, 0, 0)

            GLState.bindBuffer(GL.ARRAY_BUFFER, colorBuffer)
            gl.vertexAttribPointer(locAttrColor, COORDS_PER_VERTEX, GL.FLOAT, false, 0, 0)

            gl.drawArrays(GL.TRIANGLES, 0, numVertices)

            gl.depthMask(wasDepthMaskEnabled) // Reset depth mask value
        }

        private var isReady = false
        private var vertexBuffer = GL.NONE
        private var normalBuffer = GL.NONE
        private var colorBuffer = GL.NONE

        private val numVertices by lazy { numCoords / COORDS_PER_VERTEX }
        private val numCoords by lazy { vertexTris.size }
        private val vertexTris by lazy { faces.flatMap { it.toVertexBuffer() }.toFloatArray() }
        //private val vertexTris = floatArrayOf(-100f, 100f, 30f, 100f, -100f, 30f, 100f, 100f, 50f)
        private val vertexTrisBuffer by lazy { FloatBuffer.wrap(vertexTris) }

        private val normalTris by lazy { faces.flatMap { it.toNormalBuffer() }.toFloatArray() }
        private val normalTrisBuffer by lazy { FloatBuffer.wrap(normalTris) }

        private val colorTris by lazy { faces.flatMap { it.toColorBuffer() }.toFloatArray() }
        private val colorTrisBuffer by lazy { FloatBuffer.wrap(colorTris) }

        private val program by lazy { makeProgram(SHADER_VERTEX, SHADER_FRAGMENT) }
        private val locAttrPosition by lazy { gl.getAttribLocation(program, "a_position") }
        private val locAttrNormal by lazy { gl.getAttribLocation(program, "a_normal") }
        private val locAttrColor by lazy { gl.getAttribLocation(program, "a_color") }
        private val locUnifMvp by lazy { gl.getUniformLocation(program, "u_mvp") }
        private val locUnifScale by lazy { gl.getUniformLocation(program, "u_scale") }

        companion object {
            private val SHADER_VERTEX = """
                attribute vec3 a_position;
                attribute vec3 a_normal;
                attribute vec3 a_color;
                uniform mat4 u_mvp;
                uniform float u_scale;
                varying vec3 v_color;
                
                void main()  {
                    gl_Position = u_mvp * vec4(a_position * u_scale, 1.0);
                    //gl_Normal = vec4(a_normal, 1.0);
                    v_color = a_color;
                }
                """.trimIndent()
            val SHADER_FRAGMENT = """
                precision mediump float;
                varying vec3 v_color;
    
                void main() {
                    //gl_FragColor = vec4(1, 0, 0.5, 1);
                    gl_FragColor = vec4(v_color, 1);
                }
                """.trimIndent()
        }

        class Vertex(val x: Double, val y: Double, val z: Double)
        class VertexNormal(val x: Double, val y: Double, val z: Double)
        class VertexTexture()
        class VertexColor(val r: Double, val g: Double, val b: Double)
        class FVertex(val coords: Vertex, val texture: VertexTexture?=null, val normal: VertexNormal?=null, val color: VertexColor?=null)
        interface Face {
            fun toTris(): Iterable<TriFace>
            fun toVertexBuffer() = toTris().flatMap { it.toVertexBuffer() }
            fun toNormalBuffer() = toTris().flatMap { it.toNormalBuffer() }
            fun toColorBuffer() = toTris().flatMap { it.toColorBuffer() }
        }
        class TriFace(val v1: FVertex, val v2: FVertex, val v3: FVertex) : Face {
            override fun toTris(): Iterable<TriFace> = listOf(this)
            override fun toVertexBuffer() = arrayOf(v1, v2, v3).flatMap { listOf(it.coords.x.toFloat(), it.coords.y.toFloat(), it.coords.z.toFloat()) }
            override fun toNormalBuffer() = arrayOf(v1, v2, v3).flatMap { listOf(it.normal?.x?.toFloat() ?: 0f, it.normal?.y?.toFloat() ?: 0f, it.normal?.z?.toFloat() ?: 0f) }
            override fun toColorBuffer() = arrayOf(v1, v2, v3).flatMap { listOf(it.color?.r?.toFloat() ?: 1f, it.color?.g?.toFloat() ?: 0f, it.color?.b?.toFloat() ?: 0.5f) }
        }
        class TriStripFace(val vertices: List<FVertex>) : Face {
            // Thanks to https://gamedev.stackexchange.com/a/207779 for this method
            override fun toTris(): Iterable<TriFace> {
                var start = 0
                var end = vertices.size - 1
                val tris = mutableListOf<TriFace>()
                while (true) {
                    if (end - start <= 1) { break }
                    tris.add(TriFace(vertices[start], vertices[end-1], vertices[end]))
                    end--
                    if (end - start <= 1) { break }
                    tris.add(TriFace(vertices[start], vertices[start+1], vertices[end]))
                    start++
                }
                return tris
            }
        }
    }

    /*
        =======================================
            Wavefront Models (.obj + .mtl)
        =======================================
     */

    class WavefrontModel : Base3DModel {
        //constructor(zip: InputStream) : super()
        constructor(obj: InputStream, mtl: InputStream?) : super(parse(obj) { mtl })
        constructor(obj: InputStream, resolver: (filename: String) -> InputStream?) : super(parse(obj, resolver))

        companion object {
            private fun parse(obj: InputStream, resolver: (filename: String) -> InputStream?): Iterable<Face> {
                val vertices = mutableListOf<Vertex>()
                val vertexNormals = mutableListOf<VertexNormal>()
                val faces = mutableListOf<Face>()
                val colors = mutableMapOf<String, VertexColor>()
                var currentColor: VertexColor? = null
                obj.bufferedReader().lines().forEach { line ->
                    val parts = line.trim().split(" ").map { it.trim() }
                    when (parts[0]) {
                        "mtllib" -> resolver(parts[1])?.let { loadMtl(it, resolver).forEach { (s, vertexColor) -> colors[s] = vertexColor } }
                        "usemtl" -> currentColor = colors[parts[1]]
                        "v" -> vertices.add(Vertex(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                        "vn" -> vertexNormals.add(VertexNormal(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                        "f" -> faces.add(TriStripFace(parts.mapIndexedNotNull { index, s ->
                            if (index == 0) { return@mapIndexedNotNull null }
                            val indices = s.split("/")
                            val vertex = vertices[indices[0].toInt()-1]
                            val vertexTexture = null
                            val vertexNormal = indices[2].toIntOrNull()?.let { vertexNormals[it-1] }
                            return@mapIndexedNotNull FVertex(vertex, vertexTexture, vertexNormal, currentColor)
                        }))
                    }
                }
                return faces
            }

            private fun loadMtl(mtl: InputStream, resolver: (filename: String) -> InputStream?): Map<String, VertexColor> {
                val colors = mutableMapOf<String, VertexColor>()
                var currentColorName = ""
                mtl.bufferedReader().lines().forEach { line ->
                    val parts = line.trim().split(" ").map { it.trim() }
                    when (parts[0]) {
                        "newmtl" -> currentColorName = parts[1]
                        "Kd" -> colors[currentColorName] = VertexColor(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
                    }
                }
                return colors
            }
        }
    }

    /*
        ==================================
            2D Markers
        ==================================
     */

    class MarkerModel(private val bitmap: Bitmap) : Model {
        constructor(drawable: Drawable) : this(drawable.toBitmap(config=Bitmap.Config.ARGB_8888))
        constructor(drawable: Drawable, maxWidth: Int, maxHeight: Int?=null) : this(drawable.toBitmap(maxWidth, maxHeight ?: ((drawable.intrinsicHeight.toDouble() / drawable.intrinsicWidth) * maxWidth).roundToInt(), Bitmap.Config.ARGB_8888))

        private var isReady = false
        private var vertexBuffer = GL.NONE
        private var textureBuffer = GL.NONE
        var scale = 1.0
        var alpha = 1.0

        private val program by lazy { makeProgram(SHADER_VERTEX, SHADER_FRAGMENT) }
        private val locAttribCorner by lazy { gl.getAttribLocation(program, "a_corner") }
        private val locUnifScale by lazy { gl.getUniformLocation(program, "u_scale") }
        private val locUnifMvp by lazy { gl.getUniformLocation(program, "u_mvp") }
        private val locUnifAlpha by lazy { gl.getUniformLocation(program, "u_alpha") }
        private val locUnifTexture by lazy { gl.getUniformLocation(program, "u_texture") }

        override fun setup() {
            if (isReady) { return }

            vertexBuffer = IntBuffer.allocate(1).also { gl.genBuffers(1, it) }[0]

            GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
            val buffer = FloatBuffer.wrap(floatArrayOf(-0.5f, 0f,  -0.5f, 1f,  0.5f, 0f,  0.5f, 1f))
            gl.bufferData(GL.ARRAY_BUFFER, 8 * BYTES_PER_FLOAT, buffer, GL.STATIC_DRAW)

            textureBuffer = IntBuffer.allocate(1).also { gl.genTextures(1, it) }[0] // TODO(SEG): reenable

            GLState.bindTex2D(textureBuffer)
            gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
            gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
            gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
            gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL.TEXTURE_2D, 0, bitmap, 0)

            isReady = true
        }
        override fun render(viewport: GLViewport, position: GeoPoint, scale: Double, alpha: Double) {
            //val bbox = Box()
            //viewport.getBBox(bbox, 0)
            val x = MercatorProjection.longitudeToX(position.longitude) - viewport.pos.x
            val y = MercatorProjection.latitudeToY(position.latitude) - viewport.pos.y
            //val point = Point()
            //viewport.toScreenPoint(x, y, point)
            //if (!bbox.contains(point)) { return }

            GLState.useProgram(program)
            GLState.blend(true)
            GLState.test(true, false)
            //val wasDepthMaskEnabled = IntBuffer.allocate(1).also { gl.getIntegerv(GL.DEPTH_WRITEMASK, it) }[0] != 0
            gl.depthMask(true)
            gl.depthFunc(GL.ALWAYS)

            if (viewport.pos.zoomLevel < 18) { gl.enable(GL.CULL_FACE) }

            val widthGl = 2 * this.scale * scale * bitmap.width / viewport.width
            val heightGl = 2 * this.scale  * scale * bitmap.height / viewport.height
            gl.uniform2f(locUnifScale, widthGl.toFloat(), heightGl.toFloat())

            val tileScale = Tile.SIZE * viewport.pos.scale

            viewport.mvp.setTransScale((x * tileScale).toFloat(), (y * tileScale).toFloat(), 1f)
            viewport.mvp.multiplyMM(viewport.viewproj, viewport.mvp)
            viewport.mvp.setAsUniform(locUnifMvp)

            GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
            gl.vertexAttribPointer(locAttribCorner, 2, GL.FLOAT, false, 0, 0)

            gl.uniform1f(locUnifAlpha, (alpha * this.alpha).toFloat())

            gl.activeTexture(GL.TEXTURE0)
            GLState.bindTex2D(textureBuffer)
            gl.uniform1i(locUnifTexture, 0)

            gl.drawArrays(GL.TRIANGLE_STRIP, 0, 4)

            gl.depthMask(false) // Reset depth mask value
            gl.disable(GL.CULL_FACE)
            gl.depthFunc(GL.LESS)
        }

        companion object {
            val SHADER_VERTEX = """
                attribute vec2 a_corner;
                uniform mat4 u_mvp;
                uniform vec2 u_scale;
                varying vec2 v_texture;
                
                void main()  {
                    vec4 position = u_mvp * vec4(0, 0, 0, 1);
                    gl_Position = position / position.w + vec4(u_scale * a_corner, 0, 0);
                    v_texture = a_corner;
                }
            """.trimIndent()
            val SHADER_FRAGMENT = """
                uniform sampler2D u_texture;
                uniform float u_alpha;
                varying vec2 v_texture;
                
                void main() {
                    vec4 color = texture2D(u_texture, vec2(1, -1) * v_texture + vec2(0.5, 1));
                    gl_FragColor = color * u_alpha;
                }
            """.trimIndent()
        }
    }
}