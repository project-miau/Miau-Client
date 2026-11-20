#version 120
uniform sampler2D image;
uniform vec2 stepSize;
uniform float radius;
varying vec2 uv;
void main() {
    if (radius <= 0.0) {
        gl_FragColor = texture2D(image, uv);
        return;
    }
    vec4 sum = vec4(0.0);
    float count = 0.0;
    for (float offset = -radius; offset <= radius; offset += 2.0) {
        sum += texture2D(image, uv + stepSize * (offset + 0.5));
        count += 1.0;
    }
    gl_FragColor = sum / count;
}
