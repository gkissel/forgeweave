# Brief para o designer: tudo que o Forgeweave desenhou por conta própria

Esta é a lista de trabalho. Ela nomeia todo asset visual do Forgeweave cujos pixels **não** vieram da Tinkers' Construct, mais as coisas que existem no jogo sem nenhuma arte própria ainda. Se um asset está nesta lista, um designer é dono dele. Se o design de um asset já está pronto, ele não aparece aqui: este documento é só o trabalho pendente.

É um companheiro de [docs/texture-manifest.pt-BR.md](../texture-manifest.pt-BR.md), o guia de arte, que explica *como* desenhar para este mod: tamanhos de tela, a regra do tingimento em cinza, como uma ferramenta é montada em camadas, quais scripts compõem o quê, e a checklist de entrega. Este documento é o *quê* e *em que ordem*. Leia as regras abaixo e use o manifesto na hora de desenhar.

Auditado no commit [`ef1ca1af`](https://github.com/gkissel/forgeweave/tree/ef1ca1afc03b2ffae6a4937164112171e25039b7). Todo link de arquivo neste documento está fixado nesse commit, então ele abre o arquivo exatamente como foi auditado mesmo depois que a arte mudar.

Esta revisão substitui a auditoria de [`ba4c0b8d`](https://github.com/gkissel/forgeweave/tree/ba4c0b8d53f1cf5dfcd0fe503de287838a819223) (PR #1048, emendada pelo PR #1056): recontei tudo contra a árvore atual e incorporei o que as PRs #1026, #1052, #1053, #1056, #1060 e #1064 mudaram desde então. A maior mudança de conteúdo é que tudo cujo design já está pronto saiu da lista, não só os três exemplos que o mantenedor citou (katana, longsword e war mace): a regra vale para qualquer asset com arte Forged no caminho padrão, então a seção das armas próprias do Forgeweave e a seção das camadas de fundição inteiras saíram também. O que cada uma continha está resumido na seção 8, "O que já está coberto".

## As regras, em poucas palavras

Os sprites são 16x16. Esse é o padrão e não está mudando. Um lote de renders de ferramenta montada em 32x32 foi tentado em 2026 e descartado; seja qual for a resolução em que você trabalha, entregue em 16x16. As exceções não são sprites de item: as folhas de armadura vestida são 64x32, os painéis de interface das estações vão de 176x166 a 256x256, o ícone de efeito de status é 18x18, as partículas de golpe das armas são 32x32 e 16x32, e as texturas de fluido são faixas animadas altas. Cada exceção é indicada onde aparece abaixo.

Forged é o que você desenha. O Forgeweave tem dois conjuntos de arte. Forged é o conjunto padrão, o que todo jogador vê, e é o seu. Legacy é um resource pack embutido, desligado por padrão, que preserva o visual que o Forgeweave tinha antes da reforma de arte começar.

Nada do que você desenha vai para o Legacy. O pacote Legacy carrega só arte que veio da Tinkers' Construct, nada além disso. Coisas novas que o Forgeweave inventou, as formas de material como placa e engrenagem incluídas, existem só em Forged. Se você desenhar, o arquivo fica no caminho normal e nunca ganha uma cópia no Legacy (decisão do mantenedor, 2026-09-18).

Entregar um sprite pronto, na ordem em que acontece:

1. Você entrega o PNG: RGBA, no tamanho certo, com o nome certo. Puro cinza se for uma parte de ferramenta, uma camada de ferramenta ou uma folha de armadura vestida, porque esses são multiplicados pela cor do material em tempo real. Cor cheia para todo o resto.
2. Um desenvolvedor solta o arquivo no caminho normal, substituindo o que estava lá.
3. Se o arquivo substituído vinha da Tinkers' Construct, o desenvolvedor copia o arquivo antigo para o pacote Legacy, no mesmo caminho relativo. Se vinha do próprio Forgeweave, o arquivo antigo é simplesmente apagado.
4. O desenvolvedor roda de novo os quatro scripts geradores, para que todo molde de papel, molde de ouro, molde de argila e variante de ferramenta quebrada construídos a partir do seu sprite sejam refeitos nos dois conjuntos.
5. Testes e datagen rodam, e o arquivo é commitado.

O passo 1 é seu. As seções 4 e 7 do guia de arte cobrem o resto, se você quiser ler.

Onde uma entrada abaixo diz "tingido", o sprite é desenhado em cinza puro (R = G = B em cada pixel) e o jogo multiplica pela cor do material. Luminosidade é sua única ferramenta: um pixel branco sai na cor crua do material, um cinza médio sai pela metade da intensidade, um pixel preto continua preto. Use a faixa inteira, porque o seu contraste vira o contraste da peça pronta. Onde uma entrada diz "não tingido", pinte nas cores que quiser.

Parte de ferramenta e camada de ferramenta usam uma rampa de cinco valores de cinza: 68 para o contorno, 160 para a sombra mais funda, depois 196, 219 e 251 para o brilho. Ela existe porque uma ferramenta montada empilha três ou quatro camadas que são multiplicadas pela mesma cor de material; uma camada fora da rampa tinge diferente do resto da ferramenta e a costura aparece. Fique nela para qualquer coisa nas seções 3 e 4.

Cor nova tem que passar num teste. O Forgeweave escolhe um hex para cada material e fluido que inventa, e o `PaletteAuditTest` derruba o build se dois deles ficarem a menos de um passo perceptível de distância em OKLab. Um playtest de 2026 achou dois combustíveis oliva-amarelos, três lingotes teal, três roxos e três vermelhos que ninguém distinguia, e é por isso que o teste existe. Você não roda esse teste, mas se propuser uma cor para um material novo, espere que ela volte se cair em cima de uma já existente. [`scripts/audit_palette.py`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/audit_palette.py) reporta a mesma coisa fora do build.

Toda entrada linka o arquivo como ele está hoje, para você abrir o placeholder num navegador e ver o que vai substituir. Textura do Minecraft vanilla serve como referência de estilo. Arte da Tinkers' Construct não serve de referência para nada aqui: o ponto inteiro do Forged é que ele é do Forgeweave.

Um harness de captura de tela, só para desenvolvimento, tira uma imagem de cada tela, cada arma segurada, cada conjunto de armadura vestida e cada página do livro, para um desenvolvedor te mandar o quadro exato sem você instalar nada. As entradas abaixo nomeiam o quadro onde existe um. O que pedir ao revisar uma troca é `forged_legacy_compare`, que põe as versões Forged e Legacy de um ícone lado a lado.

Prioridade quer dizer com que frequência o mantenedor vê o asset jogando:

- Toda sessão: na tela o tempo todo. Corrija esses primeiro.
- Às vezes: aparece em jogo normal, mas não a cada minuto.
- Raro: progressão profunda, uma combinação opcional de mods, ou um caso de borda.

## Como esta lista foi construída

O `NOTICE.md` da raiz do Forgeweave carrega uma linha por arquivo que veio de outro lugar. Uma textura com linha é arte da Tinkers' Construct (ou da Mantle, ou do addon de tool leveling), então não está nesta lista: ela sai sozinha quando um sprite Forged a substitui. Uma textura sem linha é do próprio Forgeweave, e é isso que esta lista reúne. Onde a origem de um arquivo não estava clara, o histórico de commits resolveu.

A auditoria cobriu todo PNG do mod: 1.872 arquivos no total, contra os 1.837 da auditoria anterior. 1.111 têm uma linha no `NOTICE.md`. Os 761 restantes são do Forgeweave, 685 deles nos caminhos padrão e 76 dentro do pacote Legacy (composites de molde e molde de ouro construídos por script que só existem porque os dois conjuntos têm bases diferentes; não são de ninguém desenhar).

Boa parte desses 685 continua sendo placeholder nunca desenhado por um humano: as formas de material e a família de lingote/minério (seção 1 e 2 abaixo) somam 554 arquivos escritos por um script Python recolorindo uma textura vanilla do Minecraft, a partir de pouco mais de uma dúzia de templates que um designer realmente desenha.

## 1. Formas de material

O maior grupo da lista, e o mais barato de resolver.

Todo material do Forgeweave, 46 deles, tem um conjunto de itens intermediários: dust que você mói, plate que você prensa, um rod, uma gear, um wire. Um jogador vê isso o tempo todo em slots de inventário, no livro de receitas, e em máquinas de outros mods. São 11 formas, todas as mesmas silhuetas em cores diferentes.

| Forma | O que é | Quantidade | Placeholder atual |
| --- | --- | --- | --- |
| dust | Material moído, o insumo de fundição | 48 | vanilla `glowstone_dust`, recolorido |
| small dust | Um terço de um dust, 48 mB contra 144 | 48 | O mesmo, encolhido para 10x10 numa tela 16x16 |
| tiny dust | Um nono de um dust, 16 mB | 48 | O mesmo, encolhido para 6x6 |
| plate | Chapa prensada, saída de crafting | 46 | vanilla `paper`, recolorido |
| double plate | Duas plates prensadas juntas | 46 | Duas cópias de `paper` a 13x13, deslocadas 3px |
| rod | Vareta de metal | 46 | vanilla `blaze_rod`, recolorido |
| gear | Engrenagem | 46 | vanilla `nether_star`, recolorido |
| wire | Fio puxado | 46 | vanilla `string`, recolorido |
| clump | Um punhado bruto do minério, antes da lavagem (issue #994) | 11 | vanilla `clay_ball`, recolorido |
| dirty dust | Dust ainda sujo, um passo antes do dust limpo (issue #994) | 11 | vanilla `gunpowder`, recolorido |
| shard | Estilhaço do minério, uma etapa da cadeia de britagem (issue #994) | 11 | vanilla `prismarine_shard`, recolorido |

`clump`, `dirty dust` e `shard` só existem para os onze minérios Track B que passam pela cadeia de processamento do Mekanism (issue #1060); os outros materiais não têm essas três formas.

Caminhos: `src/main/resources/assets/forgeweave/textures/item/<material>_<forma>.png`. Todos 16x16. [Abra a pasta](https://github.com/gkissel/forgeweave/tree/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/item).

O que você realmente desenha: 11 sprites, um por forma, uma vez cada. A gear é a pior das oito originais e a que mais merece atenção: uma nether star recolorida não lê como engrenagem em nenhum tamanho.

Cada forma tem seu próprio arquivo em [`scripts/templates/material_forms/`](https://github.com/gkissel/forgeweave/tree/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/templates/material_forms): `dust.png`, `small_dust.png`, `tiny_dust.png`, `plate.png`, `double_plate.png`, `rod.png`, `gear.png`, `wire.png`, `clump.png`, `dirty_dust.png`, `shard.png`. Abra um, substitua por um sprite 16x16 RGBA seu no mesmo nome de arquivo, e rode de novo `scripts/generate_material_forms.py`: todo material se regenera a partir dele, sem instalação do Minecraft e sem client jar envolvido. `small_dust.png`/`tiny_dust.png` e `double_plate.png` são arquivos independentes, não derivados de `dust.png`/`plate.png` na hora de gerar, então redesenhar a forma de tamanho cheio não redesenha os tamanhos menores nem o par empilhado: troque os três juntos se a escala inteira de uma forma deve mudar.

O tingimento aqui funciona diferente. Não é a multiplicação simples de cinza usada nas partes de ferramenta. O script troca o matiz de cada pixel pelo matiz do material e escala sua saturação e luminosidade pela razão entre a cor do material e a cor média do próprio sprite. Duas consequências para você:

Não desenhe essas formas em cinza puro. Um sprite totalmente cinza não tem saturação para escalar, e o script cai num chroma plano por cima de tudo, perdendo toda a sua variação de cor. Desenhe em cor, numa saturação média neutra, e o recolorimento preserva sua sombra.

Mantenha separação clara de luminosidade entre o corpo da forma e sua sombra. A luminosidade do material é aplicada como uma razão sobre a sua média, então um sprite chapado continua chapado nas 46 cores.

Prioridade: toda sessão. Essas formas ficam na tela mais do que qualquer outra coisa no mod.

## 2. Lingotes, pepitas, minério bruto e blocos

A moeda de cada material inteira num só lugar: o que o bloco de minério solta, no que ele funde, a pepita em que quebra, o bloco onde ele mora na parede e o bloco onde ele se guarda.

| Grupo | O que é | Caminho | Templates | Sprites gerados | Placeholder atual |
| --- | --- | --- | --- | --- | --- |
| Lingote | Barra fundida, a unidade que vai pro molde | `textures/item/<material>_ingot.png` | 1 | 37 | Lingote vanilla de iron/copper/gold/diamond/redstone/lapis/emerald, recolorido |
| Pepita | Um nono do lingote | `textures/item/<material>_nugget.png` | 1 | 37 | A pepita do mesmo doador, recolorida. Copper cai para a pepita de iron, porque o vanilla não tem pepita de copper |
| Minério bruto | O que o bloco de minério solta antes de fundir | `textures/item/raw_<material>.png` | 1 | 12 | vanilla `raw_iron`/`raw_copper`/`raw_gold`, recolorido |
| Cristal (gema bruta) | Brimspar e fulmenite soltam um cristal facetado em vez de um pedaço bruto | `textures/item/{brimspar,fulmenite}_crystal.png` | 1 | 2 | vanilla `amethyst_shard`, recolorido |
| Bloco de minério | O minério na parede, durante a mineração | `textures/block/<material>_ore.png` | 1 | 12 | Uma textura de minério vanilla com a rocha hospedeira intacta e só o veio mineral recolorido. Os minérios de end stone são pintados por cima do `end_stone` vanilla, porque o vanilla não tem minério próprio de end stone |
| Bloco de armazenamento | Nove lingotes compactados | `textures/block/<material>_block.png` | 1 | 37 | Bloco metálico vanilla, totalmente recolorido |
| Bloco de armazenamento de minério bruto | Nove minérios brutos compactados | `textures/block/raw_<material>_block.png` | 1 | 10 | Bloco bruto vanilla, totalmente recolorido |

Total: 7 templates, 147 sprites gerados a partir deles. Todos 16x16, não tingidos em tempo de execução, um arquivo por material. Escritos por [`generate_track_b_ore_textures.py`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/generate_track_b_ore_textures.py) e [`generate_track_b_alloy_textures.py`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/generate_track_b_alloy_textures.py); os dois raw drops mais antigos, cobalt e ardite, vêm de [`recolor_raw_ore.py`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/recolor_raw_ore.py). [Abra a pasta de itens](https://github.com/gkissel/forgeweave/tree/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/item) e a [pasta de blocos](https://github.com/gkissel/forgeweave/tree/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/block).

Mesma ressalva de cor do que de cinza da seção 1 para o lingote, a pepita, o minério bruto e o cristal: não há template já registrado no repositório para esses quatro ainda, então desenhe em cor, numa saturação média.

O bloco de minério merece atenção à parte: o script decide quais pixels são "minério" e quais são "rocha" comparando o doador contra a textura pura da rocha hospedeira, e precisa de uma separação limpa entre as duas. Um veio mineral suave e borrado quebra essa máscara, então mantenha a forma do mineral nítida contra a rocha. A rocha hospedeira varia por material (stone, deepslate, netherrack, end stone), então o veio precisa ficar legível nas quatro.

Note que cobalt, ardite, manyullyn, rose gold, steel, knightslime, pig iron, amethyst bronze, queen's slime e hepatizon não entram nessas contagens. O lingote e a pepita deles são arte da Tinkers' com linha no `NOTICE.md`, então ficam fora desta lista até um lote Forged chegar neles.

Prioridade: toda sessão para minério bruto, lingote, pepita, cristal e bloco de minério. Às vezes para bloco de armazenamento e bloco de armazenamento de minério bruto.

## 3. Silhuetas de partes de ferramenta que ainda são arte da Tinkers'

A silhueta de uma parte de ferramenta comanda quatro arquivos. Redesenhe a parte e os scripts reconstroem seu molde de papel, seu molde de ouro, seu molde de argila e, onde existe, sua arte quebrada, nos dois conjuntos de arte. 17 silhuetas de parte ainda são arte da Tinkers' no caminho padrão:

`cross_guard`, `excavator_head`, `fletching`, `hammer_head`, `kama_head`, `large_sword_blade`, `maille`, `pan`, `pickaxe_head`, `shard` (a parte de ferramenta da Tinkers', sem relação com a forma de minério `shard` da seção 1: são dois conceitos diferentes com o mesmo nome em inglês), `sharpening_kit`, `shovel_head`, `sign_plate`, `sword_blade`, `tough_tool_rod`, `vein_hammer_head`, `wide_guard`.

Caminhos: `textures/derived/item/<parte>.png` e, para a camada de ferramenta montada, `textures/derived/tools/<ferramenta>_<papel>.png`. Todos 16x16. [Abra a pasta de itens](https://github.com/gkissel/forgeweave/tree/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/derived/item).

Doze outras já são Forged: `arrow_head`, `arrow_shaft`, `axe_head`, `bow_limb`, `bow_string`, `broad_axe_head`, `hand_guard`, `knife_blade`, `scythe_head`, `tool_binding`, `tool_handle`, `tough_binding`.

Sobre o longsword especificamente, já que ele é um dos exemplos citados como pronto: suas camadas de ferramenta montada (`longsword_handle.png`, `longsword_head.png`, `longsword_binding.png` e a cabeça quebrada) são Forged de verdade, sem linha no `NOTICE.md` no caminho padrão, então o longsword em si não precisa de nada novo. Mas a cabeça dele é montada a partir da parte `sword_blade`, que está na lista acima: essa silhueta continua sendo arte da Tinkers', compartilhada com o broadsword e o rapier. Redesenhar `sword_blade` não é um pedido específico do longsword, mas continua pendente.

Essas partes são tingidas. Desenhe em cinza puro, com os três canais iguais, e carregue a forma no canal alfa. Dois detalhes de script decorrem disso:

O script do molde de papel lê o alfa com limiar de 64. Um pixel mais fraco que isso é tratado como ausente e não deixa marca.

Cada parte tem um deslocamento escolhido à mão para a marca sair centralizada no molde. Se o seu redesenho mover a silhueta na tela, esse deslocamento precisa ser remedido: avise ao entregar o arquivo.

Quadros para consultar: `tool_station`, `part_builder`, `stencil_table`, e `weapon_<ferramenta>` para cada arma montada.

Prioridade: toda sessão para `pickaxe_head`, `shovel_head`, `sword_blade`, `tough_tool_rod`, `tool_handle`. Às vezes para o resto.

Enquanto estiver nessa pasta, uma nota sobre as cinco camadas do rapier e do scythe: `rapier_binding`, `rapier_handle`, `rapier_head`, `rapier_head_broken` e `scythe_binding` carregam um ou dois pixels que não são exatamente cinza. Vão tingir com um leve desvio de matiz. Invisível a 16 pixels, mas vale limpar se você redesenhar esse conjunto.

## 4. O tanque energizado

Uma parede de fundição que queima Forge Energy em vez de lava, adicionada na PR #1014. Não existe equivalente na Tinkers', então tudo aqui é do Forgeweave.

### Texturas de bloco, 3 arquivos

`textures/block/energized_tank_side.png`, `energized_tank_side_overdrive.png`, `energized_tank_top.png`. Todos 16x16, desenhados à mão por um agente, não tingidos.

Ele mostra dois estados, não três: o blockstate tem um único booleano, `overdrive`, e `energized_tank_top` serve tanto de topo quanto de base. A tela mostra calor e carga como números, mas o bloco em si não muda com o nível de carga. Se devesse mudar, isso é arte nova e uma propriedade nova de blockstate: levante isso antes de desenhar.

- [energized_tank_side.png](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/block/energized_tank_side.png)
- [energized_tank_side_overdrive.png](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/block/energized_tank_side_overdrive.png)
- [energized_tank_top.png](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/block/energized_tank_top.png)

Prioridade: às vezes.

### A tela dele, e o único widget desenhado à mão do mod

A PR #1026 deu uma tela ao tanque. Cada pixel dela vem emprestado de outra tela, exceto uma barra.

O fundo é o painel de estação em branco da Tinkers', [`derived/gui/blank.png`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/src/main/resources/assets/forgeweave/textures/derived/gui/blank.png), 256x256 desenhado como uma janela de 176x166, o mesmo arquivo do baú de padrões. A metade de cima inteira dele está vazia. O medidor de combustível e sua sobreposição de escala 52x52 vêm da folha de sprites da tela da fundição.

A barra de energia é desenhada como dois retângulos preenchidos direto no código, sem sprite por trás:

- Trilho: 102 de largura por 8 de altura, nas coordenadas do painel (66, 36), cor `#373737`.
- Preenchimento: recuado um pixel em cada lado, então 100 por 6 em (67, 37), cor `#E8620E`, o laranja padrão da Forge Energy.
- Sem borda, sem ponta, sem gradiente.

O comentário da classe explica o motivo: um nível de carga não é um fluido, e nenhuma das folhas portadas tem uma barra horizontal.

O resto da coluna direita, para referência de layout: calor em y=16, custo em y=26, a barra em y=36, os números de carga em y=46, e o botão de overdrive em y=56, todos em x=66 e 102 de largura.

A PR #1056 adicionou um quadro no harness de captura de tela para essa tela (`energized_tank`, em `ScreenshotHarness.SCREENS`), então ela já pode ser revisada sem abrir o jogo, como qualquer outra estação.

Prioridade: às vezes. É a única tela que ainda parece inacabada, então talvez valha mais do que sua frequência de uso sugere.

## 5. Armadura pesada

O conjunto está no jogo e é jogável, e não tem nenhuma arte própria: todo pixel que ele mostra pertence ao conjunto de armadura leve.

Quatro itens, `heavy_helmet`, `heavy_chestplate`, `heavy_leggings` e `heavy_boots`, montados a partir de três materiais cada: plating, maille, e um terceiro slot de placa grande que o conjunto leve não tem.

Isso se divide em três frentes, para orçar uma de cada vez.

Os sprites de item vêm primeiro. As peças pesadas hoje renderizam os sprites do conjunto leve, porque o código tira o prefixo `heavy_` antes de procurar a arte. Então uma peça de peito pesada e uma leve são idênticas no inventário. Pior, o terceiro material é invisível: o modelo do item filtra a camada de placa grande por completo, com um comentário no código dizendo que faz isso porque essa camada não tem sprite. Entregável: 4 camadas de plating e, se o terceiro material deve aparecer, 4 camadas de placa grande. `textures/derived/tools/<peça>_{plating,maille}.png`, 16x16, tingido, em cinza.

Depois vêm as camadas de armadura vestida, as folhas que renderizam no corpo do jogador, em 64x32, o layout legado de armadura do Minecraft, não 16x16. Hoje existem quatro, compartilhadas entre o conjunto leve e o pesado: `textures/models/armor/derived/{plating,maille}_layer_{1,2}.png`. A camada 1 cobre capacete, peitoral e botas; a camada 2 cobre as calças. As quatro são arte da Tinkers' com linha no `NOTICE.md`, e são em cinza, tingidas em tempo de execução pela cor do material da peça de plating ou de maille equipada. Entregável: até 4 folhas novas se só o conjunto pesado ganhar visual próprio, 8 se os dois ganharem.

A terceira frente, trabalho de modelo 3D, não precisa de nada. Os dois conjuntos desenham sobre a malha humanoide do vanilla, uma passada por camada declarada. Não existe classe de modelo customizada nem nada para riggar. Uma geometria mais robusta para a armadura pesada seria mudança de código, então levante isso como pedido em vez de presumir.

Quadros: `tool_forge_heavy_armor` para a aba da estação, `armor_heavy_iron` e `armor_heavy_iron_firstperson` para o visual vestido. Compare contra `armor_iron`, `armor_cobalt` e `armor_obsidian_chestplate`.

Prioridade: às vezes. O conjunto leve carrega o visual hoje, então nada está quebrado, mas um tier visualmente idêntico ao tier abaixo dele é uma lacuna real.

## 6. Coisas sem nenhuma arte

Cada uma destas é um entregável limpo e autocontido.

| Asset | Onde entra | Tamanho | Estado |
| --- | --- | --- | --- |
| Logo do mod | raiz do jar, ligado via `logoFile` em `neoforge.mods.toml` | 128x128 ou 256x256 | Não existe. O manifesto carrega uma decisão registrada adiando isso, porque derivar o logo da engrenagem da Tinkers' passaria a impressão de estar se passando pela marca deles |
| `pack.png` do mod | `src/main/resources/pack.png` | 128x128 | Não existe |
| `pack.png` do pacote Legacy | `src/main/resources/resourcepacks/legacy/pack.png` | 128x128 | Não existe, então o pacote mostra o placeholder de textura faltando na lista de resource packs |
| Fundo da aba de conquistas | `textures/gui/advancements/backgrounds/` | repetível | Reaproveita o fundo de pedra do vanilla. Opcional |
| Partícula de golpe do longsword | `textures/particle/` mais uma reescrita de `particles/slash_longsword.json` | 8 quadros | Toma emprestado o `sweep_0` a `sweep_7` do vanilla direto. Toda outra arma tem sua própria folha de golpe. Opcional, mas é a única arma cujo golpe não bate com as outras |

Prioridade: raro para todos, exceto os dois `pack.png` e o logo, que são a primeira coisa que qualquer um vê numa lista de mods. Chame esses de às vezes.

`slime_layer_2.png` saiu desta tabela: era listado aqui como faltando, mas a investigação registrada nas perguntas em aberto no fim deste documento mostrou que a armadura de slime não tem peça de calças, então esse arquivo nunca é necessário.

## 7. Itens avulsos

Três sprites de item que nenhum template cobre.

| Item | O que é | Caminho | Origem |
| --- | --- | --- | --- |
| Dusk cage | Uma jaula de lanterna de murkiron que captura um mob | `textures/item/dusk_cage.png` | Desenhado proceduralmente do zero por [`generate_dusk_cage_texture.py`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/generate_dusk_cage_texture.py), usando uma paleta de cinco cores a partir do `#3A5C56` do murkiron |
| Weldheart | O catalisador que uma fusão do Draconic Evolution consome | `textures/item/weldheart.png` | Desenhado proceduralmente por [`generate_weldheart_texture.py`](https://github.com/gkissel/forgeweave/blob/ef1ca1afc03b2ffae6a4937164112171e25039b7/scripts/generate_weldheart_texture.py): três diamantes aninhados nas cores dos três metais de solda |
| Nahuatl board | Um intermediário de crafting que torna alcançáveis o plating e o maille de nahuatl | `textures/item/nahuatl_board.png` | Desenhado à mão por um agente na PR #740. O único sprite em `textures/item/` que nenhum script produz |

Todos 16x16, não tingidos, pinte em cor cheia. Prioridade: raro.

## 8. O que já está coberto, então pode pular

Vale dizer isso para ninguém passar um dia em cima de algo que não é uma lacuna.

- Toda folha de GUI: as 21 são arte da Tinkers' ou da Mantle, com linha no `NOTICE.md`: a bancada de ferramentas, a bancada de peças, a mesa de estêncil, a fundição, o forno seared, o reservatório e o duto, o painel do baú, o painel lateral e o painel de informações compartilhados, a folha de ícones das estações, as duas miras de arco. A estação de crafting usa de propósito o painel da mesa de crafting do vanilla, igual a própria Tinkers' faz. Os únicos pixels de GUI que o Forgeweave desenha são a barra de energia do tanque energizado (seção 4) e um realce de hover chapado no livro.
- O Modifier Worktable inteiro (#1064): a folha da tela (256x256), o topo do bloco e a lateral do bloco vêm copiados sem alteração da branch 1.20 upstream, cada um com linha no `NOTICE.md`. Nada para desenhar aqui, pelo mesmo motivo das folhas de GUI acima.
- O anel de rotação do preview no armor stand (#1053): `StandPreview.java` desenha esse anel a partir de um recorte de `icons.png` da branch 1.20, com linha no `NOTICE.md`. Também nada para desenhar.
- As quatro camadas de fundição (standard, Nether, End, Deep): a padrão é 100% arte da Tinkers'. As outras três (`seared_bricks_{nether,end,deep}.png` e os arquivos do core de cada uma) já são Forged, desenhadas nas PRs #983 e #987. O script que deriva as outras faces de cada tier a partir de um único tijolo está documentado no guia de arte, para quando uma quinta camada for cogitada.
- As armas próprias do Forgeweave: katana, scimitar e war mace, mais as partes `curved_blade` e `war_mace_head`, não existem na Tinkers' Construct e nunca ganham cópia no Legacy. Todas as camadas delas são Forged, sem linha no `NOTICE.md`, redesenhadas nos lotes 16px das PRs #810, #977, #982 e #1001. O cast da cabeça do war mace ficou exclusivo de vazamento desde a PR #1052 (não sai mais do Part Builder por pattern), mas isso não muda a arte em si.
- Todo livro do guia: quatro arquivos, nenhum nosso. A dupla página e a capa, 512x512 cada, vêm da Mantle, a biblioteca da SlimeKnights onde mora o motor de livro do 1.12. A página de modificação (256x256) e o diagrama de fundição (854x480) vêm da Tinkers'. `appearance.json` não referencia nenhuma imagem; ele define um tingimento de capa `#ffce85`. Nenhuma página do livro aponta para um arquivo inexistente.
- Todo painel e ícone do JEI: quatro folhas de painel, todas com linha. Dez dos onze ícones de categoria são só uma pilha de item, então herdam o que quer que o sprite daquele item vire. O décimo primeiro, entity melting, recorta um quadrado 16x16 da folha de fundição.
- Todo ícone de aba criativa: seis abas, cada uma mostrando um item já existente: cristal de slime azul, picareta, cabeça de picareta, tanque seared, slime sling, solo de slime verde.
- Todo ícone de conquista: onze conquistas, todas mostrando uma pilha de item.
- Peças de armadura, moldes e casts: plating, maille e placa grande, com seus moldes de papel e seus casts de ouro e de argila, estão todos presentes e todos com linha. Sem lacunas.
- Fluidos: seis texturas para o mod inteiro, todas com linha, todas animadas: `molten_metal` e seu fluxo, `liquid` e seu fluxo, `liquid_stone` e seu fluxo, de 16 a 32 quadros cada, de 16x320 até 32x1024. Todo metal fundido compartilha o mesmo par em cinza e é tingido por fluido, e é por isso que são seis arquivos e não uma centena.
- Baldes de fluido: não há arte de balde para desenhar. O modelo de contêiner dinâmico do NeoForge desenha o fluido dentro de um balde vanilla e lê o tingimento do próprio fluido.
- Entidades: o mod adiciona quatro tipos de entidade e envia uma única textura de entidade, o slime azul, 64x32, com linha, em cinza e tingido `#67f0f5` em tempo de execução. Flecha, shuriken e itens indestrutíveis renderizam o próprio modelo de item, então não têm textura de entidade.
- Partículas: as 45 têm linha. Cinco sobreposições de coração a 8x8; cinco folhas de golpe de arma a 8 quadros cada, em 32x32 para machado e martelo, 16x32 para cleaver e rapier, 16x16 para a frigideira. O hatchet e o lumberaxe compartilham a folha do machado. A regra dos 16x16 não vale para quadros de golpe.
- Cenas de ponder: os sete arquivos `.nbt` são arranjos de blocos no mundo, não desenhos. Como ficam depende inteiramente das texturas de bloco das seções 2 e 4.
- Presets de material Track A: esses nunca ganham sprite próprio. Renderizam pelas partes em cinza da seção 3, tingidas pelo campo `color` do material. Então todo material Track A que um pack de compatibilidade adiciona herda de graça a sua arte de parte, e não há nada por material para desenhar. O mesmo vale para Occultism, Elementarium e os tiers do Allthemodium: são definições de material via datapack apontando para itens de outros mods.
- Sprites de plantação e essência do Mystical Agriculture (PR #1036): nada para desenhar aqui também, por um motivo diferente do Track A. `MysticalAgricultureCompat` passa a cada plantação do Forgeweave um `CropTextures` pronto mais o hex do material, e o próprio `client.ModelHandler`/`client.ColorHandler` do Mystical Agriculture constrói e tinge a flor, a essência e a semente em tempo de execução a partir disso. O Forgeweave não envia sprite próprio de nada disso, então não há linha no `NOTICE.md` e nada nesta lista.

## 9. A caminho: a família de tridente

Só planejamento, issue #990, sem código e sem arte ainda. Listado para poder ser agendado em vez de chegar de surpresa.

O mantenedor pediu um tridente do Forgeweave. A 1.12 da Tinkers' é anterior ao tridente vanilla, então não há arte nem design upstream. Tudo nele, arte incluída, vai ser original.

Três perguntas na issue decidem o que será desenhado: quantas partes o tridente tem (a proposta cogita uma forma de três partes, como o javelin que foi deixado de lado), se a cabeça aceita materiais não metálicos, e se riptide, channeling e loyalty viram traits ou modifiers. Riptide em particular decide se a ferramenta precisa de um visual de arremesso diferente do visual empunhado.

Se a forma se fechar em cabeça, cabo e fecho, espere o conjunto de sempre: três camadas em cinza tingidas em `textures/derived/tools/trident_{handle,head,binding}.png`, uma cabeça quebrada, e uma silhueta de parte em `textures/derived/item/trident_head.png`. O molde de papel, o molde de ouro e o molde de argila saem de graça dos scripts. Tudo 16x16.

Prioridade: a caminho. Não comece; espere a sessão de planejamento fechar.

## Resumo

| Seção | Templates para desenhar | Prioridade |
| --- | --- | --- |
| 1. Formas de material | 11 | Toda sessão |
| 2. Lingotes, pepitas, minério bruto e blocos | 7 | Toda sessão / às vezes |
| 3. Silhuetas de parte ainda em arte da Tinkers' | 17 | Toda sessão / às vezes |
| 4. Tanque energizado, bloco e tela | 4 | Às vezes |
| 5. Armadura pesada | 4 camadas de item + até 8 folhas vestidas | Às vezes |
| 6. Coisas sem nenhuma arte | 5 | Às vezes / raro |
| 7. Itens avulsos | 3 | Raro |
| 8. Já coberto (GUI, livro, JEI, fluidos, entidades, partículas, Modifier Worktable, armas próprias, camadas de fundição, e mais) | 0 | n/a |
| 9. Família de tridente | 0 até a #990 fechar | A caminho |
| **Total** | **aproximadamente 59 desenhos** | |

## Perguntas em aberto e coisas que não deu para classificar

Nada da auditoria original ficou sem classificar: todo PNG resolveu para uma linha no `NOTICE.md`, um script gerador nomeado, ou uma pull request específica. Os itens abaixo são decisões de julgamento, não falhas de classificação, preservados da auditoria de `ba4c0b8d` porque continuam valendo.

1. `brimspar_ore.png` é 16x16, cor cheia, sem linha, então é do Forgeweave de qualquer jeito. Chegou na PR #907 junto do trabalho da escada de combustível, em vez de num lote de textura Track B, e o script de minério monta sua tabela de doadores na importação, então se brimspar está nesse elenco ou foi posto à mão não dava para saber só pela tabela. Trate como trabalho da seção 2 de qualquer forma.
2. `slime_layer_2.png`, resolvido na issue #1049: não é bug. A armadura de slime é um único item, a bota de slime (`SlimeBootsItem`, `Type.BOOTS`), não um conjunto completo de capacete/peitoral/calças/botas como plating e maille são; não existe item de calças de slime nenhum, então não há nada que algum dia precisasse de uma folha de camada 2. Plating e maille têm o par porque os dois cobrem uma peça de calças de verdade; a armadura de slime não.
3. Dois arquivos mortos da katana, resolvido na issue #1049: apagados. `derived/tools/katana_binding.png` e `derived/tools/katana_handle.png` eram idênticos byte a byte ao par ativo em `textures/tools/`, confirmados sem referência em nenhum modelo gerado, código Java ou script, e removidos.
4. Se as formas de material deveriam continuar usando doadores vanilla, resolvido na issue #1049: não. Ver seção 1 acima: cada forma agora tem seu próprio arquivo de template, e o script tinge esses em vez de ler o client jar.
5. O tanque energizado não tinha quadro de captura de tela, resolvido na issue #1049. `ScreenshotHarness.SCREENS` agora tem uma entrada `energized_tank`, o mesmo padrão de bloco único de toda outra estação estilo M1. As visões de JEI `SmelteryFuelCategory`/`CoreTransformCategory` ganharam quadro do mesmo jeito, adicionadas a `JeiScreenshotHarness.TYPES`.
6. Dois scripts geradores pareciam ultrapassados por lotes Forged, resolvido na issue #1049. O war mace é uma ferramenta exclusiva do Forgeweave (CLAUDE.md), então `derive_warmace_art.py`, que só conseguiria escrever pixels derivados da Tinkers' em qualquer um dos dois conjuntos, foi apagado de vez. O battleaxe é nativo da Tinkers', então `generate_battleaxe_head.py` continua, reescrito para gravar por `sprite_sets.save_legacy_if_different` em vez de direto na árvore padrão: uma camada que o lote Forged atual ainda não tocou (`battleaxe_handle.png`/`battleaxe_binding.png`, hoje) não faz nada, e uma camada que um sprite Forged já substituiu (`battleaxe_head.png`/`battleaxe_head2.png`, hoje) tem seus pixels pré-Forged gravados no pacote Legacy em vez de sobrescrever o Forged padrão.
