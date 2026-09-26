#version 150

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in vec2 texCoord;
in float distanceFromCamera;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radius = length(centered);
    if (radius > 1.0) discard;
    float alpha = vertexColor.a * pow(max(0.0, 1.0 - radius), 1.7);
    float fog = FogEnd > FogStart ? smoothstep(FogStart, FogEnd, distanceFromCamera) : 0.0;
    alpha *= 1.0 - fog * FogColor.a;
    fragColor = vec4(0.0, 0.0, 0.0, alpha);
}
