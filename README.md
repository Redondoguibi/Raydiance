# Raydiance 1.5.2

1.5.2 adds an optional **Direct settings** button on the Integration page, using the installed mod's NeoForge configuration factory. Direct saves independently; returning preserves the pending Raydiance preview. F8 now opens the menu only from gameplay, avoiding nested menus while editing. These changes were compiled and packaged without running tests or launching the game.

Independent visual mod for Minecraft Java 1.21.1 / NeoForge 21.1.265, Java 21.
OpenGL effects, dynamic profiles and the settings interface work without Direct.

Direct 1.5.0 / API 1.4 optionally executes material highlights, rain wetness, emissive surfaces and water absorption. Controls live in F8 > Materiais DX12 and are available to pack presets and dynamic profiles. These four effects require the native backend; the manager remains Java-only and runs without Direct. Their defaults are zero.

## Interface

Open Mods > Raydiance > Config, press F8 inside a world, or use `/raydiance menu`.
Seven pages expose color grading, effects, environment, folder shaderpacks, optional DX12, Lighting and Materiais DX12.
Numeric sliders preview immediately. Save persists; Cancel/Escape restores the settings from screen entry.
Profile editing has its own Save/Cancel transaction. Advanced effects and automatic profiles are off by default.
Existing color/fog commands and Direct HLSL commands remain supported.

## Implemented effects

| Feature | Implementation | Scope |
| --- | --- | --- |
| Grading | Saturation, contrast, brightness, exposure, gamma, temperature, vignette, strength | LDR world color; hand/GUI unaffected |
| Bloom | Threshold extraction + separable half-resolution blur | Two extra half-resolution draws |
| Sky / atmosphere | Directional sun glow, night tint, depth haze | Vanilla sky; no volumetric clouds |
| Water | Water atlas mask, vertex waves, screen refraction | Vanilla translucent terrain |
| Vegetation | Vertex wind for selected vanilla grass, fern, leaves, vine and wheat sprites | Not arbitrary modded geometry |
| Dynamic shadows | Near-camera sun depth map, PCF filtering; screen-space fallback | 48-block light frustum; camera-visible compiled terrain casters |
| Anti-aliasing | FXAA fused with grading | Spatial, not temporal AA |
| Reflections | Water-only screen-space depth ray march and Fresnel | Visible opaque scene; approximate sky fallback |
| SSAO | Bounded view-space depth neighborhood occlusion | Screen-space; 6/12 samples depending on quality |
| Sun rays | Depth-occluded radial screen sampling toward the sun | No off-screen light sources or volumetric cloud geometry |
| Sharpen | Restrained local unsharp filter fused with grading | Spatial filter; use low strength with FXAA |
| Tone mapping | White-normalized Reinhard-style shoulder | Works on post-process color; vanilla scene input remains LDR |

Shadows/water/reflections are experimental. Terrain shadow maps use 512, 1024 or 2048 resolution (quality 0/1/2),
up to 768 terrain draws and an 80-block caster radius. Over-budget maps are discarded in favor of screen-space
contact shadows. There are no cascades or off-screen shadow casters. Shadows use static geometry, so waving
foliage can have a slight silhouette mismatch. Reflections cannot recover off-screen/hidden/translucent objects.

## Dynamic profiles

Enable Dynamic profiles and use Edit profiles, or edit `config/raydiance/profiles.json`:

```json
{"profiles":[{"name":"Forest night rain","dimension":"minecraft:overworld","biome":"minecraft:forest",
"weather":"rain","start":13000,"end":23000,"priority":20,
"values":{"saturation":0.9,"fogDistance":0.8,"bloom":0.2}}]}
```

Dimension/biome selectors are exact resource IDs; `*` matches any. Weather is `*`, `clear`, `rain`, `thunder`.
All selectors must match. Highest priority wins, with name ordering as the deterministic tie-breaker.
Start is inclusive, end exclusive. Time intervals can cross midnight; equal start/end means all day.
Missing values inherit base/pack values. Transitions use elapsed-time exponential smoothing, reset on world changes.
Invalid reloads retain the last valid rules. The GUI validates values and saves the entire profile file atomically.

Available numeric keys: saturation, contrast, brightness, exposure, gamma, temperature, vignette, strength,
fogDistance, bloom, bloomThreshold, atmosphere, contactShadows, water, reflections, wind, ssao, godRays, sharpen, toneMapping, materialSpecular, materialWetness, materialEmissive, waterAbsorption.
The `contactShadows` setting controls projected shadows and their fallback. Underwater/lava fog is preserved.

## Folder shaderpacks

```text
config/raydiance/shaderpacks/
  cinematic/
    raydiance.json
    composite.fsh       (optional GLSL 150)
  softwarm.hlsl         (legacy optional Direct HLSL)
```

Install example folder creates a cinematic pack with an editable full composite source; an existing folder is not overwritten.
Select packs and reload in the Shaderpacks page. Candidate shaders compile before replacing the working program.
Invalid packs retain the previous valid program/settings and display an error, without retrying every frame.

