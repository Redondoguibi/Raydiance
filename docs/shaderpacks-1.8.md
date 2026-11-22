# Shaderpacks Raydiance 1.8

Use Minecraft Java 1.21.1 com NeoForge e Direct 1.8 ou posterior. Ative Direct DX12 com substituição de terreno e selecione gráficos Rápidos ou Detalhados. Em **F8 > Shaderpacks > Biblioteca**, instale o exemplo e selecione **direct-example**.

## Estrutura do pack

O Raydiance lê pastas e arquivos ZIP em `config/raydiance/shaderpacks`. Cada pack tem um `raydiance.json` com `format: 4`. Declare pelo menos um destes estágios.

| Campo | Estágio |
| --- | --- |
| `geometry` | Programas de vértice e pixel do terreno Direct. Use `file` para o HLSL. Os campos booleanos opcionais `translucent`, `sceneInputs` e `shadows` habilitam a etapa translúcida, a captura de cor e profundidade antes dela e um mapa de sombras de 1024², respectivamente. `sceneInputs` exige `translucent`. |
| `terrainPasses` | Lista de até oito arquivos HLSL em ordem de execução, sobre a cor e profundidade do terreno opaco, antes da composição com o restante do mundo. A lista compartilha os slots do Direct com passes registrados por outros mods. |
| `worldPasses` | Lista de até oito arquivos HLSL (dezesseis com o Raydiance e o Direct 1.9) sobre o mundo completo antes da mão e da interface. |

Um passe intermediário aceita o objeto `{"file":"bloom.hlsl","target":"rgba16f"}` para preservar luz acima de 1. Uma entrada que declara só o nome do arquivo usa RGBA8. O último passe de cada estágio escreve em RGBA8 para compor com o jogo.

A partir do Raydiance 1.8.1, passes intermediários de mundo aceitam `"whenAny":["bloom","godRays"]`. O passe roda quando pelo menos uma dessas opções tem valor diferente de zero. O passe seguinte recebe em `t0` a saída do último passe executado, ou a cor original se for o primeiro. O último passe é obrigatório. Em `geometry`, use `"translucent":true,"translucentWhenAny":["define:WATER_GLINT"]` para pedir a etapa translúcida apenas enquanto o efeito estiver ativo. Controles de compilação usam o prefixo `define:` e escolhas numéricas usam o valor escrito no HLSL. Opções próprias do Direct ainda podem solicitar a etapa translúcida.

O carregador expande includes locais. Pastas `world0`, `world-1` e `world1` podem fornecer arquivos por dimensão usando os mesmos caminhos relativos do pack. O manifesto também aceita o mapa `dimensions` para pastas personalizadas.

## Opções

Declare controles em `shaderOptions`. Use `category` como caminho, por exemplo `Terrain / Water`. Os 32 primeiros valores declarados ficam disponíveis em todos os estágios por seus nomes `Pack...` do campo `uniform`, em ordem de manifesto. Declare valores de predefinições em `presets[].shaderValues`. `#define` com lista de valores ou toggle referenciado pode expor opções de compilação, e `defineCategories` permite colocá-las nas mesmas pastas.

O formato 4 rejeita os campos antigos `options`, `builtinEffects` e os controles embutidos em predefinições. Os packs em formatos 1–3 precisam de migração para rodar em 1.8. Ajustes Standalone ficam no Raydiance e as opções declaradas ficam no armazenamento de cada pack.

## Contrato HLSL

Os passes de tela usam `float4 main(float4 position : SV_Position) : SV_Target0`. Declare as entradas que o programa usar.

O Direct 1.8.2 aceita um programa de vértices opcional em passes de tela. Inclua `// direct:fullscreen-vertex` em uma linha própria e implemente `passVertex(uint id : SV_VertexID)` para os três vértices do triângulo de tela cheia. Os dois programas recebem `DIRECT_FULLSCREEN_VERTEX` e podem ler o mesmo `b0`. Texturas e sampler ficam disponíveis no pixel shader. Use `nointerpolation` nos valores constantes do quadro. Para manter compatibilidade com o Direct 1.8.0 e 1.8.1, forneça também uma assinatura de `main` que receba só `SV_Position` quando a macro estiver ausente.

| Binding | Dado |
| --- | --- |
| `t0` | Cor produzida pelo passe anterior. O primeiro recebe a cor original do estágio. |
| `t1` | Profundidade original, com valor 1 nas áreas sem geometria. |
| `t2` | Cor original do estágio, disponível em todos os passes. |
| `s0` | Amostragem linear com clamp. |
| `b0` | Declare `float4 DirectFrame; float4 RaydianceData[63];`. O primeiro vetor traz tempo do Direct, largura, altura e índice de quadro. No restante, floats 32–63 guardam opções, 64–127 guardam projeção, inversa da projeção, inversa da vista e vista, nessa ordem. Os floats 128–147 trazem direção e intensidade solar, chuva, trovão, relógios, posição da câmera, iluminação local, fluido e cor do céu. |

Para geometria, o arquivo entra após a declaração das estruturas e bindings do Direct. Implemente `VertexOutput packVertex(VertexInput input, VertexOutput vanilla)`, `float4 packOpaque(VertexOutput input, float4 vanilla)`, `float4 packCutout(VertexOutput input, float4 vanilla)`, `float4 packTranslucent(VertexOutput input, float4 vanilla)` e `void packShadow(VertexOutput input, float alpha)`. `packCutout`, `packTranslucent` e `packShadow` podem fazer seu próprio descarte com `clip`. Para recorte vanilla, use `clip(vanilla.a - alphaCutoff)` e retorne alfa 1 no pixel aceito. Preserve alfa 1 em superfícies opacas.

Em geometria, o Direct oferece atlas, lightmap, posição relativa à câmera, UV, cor, normal, material e matrizes. `SceneColor` e `SceneDepth` são entradas do pixel shader translúcido com `sceneInputs` ativo. `ShadowMap` e `shadowVP` ficam disponíveis para amostragem no pixel shader quando `geometryParams.x` indica um mapa válido. O exemplo mostra como combinar esses dados com opções do pack.

A superfície programável cobre terreno Direct e os dois estágios de tela. O Raydiance 1.9 com o Direct 1.9 acrescenta buffers nomeados com múltiplos alvos, compute, histórico temporal e substituição do céu e das nuvens: veja [shaderpacks-1.9.md](shaderpacks-1.9.md).
