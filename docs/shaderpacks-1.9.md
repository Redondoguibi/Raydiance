# Shaderpacks Raydiance 1.9 — buffers, saídas múltiplas, histórico e compute

O Raydiance 1.9 mantém o formato 4 do [1.8](shaderpacks-1.8.md) e acrescenta campos opcionais que exigem o Direct 1.9 (API 1.7). Um pack que não os usa funciona como antes, inclusive com o Direct 1.8. Um pack que os usa com um Direct mais antigo é recusado com uma mensagem clara.

## Manifesto

```json
{
  "format": 4,
  "name": "Exemplo",
  "buffers": {
    "bloom": {"format": "rgba16f", "scale": 0.5, "clear": true},
    "taa":   {"format": "rgba16f", "history": true},
    "lum":   {"format": "r32f", "scale": 0.25}
  },
  "terrainDepth": true,
  "replaceVanilla": ["sky", "clouds"],
  "worldPasses": [
    {"file": "bloom.hlsl", "outputs": ["bloom"]},
    {"file": "luminance.hlsl", "compute": true, "outputs": ["lum"]},
    {"file": "taa.hlsl", "outputs": ["color", "taa"]},
    "final.hlsl"
  ]
}
```

| Campo | Significado |
| --- | --- |
| `buffers` | Até oito buffers nomeados, na ordem declarada. `format`: `rgba8` (padrão), `rgba16f`, `rgba32f`, `r11g11b10f`, `rg16f`, `r16f`, `r32f`, `rgba16`, `rg8`, `r8`. `scale` de 0,125 a 1 da tela. `history` mantém o quadro anterior legível; `clear` zera o buffer a cada quadro (sem ele, o conteúdo persiste). |
| `worldPasses[].outputs` | Até quatro alvos. `color` é a cor da cadeia e vem primeiro. Sem o campo, o passe escreve só `color`. Todas as saídas de um passe têm o mesmo tamanho. |
| `worldPasses[].compute` | O passe é um compute shader (`main` com `[numthreads(x, y, 1)]`) e escreve apenas buffers. |
| `terrainDepth` | Expõe a profundidade só do terreno, antes de entidades, partículas e translucidez. |
| `replaceVanilla` | `sky` e/ou `clouds`: o Direct deixa de desenhar o céu (cúpula, sol, lua, estrelas) ou as nuvens vanilla enquanto os passes de mundo rodam. O pack desenha esses elementos onde a profundidade é 1. |

Agora são aceitos até 16 passes de mundo. `whenAny` continua valendo; o último passe precisa escrever `color` e não pode ser compute.

## Declarações geradas

O Raydiance escreve as diretivas do Direct e declara os buffers com o nome do manifesto, antes do código do pack:

| Nome no HLSL | Conteúdo |
| --- | --- |
| `bloom` | `Texture2D<float4>` com o estado atual (vazio no passe que o escreve) |
| `bloomHistory` | o quadro anterior com `history`; senão o conteúdo atual |
| `bloomSize` | largura, altura e recíprocos |
| `bloomOut` | `RWTexture2D<float4>`, só no passe compute que escreve o buffer |
| `DirectTarget` | tamanho da saída do passe atual, mesmo layout |
| `RaydianceTerrainDepth` | `Texture2D<float>` com `terrainDepth` |
| `RaydiancePointClamp` | sampler de ponto com clamp |

Os registros t0–t2, o sampler linear `s0` e o `b0` continuam declarados pelo pack, como no 1.8. Não declare manualmente t3–t19, u0–u7, `s1` ou `b1` num pack que usa esses campos: os nomes acima já ocupam esses registros.

Um passe gráfico com várias saídas devolve uma estrutura:

```hlsl
struct TaaOutput { float4 color : SV_Target0; float4 history : SV_Target1; };
TaaOutput main(float4 position : SV_Position) {
    int3 pixel = int3(int2(position.xy), 0);
    float4 now = Previous.Load(pixel);
    TaaOutput o;
    o.history = lerp(taaHistory.Load(pixel), now, 0.1);
    o.color = o.history;
    return o;
}
```

Um passe compute escreve pelo `...Out`:

```hlsl
[numthreads(8, 8, 1)]
void main(uint3 id : SV_DispatchThreadID) {
    float2 uv = (id.xy + 0.5) * lumSize.zw;
    float luma = dot(Original.SampleLevel(LinearClamp, uv, 0).rgb, float3(0.2126, 0.7152, 0.0722));
    lumOut[id.xy] = float4(luma, 0, 0, 1);
}
```

## Entidades, partículas e céu

Entidades, partículas, mão e GUI continuam sendo desenhados pelo OpenGL do Minecraft; o Direct renderiza só o terreno em DX12, então não há programas próprios por entidade ou partícula. Com `terrainDepth`, os passes distinguem terreno, objetos na frente do terreno e céu. O céu e as nuvens podem ser desenhados inteiramente pelo pack com `replaceVanilla`.

## Validação

`WorldGraphManifestTest` lê um pack com todos os campos, confere as diretivas e declarações geradas, recusa nove manifestos inválidos e compila as fontes geradas com o `fxc` do Windows SDK nos perfis do Direct. O Direct executa o grafo em D3D12 real em `NativeWorldGraphInteropTest`. Nenhum pack com esses recursos foi aberto no jogo ainda.