Manifest example:

```json
{"format":1,"name":"Cinematic Light","options":{"bloom":0.25,"atmosphere":0.2,"saturation":1.08}}
```

Pack defaults override base numeric settings. Moving a slider copies those defaults into editable settings and turns
off that override, retaining the selected GLSL. Use pack defaults restores it. FXAA/quality remain client controls.
Folder names use 1-48 lowercase letters/digits/underscores/hyphens. Manifests are bounded to 64 KiB, GLSL to 256 KiB.
Local quoted includes are supported with cycle/depth/file/output budgets; path traversal and symlink paths are rejected.
Packs do not load native code or access the network.

In format 1, custom `composite.fsh` replaces the built-in final composite. It receives `in vec2 texCoord` and writes
`out vec4 fragColor`. The example provides the full source. Available uniforms:

- Samplers `Scene`, `Depth`, `BloomTex`, `ShadowTex`; vec2 `Texel`.
- Float `Saturation`, `Contrast`, `Brightness`, `Exposure`, `Gamma`, `Temperature`, `Vignette`, `Strength`.
- Float `Bloom`, `Atmosphere`, `Shadows`, `Daylight`, `Rain`, `Time`, `ShadowReady`; int `Fxaa`, `Steps`.
- Matrices `Projection`, `InvProjection`, `ShadowTransform`; vec3 `Sun` in view space.

Depth uses OpenGL [0,1] depth with sky at 1. ShadowTransform maps view-space positions to light clip space.
Use ShadowTex only when ShadowReady > 0 and BloomTex only when Bloom > 0. Custom GLSL decides which controls to honor.
Format 1 supports one custom composite. Format 2 adds the bounded ordered post chain described below.
Gbuffers, compute shaders, arbitrary target graphs and Iris shader translation are not available.
It is not compatible with Iris/OptiFine/Complementary/BSL shaderpacks.

## Performance and compatibility

Neutral settings skip post draws and free targets. The master switch disables all OpenGL effects and fog overrides.
Grading, FXAA, SSAO, sun rays, sharpen, tone mapping, atmosphere and shadow composition share one fullscreen draw
and one framebuffer copy. Disabled effects skip their sampling loops.
Bloom adds two half-resolution draws only when enabled. Water/reflections request one color/depth snapshot;
wind alone adds no fullscreen work. Shadow geometry is a separate opt-in depth pass with a bounded draw budget.
Targets resize with the window and release on world exit. The snapshot preserves the actual source depth/stencil format.

Known alternate terrain renderers (Iris, Oculus, Sodium, Embeddium, OptiFine) disable vanilla geometry overrides and
projected shadow maps. Unknown terrain shader programs are retained. These guards are not proof of compatibility
with every mod, GPU, resource pack or Fabulous rendering. Resource reloads recreate core shaders.

## Optional DX12

The Integration page controls optional Direct pass registration and single-file HLSL selection. Renderer capability discovery remains active when Direct is present. Direct is accessed only via
reflection, with no hard dependency. OpenGL post effects can process its imported world color/depth.
With Direct 1.4.0 / API 1.3, the general shadows/water/reflections/wind controls request native geometry effects every tick. Direct executes their C++/HLSL implementation; the GL equivalents do not duplicate it. Disabling Effects or optional integration releases both the request and any HLSL pass. Without Direct, the original GL path remains independent. Older Direct versions retain their feature restrictions. Fullscreen HLSL sees original opaque/cutout terrain depth and its color, before entities/transparency.

## Build and tests

```powershell
.\gradlew.bat build
.\gradlew.bat graphicsTest
.\gradlew.bat runClient
```

Build runs JUnit checks for selector combinations, priority, midnight boundaries, transitions, invalid profiles,
pack formats/path/bounds/errors, preservation of valid packs and disabled/neutral pass planning.
Graphics tests require a driver/display. They compile actual composite, bloom, terrain and shadow GLSL,
check identity pixels and finite effect output, restore GL state, reject malformed GLSL, and copy normalized,
floating-point and combined depth/stencil formats.

A development client without Direct was launched, configuration/editor screens inspected, saved settings restored
across launches, and a local test world rendered with advanced effects enabled. This smoke test does not replace
long-session testing, GPU benchmarks or broad mod compatibility testing. Full off-screen/cascaded shadows,
arbitrary shaderpack pass graphs and Iris compatibility remain unimplemented.

Output: `build/libs/raydiance-1.5.2.jar`.

## Format 2: ordered post passes

The Lighting page installs a `multipass_demo` folder. It contains a floating-point intermediate and a local include.
A format-2 pack first runs the current built-in world effects, then the listed custom post passes, in order:

```json
{
  "format": 2,
  "name": "Multi-pass light",
  "options": {"ssao": 0.2, "godRays": 0.15},
  "passes": [
    {"file": "composite.fsh", "scale": 0.5, "target": "rgba16f"},
    {"file": "final.fsh", "scale": 1.0, "target": "rgba8"}
  ]
}
```

