// Fragment Shader: Premium Smart Eraser with Advanced Visual Effects
// LeapmotorTranslator - Optimized for Adreno 640 GPU / Snapdragon 8155
// OpenGL ES 3.0
// ENHANCED: Premium gradients, soft glow borders, chromatic noise, shimmer effects

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
uniform int uBoxCount;              // Number of active boxes
uniform vec4 uBoundingBoxes[32];    // Array of boxes (x, y, w, h)

// Padding added to each bounding box to ensure complete coverage
const float BOX_PADDING = 6.0;

// Premium visual settings
const float GLOW_INTENSITY = 0.15;
const float GLOW_RADIUS = 8.0;
const float SHIMMER_SPEED = 0.8;
const float GRADIENT_STRENGTH = 0.08;

// ============================================================================
// ADVANCED HASH FUNCTIONS FOR HIGH-QUALITY NOISE
// ============================================================================

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// ============================================================================
// SIMPLEX NOISE IMPLEMENTATION - ENHANCED
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
// VALUE NOISE - Smoother alternative for gradients
// ============================================================================

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    
    // Improved smoothstep for smoother interpolation
    vec2 u = f * f * (3.0 - 2.0 * f);
    
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// ============================================================================
// FRACTAL BROWNIAN MOTION (FBM) - ENHANCED
// ============================================================================

float fbm(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    float maxValue = 0.0;
    
    // Rotation matrix for domain warping (reduces grid artifacts)
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    
    for (int i = 0; i < octaves; i++) {
        value += amplitude * snoise(p * frequency);
        maxValue += amplitude;
        amplitude *= 0.5;
        frequency *= 2.0;
        p = rot * p; // Domain warping
    }
    
    return value / maxValue;
}

// Enhanced FBM with chromatic variation
vec3 fbmChromatic(vec2 p, int octaves) {
    float r = fbm(p + vec2(0.0, 0.0), octaves);
    float g = fbm(p + vec2(5.2, 1.3), octaves);
    float b = fbm(p + vec2(1.7, 9.2), octaves);
    return vec3(r, g, b);
}

// ============================================================================
// PREMIUM GRADIENT FILL
// ============================================================================

vec4 getPremiumFill(vec2 screenPos, vec2 boxCenter, vec2 boxSize) {
    // Normalized position within the box (-1 to 1)
    vec2 localPos = (screenPos - boxCenter) / (boxSize * 0.5);
    
    // Subtle radial gradient from center
    float radialGradient = 1.0 - length(localPos) * GRADIENT_STRENGTH;
    
    // Animated shimmer effect
    float shimmer = sin(screenPos.x * 0.03 + screenPos.y * 0.02 + uTime * SHIMMER_SPEED) * 0.5 + 0.5;
    shimmer = shimmer * shimmer * 0.02; // Subtle shimmer
    
    if (uIsLightBackground == 1) {
        // ====== LIGHT THEME: Premium matte white with subtle warmth ======
        
        // Multi-layer noise for natural paper-like texture
        float coarseNoise = fbm(screenPos * 0.015 + uTime * 0.008, 3) * 0.012;
        float fineNoise = valueNoise(screenPos * 0.08) * 0.008;
        float microDetail = snoise(screenPos * 0.3) * 0.003;
        
        // Base color with warm undertone (slight cream tint)
        float base = 0.92 + coarseNoise + fineNoise + microDetail;
        base *= radialGradient;
        base += shimmer;
        
        // Very subtle warm color variation
        vec3 color = vec3(base, base * 0.995, base * 0.985);
        
        return vec4(color, 1.0);
        
    } else {
        // ====== DARK THEME: Premium carbon fiber / dashboard look ======
        
        // Multi-layer noise for rich texture
        float coarseNoise = fbm(screenPos * 0.012 + uTime * 0.015, 4);
        float fineNoise = valueNoise(screenPos * 0.05 + uTime * 0.02) * 0.015;
        
        // Subtle chromatic variation for premium look
        vec3 chromaNoise = fbmChromatic(screenPos * 0.008, 2) * 0.015;
        
        // Base dark color with subtle blue undertone (car dashboard style)
        float base = 0.08 + coarseNoise * 0.04 + fineNoise;
        base *= radialGradient;
        base += shimmer * 0.5;
        
        // Subtle cool color shift for depth
        vec3 color = vec3(
            base + chromaNoise.r * 0.3,
            base + chromaNoise.g * 0.5,
            base + chromaNoise.b * 0.8
        );
        
        // Slight blue tint for automotive theme
        color.b += 0.008;
        
        return vec4(color, 1.0);
    }
}

// ============================================================================
// SOFT GLOW EFFECT
// ============================================================================

