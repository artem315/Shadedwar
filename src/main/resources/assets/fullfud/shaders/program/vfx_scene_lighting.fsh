#version 150

uniform sampler2D DepthSampler;
// Copy of the already rendered frame.  Needed so light is multiplied by the
// surface colour (true diffuse) instead of being pasted on top as a glow.
uniform sampler2D SceneSampler;
uniform mat4 ProjectionMat;
uniform mat4 ViewMat;
uniform mat4 InvProjectionMat;
uniform mat4 InvViewMat;
uniform vec2 TexelSize;
uniform vec3 CameraPos;
uniform float AmbientStrength;
uniform float BloomStrength;
uniform float SceneContribution;
uniform float NightBoost;
uniform float NightAmount;
uniform float AmbientFloor;
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

in vec2 texCoord;
out vec4 fragColor;

const int VISIBILITY_STEPS = 10;
const int AO_SAMPLES = 8;

float readDepth(vec2 uv) {
    return texture(DepthSampler, clamp(uv, 0.0, 1.0)).r;
}

/** View-space distance (blocks) from a [0,1] depth-buffer value. */
float linearDepth(float depth) {
    float ndcZ = depth * 2.0 - 1.0;
    return -ProjectionMat[3][2] / (-ndcZ - ProjectionMat[2][2]);
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

/** Per-pixel noise used to jitter shadow/AO rays so steps turn into soft penumbra instead of bands. */
float interleavedNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

vec3 worldFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPosition = InvProjectionMat * ndc;
    viewPosition /= viewPosition.w;
    return (InvViewMat * viewPosition).xyz;
}

/**
 * Screen-space shadow ray with a soft penumbra.  Marches from the shaded
 * surface toward the light and removes a fraction of visibility for every
 * occluded sample, which reads as long directional shadows cast by terrain and
 * entities rather than a flat contact disc.
 */
float visibilityToLight(vec3 worldPosition, vec3 normal, vec3 lightPosition, float radius, float jitter) {
    vec3 delta = lightPosition - worldPosition;
    float distanceToLight = length(delta);
    if (distanceToLight < 1.0E-3) {
        return 1.0;
    }
    vec3 direction = delta / distanceToLight;
    // Stop short of the emitter so the missile/flame itself never shadows the pad.
    float marchLength = max(0.0, min(distanceToLight - 1.2, radius));
    if (marchLength <= 0.05) {
        return 1.0;
    }
    // Normal offset keeps a surface from shadowing itself.
    vec3 origin = worldPosition + normal * 0.06;
    float visibility = 1.0;
    for (int i = 0; i < VISIBILITY_STEPS; i++) {
        float f = (float(i) + jitter) / float(VISIBILITY_STEPS);
        // Quadratic spacing: dense near the receiver (contact shadow), sparse
        // towards the light (long cast shadow).
        float t = f * f * marchLength + 0.08;
        vec3 samplePosition = origin + direction * t;
        vec4 viewPosition = ViewMat * vec4(samplePosition, 1.0);
        vec4 clipPosition = ProjectionMat * viewPosition;
        if (clipPosition.w <= 0.0) continue;
        vec3 ndc = clipPosition.xyz / clipPosition.w;
        vec2 sampleUv = ndc.xy * 0.5 + 0.5;
        if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) continue;
        float sceneDepth = readDepth(sampleUv);
        if (sceneDepth >= 0.99999) continue;
        float sampleDistance = -viewPosition.z;
        float sceneDistance = linearDepth(sceneDepth);
        float gap = sampleDistance - sceneDistance;
        // Thickness test: only geometry just in front of the ray occludes.  A
        // wall far in front of the ray in screen space must not cast a shadow.
        float thickness = 2.5 + t * 0.18;
        if (gap > 0.04 + sampleDistance * 0.004 && gap < thickness) {
            // Soft falloff towards the far end of the ray widens the penumbra.
            visibility -= (1.0 - f * 0.45) * (1.6 / float(VISIBILITY_STEPS));
        }
    }
    return clamp(visibility, 0.0, 1.0);
}

/**
 * Physical window: the light reaches its full footprint with a long inverse
 * square tail instead of dying abruptly at the radius, so distant walls still
 * catch the gradient the way they do in the reference lighting.
 */
