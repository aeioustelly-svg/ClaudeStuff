#version 150

// Lights a baked cloud sprite. Keep in step with ShadeCpu in src/preview.
//
// Texture: R,G = sprite-space normal x,y. B = openness (low in crevices). A = 0 for empty, 40 for a soft
// fringe pixel, otherwise 96 + 8 * hue + jitter (hue and jitter 0..7).
// Vertex colour: r = haze towards the sky, g = per-cloud brightness bias (0.5 neutral), b = 1 for the
// complementary dither pattern (second sprite of a cross-dissolve), a = fade (1 = solid).

uniform sampler2D Sampler0;
uniform vec3 Shade0;
uniform vec3 Shade1;
uniform vec3 Shade2;
uniform vec3 Shade3;
uniform vec3 Shade4;
uniform vec3 RimColor;
uniform vec3 Violet;
uniform vec3 HazeColor;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 lightDir;

out vec4 fragColor;

const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    float code = floor(tex.a * 255.0 + 0.5);
    if (code < 1.0) {
        discard;
    }

    // Screen-space ordered dither for fading clouds in and out.
    ivec2 fc = ivec2(gl_FragCoord.xy) & 3;
    float d = (BAYER[fc.y * 4 + fc.x] + 0.5) / 16.0;
    if (vertexColor.b > 0.5) {
        d = 1.0 - d;
    }
    if (d >= vertexColor.a) {
        discard;
    }

    vec2 nxy = tex.rg * 2.0 - 1.0;
    float nz = sqrt(max(0.0, 1.0 - dot(nxy, nxy)));
    vec3 L = normalize(lightDir);

    bool isFringe = code < 70.0;
    float v = max(code - 96.0, 0.0);
    float hueCell = floor(v / 8.0);
    float hue = hueCell / 7.0;
    float jit = (v - hueCell * 8.0) / 7.0;

    float lit = clamp((dot(nxy, L.xy) + nz * 0.3 * L.z) * 0.5 + 0.5, 0.0, 1.0);
    lit *= 0.55 + 0.45 * tex.b;
    lit += (jit - 0.5) * 0.12 + (vertexColor.g - 0.5) * 0.7;

    // Silver lining when the sun is behind the cloud, strongest on the sun-facing edge.
    float back = clamp(-L.z, 0.0, 1.0);
    float towards = clamp(dot(nxy, L.xy) / ((length(nxy) + 1e-5) * (length(L.xy) + 1e-5)), 0.0, 1.0);
    float rim = back * pow(1.0 - nz, 1.5) * 1.6 * (0.35 + 0.65 * towards);
    lit += rim * 0.4;

    vec3 col;
    if (isFringe) {
        // Soft halo: a lighter shade pulled towards the sky.
        col = mix(lit > 0.6 ? Shade3 : Shade2, HazeColor, 0.55);
    } else {
        col = lit < 0.26 ? Shade0 : (lit < 0.44 ? Shade1 : (lit < 0.62 ? Shade2 : (lit < 0.80 ? Shade3 : Shade4)));
        // Shadows drift towards violet in some parts of the cloud.
        float shadowWeight = lit < 0.44 ? 1.0 : (lit < 0.62 ? 0.6 : 0.0);
        col = mix(col, Violet, shadowWeight * hue * 0.55);
        if (rim > 0.40) {
            col = RimColor;
        }
    }
    col = mix(col, HazeColor, vertexColor.r);
    fragColor = vec4(col, 1.0);
}