float getSoftGlow(vec2 screenPos, float distToEdge, float coverage) {
    // Animated glow pulse
    float pulse = sin(uTime * 2.0) * 0.3 + 0.7;
    
    // Glow extends beyond the box edges
    float glowFalloff = smoothstep(GLOW_RADIUS, 0.0, -distToEdge);
    
    // Combine with coverage for smooth transition
    return glowFalloff * GLOW_INTENSITY * pulse * (1.0 - coverage);
}

// ============================================================================
// ADVANCED BOUNDING BOX COLLISION DETECTION
// Returns: coverage factor, distance to edge, box center, box size
// ============================================================================

struct BoxInfo {
    float coverage;
    float distToEdge;
    vec2 center;
    vec2 size;
    bool inside;
};

BoxInfo getBoxInfo(vec2 screenPos) {
    BoxInfo info;
    info.coverage = 0.0;
    info.distToEdge = 1000.0;
    info.center = vec2(0.0);
    info.size = vec2(1.0);
    info.inside = false;
    
    float maxCoverage = 0.0;
    float minDistToEdge = 1000.0;
    
    for (int i = 0; i < 32; i++) {
        if (i >= uBoxCount) break;
        
        vec4 box = uBoundingBoxes[i];
        
        // Box boundaries with padding
        float left   = box.x - BOX_PADDING;
        float top    = box.y - BOX_PADDING;
        float right  = box.x + box.z + BOX_PADDING;
        float bottom = box.y + box.w + BOX_PADDING;
        
        // Calculate signed distance to box edges
        float dx = max(left - screenPos.x, screenPos.x - right);
        float dy = max(top - screenPos.y, screenPos.y - bottom);
        float signedDist = max(dx, dy);
        
        // Track minimum distance for glow effect
        if (abs(signedDist) < abs(minDistToEdge)) {
            minDistToEdge = signedDist;
            info.center = vec2(box.x + box.z * 0.5, box.y + box.w * 0.5);
            info.size = vec2(box.z + BOX_PADDING * 2.0, box.w + BOX_PADDING * 2.0);
        }
        
        // Check if inside this expanded box
        if (signedDist <= 0.0) {
            info.inside = true;
            
            // Calculate distance to nearest edge for smooth falloff
            float distToEdgeInner = min(
                min(screenPos.x - left, right - screenPos.x),
                min(screenPos.y - top, bottom - screenPos.y)
            );
            
            // Premium smooth edge transition with cubic easing
            float t = clamp(distToEdgeInner / 3.0, 0.0, 1.0);
            float edgeSoftness = t * t * (3.0 - 2.0 * t); // Smoothstep equivalent
            
            maxCoverage = max(maxCoverage, edgeSoftness);
        }
    }
    
    info.coverage = maxCoverage;
    info.distToEdge = minDistToEdge;
    
    return info;
}

// ============================================================================
// MAIN SHADER
// ============================================================================

void main() {
    // Get comprehensive box information
    BoxInfo boxInfo = getBoxInfo(vScreenPos);
    
    if (boxInfo.inside) {
        // === INSIDE TEXT BOX: Apply FULL OPACITY eraser to completely hide Chinese text ===
        
        // Get premium fill color with gradients and texture
        vec4 fillColor = getPremiumFill(vScreenPos, boxInfo.center, boxInfo.size);
        
        // CRITICAL: Use FULL OPACITY to completely hide Chinese text underneath
        // Only apply slight edge softness at the very border (last 2 pixels)
        float edgeAlpha = 1.0;
        if (boxInfo.coverage < 1.0) {
            // Only soften the extreme outer edge (last 2 pixels)
            edgeAlpha = smoothstep(0.0, 0.3, boxInfo.coverage);
        }
        
        // Ensure minimum 95% opacity even at edges to fully hide text
        float finalAlpha = max(edgeAlpha, 0.95);
        
        fragColor = vec4(fillColor.rgb, finalAlpha);
        
    } else if (boxInfo.distToEdge < GLOW_RADIUS && boxInfo.distToEdge > 0.0) {
        // === NEAR TEXT BOX: Subtle outer glow for premium look ===
        
        float glowAlpha = getSoftGlow(vScreenPos, boxInfo.distToEdge, boxInfo.coverage);
        
        if (glowAlpha > 0.001) {
            // Glow color based on theme
            vec3 glowColor;
            if (uIsLightBackground == 1) {
                glowColor = vec3(0.95, 0.93, 0.90); // Warm white glow
            } else {
                glowColor = vec3(0.15, 0.18, 0.25); // Cool blue glow
            }
            
            fragColor = vec4(glowColor, glowAlpha);
        } else {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        }
        
    } else {
        // === OUTSIDE TEXT BOXES: Fully transparent ===
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
    }
}
