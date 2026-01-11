// Fragment Shader: Smart Eraser with Procedural Inpainting
// LeapmotorTranslator - Optimized for Adreno 640 GPU / Snapdragon 8155
// OpenGL ES 3.0

#version 300 es

precision highp float;
precision highp int;

// Input from vertex shader
in vec2 vTexCoord;
in vec2 vScreenPos;

// Output color
out vec4 fragColor;

// Uniforms
uniform vec2 uResolution;           // Screen resolution (1920.0, 1080.0)
uniform float uTime;                // Animation time for noise variation
uniform int uBoxCount;              // Number of active bounding boxes
uniform vec4 uBoundingBoxes[32];    // Bounding boxes: (x, y, width, height) in pixels

// ============================================================================
// SIMPLEX NOISE IMPLEMENTATION
// High-frequency procedural noise for text scrambling
// ============================================================================

// Permutation polynomial
vec3 permute(vec3 x) {
    return mod(((x * 34.0) + 1.0) * x, 289.0);
}

// 2D Simplex Noise
float snoise(vec2 v) {
    const vec4 C = vec4(
        0.211324865405187,   // (3.0 - sqrt(3.0)) / 6.0
        0.366025403784439,   // 0.5 * (sqrt(3.0) - 1.0)
        -0.577350269189626,  // -1.0 + 2.0 * C.x
        0.024390243902439    // 1.0 / 41.0
    );
    
    // First corner
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    
    // Other corners
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    
    // Permutations
    i = mod(i, 289.0);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    
    // Gradients
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    
    // Normalize
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    
    // Compute final noise value
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

// ============================================================================
// FRACTAL BROWNIAN MOTION (FBM)
// Multi-octave noise for natural-looking texture
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
// BLUR APPROXIMATION
// Fast Gaussian-like blur using noise-based sampling
// ============================================================================

float gaussianWeight(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

vec4 blurredNoise(vec2 uv, float radius) {
    float sigma = radius * 0.5;
    float totalWeight = 0.0;
    float noiseSum = 0.0;
    
    // 5x5 kernel approximation
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * radius * 0.5;
            float weight = gaussianWeight(length(offset), sigma);
            
            vec2 samplePos = uv + offset / uResolution;
            float n = fbm(samplePos * 50.0 + uTime * 0.1, 4);
            
            noiseSum += n * weight;
            totalWeight += weight;
        }
    }
    
    float finalNoise = noiseSum / totalWeight;
    
    // Map to dark grayscale to blend with car UI background
    float grayscale = finalNoise * 0.15 + 0.05;  // Range: 0.05 - 0.20 (very dark)
    
    return vec4(grayscale, grayscale, grayscale, 1.0);
}

// ============================================================================
// BOUNDING BOX CHECK
// Determine if current pixel is inside any text bounding box
// ============================================================================

bool isInsideBoundingBox(vec2 screenPos, out float distanceToEdge) {
    distanceToEdge = 1000.0;
    
    for (int i = 0; i < 32; i++) {
        if (i >= uBoxCount) break;
        
        vec4 box = uBoundingBoxes[i];
        float left = box.x;
        float top = box.y;
        float right = box.x + box.z;
        float bottom = box.y + box.w;
        
        // Check if inside this box
        if (screenPos.x >= left && screenPos.x <= right &&
            screenPos.y >= top && screenPos.y <= bottom) {
            
            // Calculate distance to nearest edge for smooth falloff
            float dx = min(screenPos.x - left, right - screenPos.x);
            float dy = min(screenPos.y - top, bottom - screenPos.y);
            distanceToEdge = min(dx, dy);
            
            return true;
        }
    }
    
    return false;
}

// ============================================================================
// EDGE SOFTENING
// Smooth transition at bounding box edges
// ============================================================================

float edgeSoftness(float distanceToEdge) {
    // Soft edge within 4 pixels of boundary
    const float edgeWidth = 4.0;
    return smoothstep(0.0, edgeWidth, distanceToEdge);
}

// ============================================================================
// MAIN SHADER
// ============================================================================

void main() {
    float distanceToEdge;
    bool insideBox = isInsideBoundingBox(vScreenPos, distanceToEdge);
    
    if (insideBox) {
        // === INSIDE TEXT BOX: Apply eraser effect ===
        
        // Generate high-frequency noise
        vec2 noiseCoord = vScreenPos / uResolution;
        vec4 noiseColor = blurredNoise(noiseCoord, 3.0);
        
        // Add subtle animated variation
        float timeVariation = snoise(noiseCoord * 10.0 + uTime * 0.5) * 0.02;
        noiseColor.rgb += timeVariation;
        
        // Apply edge softness for smooth blending
        float alpha = edgeSoftness(distanceToEdge);
        
        // Output: dark noisy texture that covers Chinese text
        fragColor = vec4(noiseColor.rgb, alpha);
        
    } else {
        // === OUTSIDE TEXT BOX: Fully transparent ===
        // Allows map, camera, and other UI to show through
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
    }
}
