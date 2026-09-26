#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;
out float vertexDistance;

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    vertexColor = Color;
    texCoord = UV0;
    // Vertices are camera-relative, so this is the distance to the eye.
    vertexDistance = length(viewPosition.xyz);
}
