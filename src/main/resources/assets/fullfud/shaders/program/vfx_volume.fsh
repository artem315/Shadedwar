#version 150

uniform sampler2D Sampler0;
uniform float Time;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in vec2 texCoord;
in vec3 viewPosition;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

void main() {
    vec4 textureSample = texture(Sampler0, texCoord);
    vec2 centered = texCoord * 2.0 - 1.0;
    float radius2 = dot(centered, centered);
    if (radius2 > 1.0) discard;
    float density = pow(max(0.0, 1.0 - radius2), 2.4);
    // The MTS radial texture breaks up the hard billboard boundary while
    // retaining a stable, low-cost volumetric silhouette.
    density *= 0.72 + textureSample.a * 0.28;
    float noise = hash21(floor(texCoord * 9.0) + vec2(floor(Time * 2.0)));
    float pulse = 0.88 + noise * 0.12;
    float alpha = vertexColor.a * density * pulse;
    vec3 color = vertexColor.rgb * (0.7 + density * 0.8);
    float distanceFog = FogEnd > FogStart ? smoothstep(FogStart, FogEnd, length(viewPosition)) : 0.0;
    color = mix(color, FogColor.rgb, distanceFog * FogColor.a);
    fragColor = vec4(color, alpha);
}