float attenuation(float distanceToLight, float radius) {
    float x = clamp(distanceToLight / radius, 0.0, 1.0);
    float window = 1.0 - x * x;
    window *= window;
    float inverseSquare = 1.0 / (1.0 + 9.0 * x * x);
    return window * inverseSquare * 4.0;
}

/**
 * Accumulates one light: shadowed radiance (what actually reaches the
 * surface), unshadowed radiance (what would have, used to darken blocked
 * zones) and footprint influence (where this light owns the local exposure).
 */
void accumulate(
    vec3 worldPosition, vec3 normal, float jitter,
    vec3 lightPosition, vec3 lightColor, float radius, float intensity,
    inout vec3 radiance, inout vec3 unshadowed, inout float influence
) {
    vec3 toLight = lightPosition - worldPosition;
    float distanceToLight = length(toLight);
    if (distanceToLight >= radius || intensity <= 0.0) return;
    float x = distanceToLight / radius;
    influence += (1.0 - x * x) * (1.0 - x * x) * clamp(intensity * 0.25, 0.0, 1.0);
    // Pure Lambert N.L: back-facing surfaces receive nothing, so they stay in
    // the dark ambient instead of getting a uniform lift.
    float lambert = max(0.0, dot(normal, toLight / distanceToLight));
    if (lambert <= 0.0) return;
    vec3 direct = lightColor * attenuation(distanceToLight, radius) * intensity * lambert;
    unshadowed += direct;
    // Skip the ray march where the light is negligible anyway.
    if (luminance(direct) < 0.02) {
        radiance += direct;
        return;
    }
    radiance += direct * visibilityToLight(worldPosition, normal, lightPosition, radius, jitter);
}

/**
 * Light ambient occlusion from the depth buffer.  Darkens creases and the base
 * of walls, which is what stops the lighting from looking like a flat glow
 * decal pasted over the scene.
 */
float ambientOcclusion(vec2 uv, vec3 worldPosition, vec3 normal, float viewDistance, float jitter) {
    // Radius is constant in world space (~0.9 block), so corners darken the
    // same way near and far instead of a fixed pixel-size halo.
    float pixelRadius = clamp(0.9 * ProjectionMat[1][1] / max(viewDistance, 0.1) * 0.5 / TexelSize.y, 3.0, 48.0);
    float occlusion = 0.0;
    float angle = jitter * 6.2831853;
    for (int i = 0; i < AO_SAMPLES; i++) {
        float a = angle + float(i) * (6.2831853 / float(AO_SAMPLES));
        float r = pixelRadius * (0.35 + 0.65 * fract(jitter + float(i) * 0.618034));
        vec2 sampleUv = uv + vec2(cos(a), sin(a)) * r * TexelSize;
        if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) continue;
        float sampleDepth = readDepth(sampleUv);
        if (sampleDepth >= 0.99999) continue;
        vec3 delta = worldFromDepth(sampleUv, sampleDepth) - worldPosition;
        float distance = length(delta);
        if (distance < 1.0E-4) continue;
        float facing = max(0.0, dot(normal, delta / distance) - 0.08);
        occlusion += facing * (1.0 - smoothstep(0.4, 1.8, distance));
    }
    return clamp(1.0 - occlusion * (1.5 / float(AO_SAMPLES)), 0.30, 1.0);
}

