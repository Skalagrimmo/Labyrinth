#version 300 es
/**
 * Cyberspace 3D Vertex Shader (#version 300 es)
 * Handles 3D perspective projection, model transformations, normal vector updates,
 * and wave motion simulation for cyberspace matrix nodes and grid structures.
 */

// Vertex Attributes
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;

// Uniform Transformation Matrices
uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uNormalMatrix;
uniform float uTime;
uniform float uWaveAmplitude;

// Varying outputs to Fragment Shader
out vec3 vWorldPosition;
out vec3 vNormal;
out vec2 vTexCoord;
out float vDistanceFogFactor;

void main() {
    // Apply dynamic cyberspace wave deformation to vertex position
    vec3 transformedPosition = aPosition;
    if (uWaveAmplitude > 0.0) {
        float wave = sin(aPosition.x * 2.0 + uTime * 3.0) * cos(aPosition.z * 2.0 + uTime * 2.5);
        transformedPosition.y += wave * uWaveAmplitude;
    }

    // Compute World Position
    vec4 worldPos = uModelMatrix * vec4(transformedPosition, 1.0);
    vWorldPosition = worldPos.xyz;

    // Transform Normal vector to world space
    vNormal = normalize(vec3(uNormalMatrix * vec4(aNormal, 0.0)));

    // Pass through texture coordinates
    vTexCoord = aTexCoord;

    // Calculate distance for depth/fog calculations
    float dist = length(vWorldPosition.xz);
    vDistanceFogFactor = clamp(1.0 - (dist / 25.0), 0.0, 1.0);

    // Final clipping space coordinate projection
    gl_Position = uMVPMatrix * vec4(transformedPosition, 1.0);
}
