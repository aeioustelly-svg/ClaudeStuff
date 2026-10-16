#version 150

// Colours a flat pixel-art cloud sprite with the current palette.
//
// Texture: alpha = 255 on the cloud and 0 elsewhere, R = tone band * 50 (1 deep shadow, 2 lavender, 3 light blue, 4 white).
// Vertex colour: r = haze towards the sky, a = fade (1 = solid).

uniform sampler2D Sampler0;
uniform vec3 Band1;
uniform vec3 Band2;
uniform vec3 Band3;
uniform vec3 Band4;
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
    if (d >= vertexColor.a) {
        discard;
    }

    float band = floor(tex.r * 255.0 / 50.0 + 0.5);
    vec3 col = band < 1.5 ? Band1 : (band < 2.5 ? Band2 : (band < 3.5 ? Band3 : Band4));
    col = mix(col, HazeColor, vertexColor.r);
    fragColor = vec4(col, 1.0);
}