void main() {
    float depth = readDepth(texCoord);
    // Sky and empty pixels are never relit: the vanilla sky must survive intact.
    if (depth >= 0.99999) discard;
    vec3 worldPosition = worldFromDepth(texCoord, depth);
    vec3 dX = dFdx(worldPosition);
    vec3 dY = dFdy(worldPosition);
    vec3 crossN = cross(dX, dY);
    float normLenSq = dot(crossN, crossN);
    vec3 toCam = CameraPos - worldPosition;
    vec3 normal;
    if (normLenSq < 1.0E-6 || !(normLenSq >= 1.0E-6) || abs(dFdx(depth)) > 0.02 || abs(dFdy(depth)) > 0.02) {
        float toCamLen = length(toCam);
        normal = toCamLen > 1.0E-4 ? toCam / toCamLen : vec3(0.0, 1.0, 0.0);
    } else {
        normal = crossN / sqrt(normLenSq);
        if (dot(normal, toCam) < 0.0) normal = -normal;
    }

    float viewDistance = linearDepth(depth);
    float jitter = interleavedNoise(gl_FragCoord.xy);
    vec3 radiance = vec3(0.0);
    vec3 unshadowed = vec3(0.0);
    float influence = 0.0;

    // Keep the contributions expanded instead of a loop so the fixed uniform
    // slots stay exactly as declared in the program JSON.
    if (LightCount > 0) accumulate(worldPosition, normal, jitter, LightPos0, LightColor0, LightRadius0, LightIntensity0, radiance, unshadowed, influence);
    if (LightCount > 1) accumulate(worldPosition, normal, jitter, LightPos1, LightColor1, LightRadius1, LightIntensity1, radiance, unshadowed, influence);
    if (LightCount > 2) accumulate(worldPosition, normal, jitter, LightPos2, LightColor2, LightRadius2, LightIntensity2, radiance, unshadowed, influence);
    if (LightCount > 3) accumulate(worldPosition, normal, jitter, LightPos3, LightColor3, LightRadius3, LightIntensity3, radiance, unshadowed, influence);
    if (LightCount > 4) accumulate(worldPosition, normal, jitter, LightPos4, LightColor4, LightRadius4, LightIntensity4, radiance, unshadowed, influence);
    if (LightCount > 5) accumulate(worldPosition, normal, jitter, LightPos5, LightColor5, LightRadius5, LightIntensity5, radiance, unshadowed, influence);
    if (LightCount > 6) accumulate(worldPosition, normal, jitter, LightPos6, LightColor6, LightRadius6, LightIntensity6, radiance, unshadowed, influence);
    if (LightCount > 7) accumulate(worldPosition, normal, jitter, LightPos7, LightColor7, LightRadius7, LightIntensity7, radiance, unshadowed, influence);

    influence = clamp(influence, 0.0, 1.0);
    // Nothing reaches this pixel: leave the vanilla frame byte-identical.
    if (influence <= 1.0E-3 && luminance(unshadowed) <= 1.0E-4) discard;

    vec3 scene = texture(SceneSampler, texCoord).rgb;
    float night = clamp(NightAmount, 0.0, 1.0);
    float ao = ambientOcclusion(texCoord, worldPosition, normal, viewDistance, jitter);

    // --- Multiplicative term: ambient * AO * shadow on the vanilla frame. ---
    // Inside a light's footprint the local exposure is owned by that light, so
    // the vanilla night ambient drops to AmbientFloor and blocked zones (energy
    // that was stopped by an occluder) sink further.  Outside the footprint the
    // frame is untouched, so a launch never dims the whole world at once.
    float blocked = max(0.0, luminance(unshadowed) - luminance(radiance));
    float exposure = 0.11 * max(SceneContribution, 0.0) * mix(1.0, NightBoost, 0.6);
    float shadowDarken = 1.0 - clamp(blocked * exposure * 0.9, 0.0, 0.85);
    float relight = AmbientFloor * ao * shadowDarken;
    vec3 base = scene * mix(1.0, relight, night * influence);

    // --- Diffuse term: albedo * irradiance. ---
    // The vanilla night frame is already darkened by the lightmap; undo part
    // of that to estimate the surface colour the light should reveal.
    vec3 albedo = clamp(scene * mix(1.4, 4.0, night), 0.0, 1.0);
    albedo = max(albedo, vec3(0.05));
    vec3 irradiance = radiance * exposure * mix(1.0, ao, 0.7);
    // Floors facing the sky catch a little extra bounce, like the lit road in the reference.
    irradiance *= 1.0 + max(0.0, normal.y) * 0.18;
    // Per-channel exponential exposure: the long orange tail stays orange, the
    // core saturates all channels to white-hot.
    vec3 lit = albedo * (vec3(1.0) - exp(-irradiance * 1.6));
    // Screen-style combine: additive at night, cannot clip a bright day frame.
    vec3 color = base + lit * (vec3(1.0) - base);

    // --- Bloom spill: over-exposed energy washes out surface detail near the core. ---
    vec3 over = max(vec3(0.0), irradiance - vec3(1.25));
    color += (vec3(1.0) - exp(-over * 0.35)) * 0.55 * BloomStrength;
    // Faint cool sky fill so fully shadowed zones are dark, not a black hole.
    color += vec3(0.010, 0.012, 0.020) * AmbientStrength * night * influence * ao;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
