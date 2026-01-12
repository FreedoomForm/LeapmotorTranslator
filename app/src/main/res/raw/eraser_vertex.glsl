// Vertex Shader for Eraser Overlay
// LeapmotorTranslator - Optimized for Adreno 640

#version 300 es

precision highp float;

// Vertex attributes
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

// Output to fragment shader
out vec2 vTexCoord;
out vec2 vScreenPos;

// Uniforms
uniform vec2 uResolution;  // Screen resolution (1920, 1080)

void main() {
    // Convert position from normalized device coordinates
    gl_Position = vec4(aPosition, 0.0, 1.0);
    
    // Pass texture coordinates to fragment shader
    vTexCoord = aTexCoord;
    
    // Calculate screen position for bounding box checks
    // Calculate screen position for bounding box checks (Invert Y for Android top-left origin)
    // GL (0,0) is Bottom-Left. Android (0,0) is Top-Left.
    // map y: 1.0 (Top) -> 0.0, -1.0 (Bottom) -> 1.0
    vScreenPos = vec2(
        (aPosition.x + 1.0) * 0.5 * uResolution.x,
        (1.0 - aPosition.y) * 0.5 * uResolution.y
    );
}
