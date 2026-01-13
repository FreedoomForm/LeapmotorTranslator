// Fragment Shader: Smart Eraser with Procedural Inpainting
// LeapmotorTranslator - Optimized for Adreno 640 GPU / Snapdragon 8155
// OpenGL ES 3.0
// FIXED: Dynamic bounding box coverage, improved coordinate handling

#version 300 es

precision highp float;
precision highp int;

// Input from vertex shader
in vec2 vTexCoord;
in vec2 vScreenPos;

// Output color
out vec4 fragColor;

// Uniforms
uniform vec2 uResolution;           // Screen resolution in pixels
uniform float uTime;                // Animation time for noise variation
uniform int uIsLightBackground;     // 0 = Dark (default), 1 = Light

// Padding added to each bounding box to ensure complete coverage
const float BOX_PADDING = 4.0;

// ============================================================================
// SIMPLEX NOISE IMPLEMENTATION
// High-frequency procedural noise for background generation
// ============================================================================

vec3 permute(vec3 x) {
    return mod(((x * 34.0) + 1.0) * x, 289.0);
}

float snoise(vec2 v) {
    const vec4 C = vec4(
        0.211324865405187,
        0.366025403784439,
        -0.577350269189626,
        0.024390243902439
    );
    
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    
    i = mod(i, 289.0);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

// ============================================================================
// FRACTAL BROWNIAN MOTION (FBM)
// ============================================================================

float fbm(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    float maxValue = 0.0;
    
    for (int i = 0; i < octaves; i++) {
        value += amplitude * snoise(p * frequency);
        maxValue += amplitude;
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    
    return value / maxValue;
}

// ============================================================================
// SOLID COLOR FILL (Smart Adaptive)
// ============================================================================

vec4 getSolidFill(vec2 screenPos) {
    if (uIsLightBackground == 1) {
        // LIGHT THEME: Micro-Noise Texture
        // Darkened to 0.85 so user can SEE the square working (was 0.96/White)
        float noise = fbm(screenPos * 0.05 + uTime * 0.02, 2) * 0.015;
        float base = 0.85 + noise; 
        return vec4(base, base, base, 1.0);
    } else {
        // DARK THEME: Textured fill
        // Matches grainy car dashboard plastics/materials
        float noise = fbm(screenPos * 0.02 + uTime * 0.05, 3);
        float base = 0.10;
        float val = base + noise * 0.05; 
        return vec4(val, val, val, 1.0);
    }
}

// ============================================================================
// BOUNDING BOX COLLISION DETECTION
// Returns coverage factor (0.0 = outside, 1.0 = fully inside)
// ============================================================================

float getBoxCoverage(vec2 screenPos) {
    float maxCoverage = 0.0;
    
    for (int i = 0; i < 32; i++) {
        if (i >= uBoxCount) break;
        
        vec4 box = uBoundingBoxes[i];
        
        // Apply padding to ensure complete coverage
        float left   = box.x - BOX_PADDING;
        float top    = box.y - BOX_PADDING;
        float right  = box.x + box.z + BOX_PADDING;
        float bottom = box.y + box.w + BOX_PADDING;
        
        // Check if inside this expanded box
        if (screenPos.x >= left && screenPos.x <= right &&
            screenPos.y >= top && screenPos.y <= bottom) {
            
            // Calculate distance to nearest edge for smooth falloff
            float dx = min(screenPos.x - left, right - screenPos.x);
            float dy = min(screenPos.y - top, bottom - screenPos.y);
            float distToEdge = min(dx, dy);
            
            // Soft edge transition (Sharpened to 1.0px to avoid transparent look)
            float edgeSoftness = smoothstep(0.0, 1.0, distToEdge);
            maxCoverage = max(maxCoverage, edgeSoftness);
        }
    }
    
    return maxCoverage;
}

// ============================================================================
// MAIN SHADER
// ============================================================================

void main() {
    // Get coverage factor for current pixel
    float coverage = getBoxCoverage(vScreenPos);
    
    if (coverage > 0.001) {
        // === INSIDE OR NEAR TEXT BOX: Apply eraser effect ===
        
        // Get fill color (solid dark with subtle variation)
        vec4 fillColor = getSolidFill(vScreenPos);
        
        // Apply coverage as alpha for smooth edges
        // RESTORED FULL OPACITY: User reported "Chinese words not working" (still visible).
        // Removing transparency to ensure complete erasing.
        fragColor = vec4(fillColor.rgb, coverage);
        
    } else {
        // === OUTSIDE TEXT BOXES: Fully transparent ===
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
    }
}
