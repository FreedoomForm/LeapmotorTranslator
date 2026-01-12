// Vertex Shader for Eraser Overlay
// LeapmotorTranslator - Optimized for Adreno 640
// FIXED: Proper coordinate transformation for Android screen coordinates

#version 300 es

precision highp float;

// Vertex attributes
layout(location = 0) in vec2 aPosition;   // NDC position (-1 to 1)
layout(location = 1) in vec2 aTexCoord;   // Texture coordinates (0 to 1)

// Output to fragment shader
out vec2 vTexCoord;
out vec2 vScreenPos;

// Uniforms
uniform vec2 uResolution;  // Screen resolution in pixels (e.g., 1920x1080)

void main() {
    // Pass through position in NDC
    gl_Position = vec4(aPosition, 0.0, 1.0);
    
    // Pass texture coordinates
    vTexCoord = aTexCoord;
    
    // Transform NDC (-1 to 1) to Android screen coordinates (0 to resolution)
    // Key insight:
    //   - OpenGL NDC: (-1,-1) = bottom-left, (1,1) = top-right
    //   - Android screen: (0,0) = top-left, (width,height) = bottom-right
    // 
    // X transformation: [-1, 1] -> [0, resolution.x]
    //   x_screen = (x_ndc + 1.0) * 0.5 * resolution.x
    //
    // Y transformation: [-1, 1] -> [resolution.y, 0] (inverted!)
    //   When y_ndc = 1.0 (top in GL), y_screen should be 0 (top in Android)
    //   When y_ndc = -1.0 (bottom in GL), y_screen should be resolution.y (bottom in Android)
    //   y_screen = (1.0 - y_ndc) * 0.5 * resolution.y
    
    vScreenPos = vec2(
        (aPosition.x + 1.0) * 0.5 * uResolution.x,
        (1.0 - aPosition.y) * 0.5 * uResolution.y
    );
}
