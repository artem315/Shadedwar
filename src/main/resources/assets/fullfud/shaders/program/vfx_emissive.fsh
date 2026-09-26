#version 150

// Purely emissive, additive (ONE, ONE) geometry: the FP-5 exhaust streak and
// the blinding nozzle glare.  Nothing here is lit; it IS the light source.

uniform float Time;
uniform float FogStart;
uniform float FogEnd;
// 0 = trail ribbon, 1 = nozzle glare billboard.
uniform float Mode;
// Scales the soft halo / streak terms (bloom toggle).
uniform float GlowStrength;

in vec4 vertexColor;
in vec2 texCoord;
in float vertexDistance;

out vec4 fragColor;

const vec3 WHITE_HOT = vec3(1.0, 0.97, 0.90);

void main() {
    vec3 color;
    float energy;
    if (Mode < 0.5) {
        // Ribbon: texCoord.y runs across the width.  A razor thin white core
        // plus a soft coloured halo reads as a long-exposure rocket streak.
        float x = texCoord.y * 2.0 - 1.0;
        float core = exp(-x * x * 26.0);
        float halo = exp(-x * x * 3.5) * (1.0 - x * x) * 0.32 * GlowStrength;
        energy = (core * 1.15 + halo) * vertexColor.a;
        color = mix(vertexColor.rgb, WHITE_HOT, core * 0.8);
    } else {
        // Glare: tiny over-exposed core, hot inner bloom, wide soft halo and a
        // horizontal lens streak.  All terms reach zero at the quad edge.
        vec2 p = texCoord * 2.0 - 1.0;
        float r2 = dot(p, p);
        float edge = clamp(1.0 - r2, 0.0, 1.0);
        float core = exp(-r2 * 140.0) * 2.4;
        float inner = exp(-r2 * 22.0) * 0.85;
        float halo = exp(-r2 * 4.2) * edge * 0.42 * GlowStrength;
        float sx = 1.0 - abs(p.x);
        float streak = exp(-abs(p.y) * 70.0) * sx * sx * 0.55 * GlowStrength;
        float flicker = 0.93 + 0.04 * sin(Time * 41.0) + 0.03 * sin(Time * 17.3 + 1.7);
        energy = (core + inner + halo + streak) * flicker * vertexColor.a;
        color = mix(vertexColor.rgb, WHITE_HOT, clamp(core + inner * 0.6, 0.0, 1.0));
    }
    // An emitter punches through fog far better than lit geometry does.
    float fog = FogEnd > FogStart ? smoothstep(FogStart, FogEnd, vertexDistance) : 0.0;
    energy *= 1.0 - fog * 0.5;
    if (energy < 0.002) discard;
    fragColor = vec4(color * energy, 1.0);
}
