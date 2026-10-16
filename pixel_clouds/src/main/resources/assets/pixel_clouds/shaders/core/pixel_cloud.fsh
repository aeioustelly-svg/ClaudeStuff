#version 150

// Lights a baked cloud sprite. Keep in step with Preview.drawSprite in src/preview.
//
// Texture: R,G = sprite-space normal x,y, B = crease occlusion, A = coverage (0 = empty).
// Vertex colour: r = haze towards the sky, g = per-cloud brightness bias (0.5 neutral),
// b = 1 for the complementary dither pattern (used by the second sprite of a cross-dissolve), a = fade (1 = solid).

uniform sampler2D Sampler0;
uniform vec3 Shade0;
uniform vec3 Shade1;
uniform vec3 Shade2;
uniform vec3 Shade3;
uniform vec3 RimColor;
uniform vec3 HazeColor;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 lightDir;

out vec4 fragColor;

const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.5) {
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

    float lit = clamp((dot(nxy, L.xy) + nz * 0.5 * L.z) * 0.5 + 0.5, 0.0, 1.0);
    lit *= 0.62 + 0.38 * tex.b;
    lit += (vertexColor.g - 0.5) * 0.7;

    // Silver lining when the sun is behind the cloud, strongest on the sun-facing edge.
    float back = clamp(-L.z, 0.0, 1.0);
    float towards = clamp(dot(nxy, L.xy) / ((length(nxy) + 1e-5) * (length(L.xy) + 1e-5)), 0.0, 1.0);
    float rim = back * pow(1.0 - nz, 1.5) * 1.6 * (0.35 + 0.65 * towards);
    lit += rim * 0.4;

    vec3 col = lit < 0.28 ? Shade0 : (lit < 0.50 ? Shade1 : (lit < 0.72 ? Shade2 : Shade3));
    if (rim > 0.40) {
        col = RimColor;
    }
    col = mix(col, HazeColor, vertexColor.r);
    fragColor = vec4(col, 1.0);
}
