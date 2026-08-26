#version 300 es
/**
 * Cyberspace Digital Rain & Scanline Fragment Shader (#version 300 es)
 * Generates matrix digital rain streams, CRT scanlines, screen vignetting, 
 * and dynamic cyberpunk neon glitches as an immersive overlay effect.
 */
precision mediump float;

in vec2 vTexCoord;

uniform float uTime;
uniform vec2 uResolution;
uniform vec4 uRainColor;

out vec4 fragColor;

// Pseudo-random function for digital rain column variation
float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

// Procedural digital rain character stream intensity
float digitalRain(vec2 uv, float time) {
    // Divide screen into vertical rain columns
    float columns = 40.0;
    float colId = floor(uv.x * columns);
    
    // Per-column random speed and drop offset
    float speed = 0.5 + 1.5 * hash(colId * 17.13);
    float offset = hash(colId * 91.41) * 10.0;
    
    // Vertical moving drop position
    float yPos = fract(uv.y * 1.5 - time * speed * 0.4 + offset);
    
    // Head glow and trailing fade
    float head = smoothstep(0.95, 1.0, yPos);
    float tail = pow(yPos, 4.0) * 0.7;
    
    // Digital bit quantization / pixelation character glitch simulation
    float charNoise = step(0.5, hash(floor(uv.y * 50.0) + floor(time * 12.0) + colId * 33.0));
    
    return (head * 1.8 + tail) * charNoise;
}

void main() {
    vec2 uv = vTexCoord;

    // 1. Digital Rain Intensity
    float rain = digitalRain(uv, uTime);

    // 2. CRT Scanline Effect (Horizontal alternating dark lines)
    float scanline = 0.85 + 0.15 * sin(uv.y * uResolution.y * 1.5 + uTime * 8.0);

    // 3. Screen Edge Vignetting (Darkened edges for immersion)
    vec2 uvCenter = uv * (1.0 - uv.yx);
    float vig = uvCenter.x * uvCenter.y * 15.0;
    vig = clamp(pow(vig, 0.25), 0.0, 1.0);

    // 4. Matrix Green / Cyan Neon Palette
    vec3 rainRGB = uRainColor.rgb * rain;
    vec3 scanlineGlow = vec3(0.0, 0.15, 0.1) * (1.0 - scanline);

    vec3 finalColor = (rainRGB + scanlineGlow) * vig;
    float alpha = clamp(rain * 0.65 + (1.0 - scanline) * 0.12, 0.0, 0.85);

    fragColor = vec4(finalColor, alpha);
}