Pass filenames are relative to the pack root and end in `.fsh`. Each pass reads the previous enabled pass through `Scene`
and writes a distinct target, preventing read/write feedback. `OriginalScene` always refers to the unprocessed world color.
`Depth`, `BloomTex` and `ShadowTex` retain world-frame meaning at every pass. UVs remain normalized across resolutions.
`Texel` and `InputSize` describe the actual input resolution, while `OutputSize` describes the current target.
Final output is upscaled when needed and copied back once. Alpha should be preserved by pack shaders.

Limits: 1-8 passes; scale 0.25-1.0; target `rgba8` or `rgba16f`; at most 128 MiB for declared custom targets at the current
resolution, excluding built-in targets. Packs exceeding that budget at load time are rejected; if resize exceeds it,
post processing is disabled until reload. HDR applies to intermediates, not vanilla lighting or the final display buffer.
An optional `"when":"ssao"` (or any supported numeric option name) skips a pass when that effective option is <= 0,
releasing its target. Entirely inactive chains do not force otherwise neutral post processing.
All programs and reserved uniform types are validated before replacing the working chain.

Format-1 shader sources retain ownership of their effects; old custom composites must add the new effect code themselves,
or migrate to format 2 to get the updated built-in effects before their custom passes.

### Local includes

```glsl
#version 150
#include "lib/finish.glsl"
uniform sampler2D Scene;
in vec2 texCoord;
out vec4 fragColor;
void main() { vec4 c = texture(Scene, texCoord); fragColor = vec4(finishLight(c.rgb), c.a); }
```

Includes resolve relative to the containing file. Use a standalone `#include "path"` line, optionally followed by `//` comments.
Libraries omit `#version`; only the entry shader declares it. Includes expand before GLSL conditionals, so include guards do
not make cycles valid. Limits are 16 nested levels, 64 distinct files per pack loader, 256 KiB per source and 1 MiB expanded
text per pass. Absolute paths, `..`, backslashes, external symlinks and cycles are rejected. `#line` directives preserve
library source numbers/line locations for compiler errors.

### Additional frame uniforms

| Uniform | GLSL type | Meaning |
| --- | --- | --- |
| OriginalScene | sampler2D | World color before built-in/custom post processing |
| InputSize / OutputSize / ViewSize | vec2 | Input, current output and world target dimensions |
| PassIndex | int | -1 for built-in/legacy composite, otherwise zero-based manifest index |
| Ssao / GodRays / Sharpen / ToneMapping | float | Effective individual control strengths |
| FrameCounter | int | Per-world frame counter |
| FrameTime / FrameTimeCounter | float | Clamped elapsed seconds / counter wrapping after one hour |
| WorldTime / WorldDay | int | Day tick modulo 24000 / day count |
| Wetness / Thunder | float | Smoothed rain wetness / thunder strength |
| EyeInWater | int | 0 air, 1 water, 2 lava, 3 powder snow |
| SkyColor / EyeBrightness | vec3 / vec2 | Vanilla sky RGB / block and sky light normalized to [0,1] |
| CameraPosition | vec3 | Float camera position; use split fields for distant-world precision |
| CameraBlock / CameraFraction | ivec3 / vec3 | Floor of world camera coordinates and positive fractional remainder |
| CameraDelta | vec3 | Current minus previous camera position in blocks |
| View / InvView | mat4 | Current camera-relative view transform / inverse |
| PreviousView / PreviousProjection | mat4 | Previous frame transforms |
| HistoryValid | int | 0 on reload, world switch or teleport >= 1024 blocks; otherwise 1 |

These are Raydiance contracts, not Iris aliases. Historical metadata alone does not implement TAA or motion blur;
no previous-color buffer is exposed. Unused uniforms/textures are skipped. Custom shaders may add their own constants,
but reserved names must use the listed types and may not be arrays.

See [Iris reference study](docs/iris-reference.md) for the inspected source revision and implementation decisions.

## Shaderpack controls and current integration

F8 > Shaderpacks > Configurar shaderpack exposes declarative pack controls, live preview, presets and reset. Finish with Concluir then Save; Cancel rolls back previews. Pack-specific values persist outside pack folders in config/raydiance/pack-options. Lus supplies 16 controls and three presets.

Use Direct 1.4.0 / API 1.3.0 for native geometry requests. API 1.2+ supplies original terrain depth to the optional Lus HLSL detail pass; HLSL: none skips that extra draw while geometry effects remain available. Disabling integration does not disable renderer discovery or the Direct terrain backend. Native shadows are near sun maps (no cascades/entity shadows); water uses bounded screen-space reflections with an atmospheric fallback. Fabulous/custom targets retain vanilla translucency. See the main Direct project docs/native-shader-contract.md for evidence, resource costs and manual validation limits. Actual modpack FPS has not been validated for this release.
