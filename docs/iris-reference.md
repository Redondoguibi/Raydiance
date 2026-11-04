# Iris 1.21.1 reference study

Source snapshot: IrisShaders/Iris branch `1.21.1`, commit `12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3`.
The study focused on shaderpack loading, post-processing targets, include resolution and frame metadata.
Raydiance uses its own implementation and format; Iris is not a runtime dependency.

## Source-to-feature mapping

- [ProgramSet](https://github.com/IrisShaders/Iris/blob/12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3/common/src/main/java/net/irisshaders/iris/shaderpack/programs/ProgramSet.java)
  loads distinct geometry/composite program families and numbered stages. Raydiance now has a bounded explicit ordered
  post-pass list, retaining the legacy single-composite format.
- [CompositeRenderer](https://github.com/IrisShaders/Iris/blob/12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3/common/src/main/java/net/irisshaders/iris/pipeline/CompositeRenderer.java)
  manages per-pass targets, buffer flipping, viewport dimensions and rendering state. Raydiance now gives each enabled
  post pass a separate target with a declared scale/format and a total memory budget. It restores the host GL state.
- [IncludeGraph](https://github.com/IrisShaders/Iris/blob/12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3/common/src/main/java/net/irisshaders/iris/shaderpack/include/IncludeGraph.java)
  resolves the source graph and detects cycles. Raydiance's local preprocessor adds bounded expansion, path containment,
  source-number line directives and explicit diagnostics. Its path rules and limits intentionally differ from Iris.
- [CommonUniforms](https://github.com/IrisShaders/Iris/blob/12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3/common/src/main/java/net/irisshaders/iris/uniforms/CommonUniforms.java)
  exposes environment/player metadata including rain, wetness, sky color, eye light and submersion.
  Raydiance adds its own typed environment uniforms and smoothing.
- [CameraUniforms](https://github.com/IrisShaders/Iris/blob/12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3/common/src/main/java/net/irisshaders/iris/uniforms/CameraUniforms.java)
  tracks camera positions and precision-aware representations. Raydiance exposes block/fraction splits, camera delta,
  previous transforms and a history-valid flag reset after world changes/teleports.
- [SystemTimeUniforms](https://github.com/IrisShaders/Iris/blob/12ea3bf16b39c0a70ec31dc9de1483c77bd3ccc3/common/src/main/java/net/irisshaders/iris/uniforms/SystemTimeUniforms.java)
  supplies per-frame timing and counters. Raydiance adds frame timing with its own reset/clamp/wrap rules.

Iris is chiefly shaderpack infrastructure; a shaderpack supplies artistic algorithms and settings.
The new built-in SSAO, sun-ray, sharpening and tone-mapping effects are Raydiance effects, implemented against its
existing depth/color inputs. They share the built-in composite and skip their optional sampling loops when disabled.

## Compatibility boundary

This implementation does not import Iris code or shaders, link Iris libraries, translate Iris/OptiFine sources,
provide full gbuffer/material IDs, implement compute/tessellation stages, colored shadow passes, cascaded/off-screen
casters, temporal AA, or expose arbitrary target graphs. Such features require changes to terrain/entity capture,
resource contracts and synchronization beyond the existing post-processing boundary.
Direct remains optional and is accessed only through the existing public-API adapter. New post passes are OpenGL;
there is no claim that an arbitrary GLSL pack can run through the optional Direct HLSL adapter.

## Verification

Unit tests cover bounded include expansion, comments, cycles, traversal/missing files, malformed versions/options,
pass ordering/scales/conditions, memory accounting, frame-history invalidation and wetness smoothing.
Driver tests cover actual GLSL compilation, reserved-uniform type rejection, HDR intermediates, scaled targets,
resize, disabled passes, rollback after a bad replacement, and finite output with the new built-in effects enabled.
Existing compositor, terrain, shadow and depth/stencil regression tests remain in the suite.
