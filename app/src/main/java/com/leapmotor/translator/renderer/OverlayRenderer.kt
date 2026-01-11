package com.leapmotor.translator.renderer

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.io.BufferedReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 Renderer for the "Smart Eraser" overlay.
 * 
 * Renders procedural noise over Chinese text bounding boxes
 * to mask the original text before Russian translation is drawn on top.
 * 
 * Optimized for Qualcomm Adreno 640 GPU (Snapdragon 8155).
 */
class OverlayRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    // Shader program
    private var programId: Int = 0
    
    // Uniform locations
    private var uResolutionLoc: Int = -1
    private var uTimeLoc: Int = -1
    private var uBoxCountLoc: Int = -1
    private var uBoundingBoxesLoc: Int = -1
    
    // Attribute locations
    private var aPositionLoc: Int = 0
    private var aTexCoordLoc: Int = 1
    
    // Geometry buffers
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    
    // Screen resolution (Leapmotor C11: 1920x1080)
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f
    
    // Animation start time
    private var startTime: Long = 0L
    
    // Bounding boxes for text areas (max 32)
    private val boundingBoxes = FloatArray(32 * 4)  // x, y, width, height for each
    private var boxCount: Int = 0
    
    // Thread safety lock
    private val boxLock = Any()
    
    companion object {
        private const val TAG = "OverlayRenderer"
        private const val MAX_BOXES = 32
        
        // Full-screen quad vertices (NDC: -1 to 1)
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f,  // Bottom left
             1f, -1f,  // Bottom right
            -1f,  1f,  // Top left
             1f,  1f   // Top right
        )
        
        // Texture coordinates
        private val QUAD_TEX_COORDS = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set clear color to fully transparent
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        
        // Enable blending for transparency
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        
        // Initialize shaders
        initShaders()
        
        // Initialize geometry buffers
        initBuffers()
        
        // Record start time for animation
        startTime = SystemClock.elapsedRealtime()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Clear with transparent
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        // Use our shader program
        GLES30.glUseProgram(programId)
        
        // Update uniforms
        GLES30.glUniform2f(uResolutionLoc, screenWidth, screenHeight)
        
        // Calculate elapsed time for animation
        val elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000f
        GLES30.glUniform1f(uTimeLoc, elapsed)
        
        // Upload bounding boxes
        synchronized(boxLock) {
            GLES30.glUniform1i(uBoxCountLoc, boxCount)
            if (boxCount > 0) {
                GLES30.glUniform4fv(uBoundingBoxesLoc, boxCount, boundingBoxes, 0)
            }
        }
        
        // Bind vertex data
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(aPositionLoc, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(aPositionLoc)
        
        texCoordBuffer.position(0)
        GLES30.glVertexAttribPointer(aTexCoordLoc, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)
        GLES30.glEnableVertexAttribArray(aTexCoordLoc)
        
        // Draw full-screen quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        
        // Cleanup
        GLES30.glDisableVertexAttribArray(aPositionLoc)
        GLES30.glDisableVertexAttribArray(aTexCoordLoc)
    }
    
    /**
     * Update the bounding boxes for text areas to erase.
     * Called from the AccessibilityService when UI changes detected.
     * 
     * @param boxes List of RectF representing text bounding boxes in screen coordinates
     */
    fun updateBoundingBoxes(boxes: List<RectF>) {
        synchronized(boxLock) {
            boxCount = minOf(boxes.size, MAX_BOXES)
            
            for (i in 0 until boxCount) {
                val box = boxes[i]
                val offset = i * 4
                boundingBoxes[offset] = box.left
                boundingBoxes[offset + 1] = box.top
                boundingBoxes[offset + 2] = box.width()
                boundingBoxes[offset + 3] = box.height()
            }
        }
    }
    
    /**
     * Clear all bounding boxes (show nothing).
     */
    fun clearBoundingBoxes() {
        synchronized(boxLock) {
            boxCount = 0
        }
    }
    
    private fun initShaders() {
        // Load shader source code
        val vertexSource = loadRawResource(com.leapmotor.translator.R.raw.eraser_vertex)
        val fragmentSource = loadRawResource(com.leapmotor.translator.R.raw.eraser_fragment)
        
        // Compile shaders
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        
        // Link program
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)
        
        // Check link status
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Shader program link failed: $log")
        }
        
        // Get uniform locations
        uResolutionLoc = GLES30.glGetUniformLocation(programId, "uResolution")
        uTimeLoc = GLES30.glGetUniformLocation(programId, "uTime")
        uBoxCountLoc = GLES30.glGetUniformLocation(programId, "uBoxCount")
        uBoundingBoxesLoc = GLES30.glGetUniformLocation(programId, "uBoundingBoxes")
        
        // Delete shader objects (no longer needed after linking)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
    }
    
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        
        // Check compile status
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            val typeName = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw RuntimeException("$typeName shader compilation failed: $log")
        }
        
        return shader
    }
    
    private fun initBuffers() {
        // Vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_VERTICES)
        vertexBuffer.position(0)
        
        // Texture coordinate buffer
        texCoordBuffer = ByteBuffer.allocateDirect(QUAD_TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_TEX_COORDS)
        texCoordBuffer.position(0)
    }
    
    private fun loadRawResource(resourceId: Int): String {
        return context.resources.openRawResource(resourceId)
            .bufferedReader()
            .use(BufferedReader::readText)
    }
    
    /**
     * Release OpenGL resources.
     */
    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}

/**
 * Custom GLSurfaceView configured for transparent overlay rendering.
 */
class EraserSurfaceView(context: Context) : GLSurfaceView(context) {
    
    val renderer: OverlayRenderer
    
    init {
        // Configure for OpenGL ES 3.0
        setEGLContextClientVersion(3)
        
        // Enable transparency
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        
        // Create and set renderer
        renderer = OverlayRenderer(context)
        setRenderer(renderer)
        
        // Render only when dirty (on demand)
        renderMode = RENDERMODE_WHEN_DIRTY
        
        // Set transparent background
        setZOrderOnTop(true)
    }
    
    /**
     * Trigger a redraw.
     */
    fun refresh() {
        requestRender()
    }
    
    /**
     * Update bounding boxes and trigger redraw.
     */
    fun updateBoxes(boxes: List<RectF>) {
        renderer.updateBoundingBoxes(boxes)
        requestRender()
    }
    
    /**
     * Clear overlay.
     */
    fun clear() {
        renderer.clearBoundingBoxes()
        requestRender()
    }
}
