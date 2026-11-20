#version 120

uniform vec2 resolution;
uniform float time;
uniform vec3 primaryColor;
uniform vec3 secondaryColor;

/*
 * Rise 6.9.5 main-menu background shader.
 * Original source: https://github.com/NoHackClient/Rise-6.9.5/blob/main/src/main/resources/assets/minecraft/rise/shader/main_menu/background.frag
 * Original attribution: @author Hazsi (kinda)
 *
 * Rise's unused GL_OES_standard_derivatives directive is intentionally omitted:
 * this client targets desktop GLSL 1.20, where that GLES extension is unavailable.
 */
mat2 m(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

float map(vec3 p) {
    p.xz *= m(time * 0.4);
    p.xy *= m(time * 0.1);
    vec3 q = p * 2.0 + time;
    return length(p + vec3(sin(time * 0.7))) * log(length(p) + 1.0)
        + sin(q.x + sin(q.z + sin(q.y))) * 0.5 - 1.0;
}

void main() {
    vec2 a = gl_FragCoord.xy / resolution.y - vec2(0.9, 0.5);
    vec3 cl = vec3(0.0);
    float d = 2.5;

    for (int i = 0; i <= 5; i++) {
        vec3 p = vec3(0, 0, 4.0) + normalize(vec3(a, -1.0)) * d;
        float rz = map(p);
        float f = clamp((rz - map(p + 0.1)) * 0.5, -0.1, 1.0);
        float highlight = max(f, 0.0);
        float shadow = 1.0 - max(-f, 0.0) * 5.0;
        vec3 l = (primaryColor * 0.4 + secondaryColor * 5.0 * highlight) * shadow;
        cl = cl * l + smoothstep(2.5, 0.0, rz) * 0.6 * l;
        d += min(rz, 1.0);
    }

    gl_FragColor = vec4(cl, 1.0);
}
