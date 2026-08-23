# PAM Native GPU

## Start here

```bash
curl --proto '=https' --proto-redir '=https' --tlsv1.2 \
    --connect-timeout 15 --max-time 60 --max-filesize 1048576 -fsSL \
    https://github.com/push-in/pam/releases/latest/download/install.sh | sh
pam init my-app --template native
cd my-app
pam composer require pushinbr/pam-native-gpu
pam doctor --fix
```

This package provides a horizontal GPU surface, not a canvas, 3D engine, game framework, or app
template. OpenGL ES 3 executes on Android and Metal executes on iOS. PHP supplies immutable shader
programs and scalar uniforms; the native render loop never crosses the PHP boundary per frame.

```php
use Pam\Native\Gpu\GpuProgram;
use Pam\Native\Gpu\GpuView;

$program = new GpuProgram(
    glsl: <<<'GLSL'
#version 300 es
precision highp float;
in vec2 v_uv;
uniform vec2 u_resolution;
uniform float u_time;
uniform float intensity;
out vec4 outColor;
void main() { outColor = vec4(v_uv, 0.5 + sin(u_time) * intensity * 0.5, 1.0); }
GLSL,
    metal: <<<'METAL'
fragment float4 pam_fragment(PamVertexOut in [[stage_in]], constant PamBuiltins& builtins [[buffer(0)]], constant float* values [[buffer(1)]]) {
    return float4(in.uv, 0.5 + sin(builtins.time) * values[0] * 0.5, 1.0);
}
METAL,
    uniforms: ['intensity' => 0.8],
);

return GpuView::make($program);
```

Shader sources are capped at 64 KiB and uniforms at 64 finite scalars. Uniforms on Metal are packed
in lexicographically sorted name order. Platform support: Android API 26+, iOS 15+, PHP 8.5+, and
PAM Native 0.8.x.

- [PAM introduction](https://push-in.github.io/pam-docs/introduction/)
- [PAM Native overview](https://push-in.github.io/pam-docs/native/overview/)
- [Report an issue](https://github.com/push-in/pam-native-gpu/issues)
