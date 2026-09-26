#version 150

uniform sampler2D Sampler0;
uniform float Time;
uniform float TrailLifetime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int LightCount;
uniform vec3 LightPos0;
uniform vec3 LightColor0;
uniform float LightRadius0;
uniform float LightIntensity0;
uniform vec3 LightPos1;
uniform vec3 LightColor1;
uniform float LightRadius1;
uniform float LightIntensity1;
uniform vec3 LightPos2;
uniform vec3 LightColor2;
uniform float LightRadius2;
uniform float LightIntensity2;
uniform vec3 LightPos3;
uniform vec3 LightColor3;
uniform float LightRadius3;
uniform float LightIntensity3;
uniform vec3 LightPos4;
uniform vec3 LightColor4;
uniform float LightRadius4;
uniform float LightIntensity4;
uniform vec3 LightPos5;
uniform vec3 LightColor5;
uniform float LightRadius5;
uniform float LightIntensity5;
uniform vec3 LightPos6;
uniform vec3 LightColor6;
uniform float LightRadius6;
uniform float LightIntensity6;
uniform vec3 LightPos7;
uniform vec3 LightColor7;
uniform float LightRadius7;
uniform float LightIntensity7;

in vec4 vertexColor;
in vec2 texCoord;
in vec3 viewPosition;
in vec3 viewNormal;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 contribution(vec3 position, vec3 normal, vec3 lightPosition, vec3 lightColor, float radius, float intensity) {
    vec3 toLight = lightPosition - position;
    float distanceToLight = length(toLight);
    if (distanceToLight >= radius) {
        return vec3(0.0);
    }
    float attenuation = pow(max(0.0, 1.0 - distanceToLight / radius), 2.0);
    float diffuse = max(dot(normal, normalize(toLight)), 0.0);
    return lightColor * (attenuation * intensity * (0.18 + diffuse * 0.82));
}

void main() {
    vec4 texel = texture(Sampler0, texCoord);
    float noise = hash21(floor(texCoord * vec2(19.0, 7.0)) + floor(Time * 3.0));
    float edge = smoothstep(0.0, 0.22, texel.a) * (0.78 + noise * 0.22);
    vec3 normal = normalize(viewNormal);
    vec3 radiance = vec3(0.12, 0.14, 0.18);
    radiance += contribution(viewPosition, normal, LightPos0, LightColor0, LightRadius0, LightIntensity0) * step(0.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos1, LightColor1, LightRadius1, LightIntensity1) * step(1.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos2, LightColor2, LightRadius2, LightIntensity2) * step(2.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos3, LightColor3, LightRadius3, LightIntensity3) * step(3.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos4, LightColor4, LightRadius4, LightIntensity4) * step(4.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos5, LightColor5, LightRadius5, LightIntensity5) * step(5.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos6, LightColor6, LightRadius6, LightIntensity6) * step(6.5, float(LightCount));
    radiance += contribution(viewPosition, normal, LightPos7, LightColor7, LightRadius7, LightIntensity7) * step(7.5, float(LightCount));
    vec3 base = vertexColor.rgb * (0.52 + noise * 0.18);
    vec3 color = base * (0.34 + radiance * 0.28) + vertexColor.rgb * radiance * 0.12;
    float distanceFog = FogEnd > FogStart ? smoothstep(FogStart, FogEnd, length(viewPosition)) : 0.0;
    color = mix(color, FogColor.rgb, distanceFog * FogColor.a);
    float alpha = texel.a * vertexColor.a * edge;
    if (alpha < 0.004) discard;
    fragColor = vec4(color, alpha);
}
