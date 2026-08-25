<!-- pam:product-page:start -->
<div align="center">

# PAM Native GPU

**Own the pixels when ordinary components are not enough.**

Host native GPU surfaces and compiled shaders while command submission stays outside the PHP event loop.

[![Latest version](https://img.shields.io/packagist/v/pushinbr/pam-native-gpu?style=flat-square&label=stable)](https://packagist.org/packages/pushinbr/pam-native-gpu)
[![CI](https://img.shields.io/github/actions/workflow/status/push-in/pam-native-gpu/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/push-in/pam-native-gpu/actions)
![PHP](https://img.shields.io/badge/PHP-8.5-777BB4?style=flat-square&logo=php&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-15%2B-000000?style=flat-square&logo=apple&logoColor=white)

**[Documentation](https://push-in.github.io/pam-docs/native/overview/) · [Quick start](#quick-start) · [What you can build](#what-you-can-build) · [PAM ecosystem](https://push-in.github.io/pam-docs/ecosystem/) · [Issues](https://github.com/push-in/pam-native-gpu/issues)**

</div>

---

## Why PAM Native GPU

Host native GPU surfaces and compiled shaders while command submission stays outside the PHP event loop. The public API is strictly typed for PHP 8.5; expensive or frame-sensitive work stays in Rust or the platform SDK instead of crossing the application boundary every frame.

| | |
| --- | --- |
| **Best for** | A focused capability you can add to any PAM Native application |
| **Native path** | OpenGL ES · Metal |
| **Application model** | Composer package + generated native integration |
| **Design rule** | Independent module; no feed, vertical, or application template bundled |

## What you can build

- Realtime visual effects and shader-driven UI
- Scientific and high-volume data visualization
- Custom renderers and GPU-accelerated creative tools

## Quick start

Already have a PAM Native project? Add only this capability:

```bash
pam composer require pushinbr/pam-native-gpu
pam doctor --fix
```

New to PAM? Follow the **[five-minute PAM Native setup](https://push-in.github.io/pam-docs/native/overview/)** once, then return here. Your application stays a normal Composer project with a committed lockfile.
<!-- pam:product-page:end -->

## See it in action

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

The render path is allocation-stable after a program revision: Android parses
uniform JSON and resolves every OpenGL location during configuration, while iOS
sorts/ packs uniforms once and reuses one Metal command queue. Neither platform
parses JSON, sorts maps, looks up shader locations, creates a command queue, or
re-enters PHP inside `draw`. Shader/program resources are released after both
successful and failed compilation, and diagnostic payloads are bounded.

This package depends only on PAM Native's public module and binary-wire
capabilities. PAM Native UI, 3D, media, sync, and backend packages remain fully
optional and independent.

- [PAM introduction](https://push-in.github.io/pam-docs/introduction/)
- [PAM Native overview](https://push-in.github.io/pam-docs/native/overview/)
- [Report an issue](https://github.com/push-in/pam-native-gpu/issues)
