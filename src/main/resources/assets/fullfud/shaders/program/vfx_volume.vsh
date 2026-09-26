#version 150

in vec4 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;
out vec3 viewPosition;

void main() {
    vec4 viewPosition4 = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition4;
    vertexColor = Color;
    texCoord = UV0;
    viewPosition = viewPosition4.xyz;
}
