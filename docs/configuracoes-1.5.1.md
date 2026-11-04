# Configurações e ajuda — Raydiance 1.5.1 / Lus

As páginas principais têm nomes em português, níveis de qualidade legíveis e dicas para todos os controles gerais, ações e integrações. O botão `?` abre um guia da página com explicações completas; a roda, setas, Page Up/Down e Home/End permitem percorrê-lo sem depender do tempo de exibição de uma dica. Esc ou Voltar retorna à edição.

O guia informa o resultado visual, valores neutros, condições de funcionamento, limitações e custos relevantes. Não promete FPS. Explica a independência do interruptor Terrain DX12 do Direct e a diferença entre passes HLSL opcionais e solicitações de geometria. Os textos gerais possuem traduções em português e inglês.

No editor de shaderpacks, cada dica inclui descrição, padrão e intervalo do controle. O guia da categoria apresenta as opções em texto corrido. Os avisos explicam a prévia, presets, restauração de todas as categorias e a necessidade de salvar na tela principal. Cancelar preserva o comportamento anterior de desfazer a edição.

As 24 descrições do Lus foram revisadas em português sem alterar identificadores, uniforms, limites, padrões, presets ou shaders. Entre as correções de nomenclatura, a recuperação de detalhe em tons escuros deixou de ser chamada de suavidade das sombras. As descrições distinguem o SSAO e as sombras de contato em tela das sombras de geometria DX12, e a tonalidade subaquática da absorção nativa da água. Opções de nuvens indicam quando não têm efeito e quais controles não eliminam o cálculo.

O editor de perfis recebeu rótulos curtos e ajuda em cada campo, incluindo IDs, curingas, intervalos que atravessam a meia-noite, fim exclusivo e desempate por nome. A descrição segue o comportamento real: uma regra é selecionada, sem somar todas as regras correspondentes.

## Verificação e instalação

Compilação, validação dos assets, 37 testes unitários, 20 testes gráficos e 10 testes do Lus passaram sem falhas ou testes ignorados. Os testes gráficos validam os shaders; não constituem inspeção visual das telas dentro do Minecraft. A disposição real, navegação com controle e leitura nas diferentes escalas de GUI ainda precisam dessa inspeção.

Raydiance 1.5.1 e o manifesto atualizado do Lus foram instalados no modpack Direct com backup. As configurações pessoais foram comparadas com o backup e preservadas. O Direct não precisou ser alterado. O arquivo `Lus.zip` também foi atualizado. Detalhes da instalação: `archive/settings-help-installation-1.5.1.json`.

Para acessar: F8 → escolha a página → `?`. Para as opções do Lus: F8 → Shaderpacks → Configurar shaderpack → escolha a categoria → `?`. Concluir retorna ao gerenciador; Salvar alterações confirma a edição.
