#version 150

in vec4 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;
out vec3 viewPosition;
out vec3 viewNormal;

void main() {
    vec4 viewPosition4 = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition4;
    vertexColor = Color;
    texCoord = UV0;
    viewPosition = viewPosition4.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
}
