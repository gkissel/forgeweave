# Guia de arte para designers

*(English version: [texture-manifest.md](texture-manifest.md))*

Este documento é para quem desenha os sprites do Forgeweave e não conhece Java nem a API do Minecraft. Ele substitui a versão antiga deste arquivo, que parou de valer depois da issue #796 (a separação entre arte Forged e Legacy) e nunca chegou a explicar como as ferramentas são montadas visualmente, o que é gerado por script e como funcionam as sobreposições de modificador. Toda regra abaixo foi conferida direto no código Java e nos scripts Python do repositório; onde uma regra existe só como código (não como comentário ou documento), eu digo qual arquivo a aplica, para você saber onde perguntar se algo aqui ficar desatualizado.

## 1. Formato e tela

Quase todo sprite de item, parte de ferramenta ou molde é um **PNG RGBA de 16x16 pixels**. Confirmei isso abrindo os arquivos: `pattern.png`, `cast.png`, `pickaxe_head.png` e o restante dos sprites de parte e de camada de ferramenta estão todos em 16x16, modo RGBA.

Exceções de tamanho:

- **Armadura vestida** (as duas folhas que aparecem no corpo do jogador, não o ícone do item): 64x32 RGBA, o tamanho padrão de textura de armadura do Minecraft. São `models/armor/derived/plating_layer_1.png` e `maille_layer_1.png` (mais as versões `_2` para calças, que usam a folha de pernas). Ver a seção 3 para como essas folhas se relacionam com os sprites de item da peça.
- **Painéis de interface das estações**: `tool_station.png` e `smeltery.png` são 256x256; `part_builder.png` e `stencil_table.png` são 176x166; `generic.png` é 64x64; `info_panel.png` e `station_icons.png` são 256x256. Esses painéis são "contrato de layout": o código Java desenha slots, medidores e texto em coordenadas de pixel fixas alinhadas a essa arte (um teste automatizado até fixa a geometria da grade de fusão da fundição contra o PNG). Redesenhe cor e detalhe à vontade, mas não mude as dimensões do painel nem a posição de nenhum slot, janela ou medidor sem avisar um programador primeiro.
- Molten metal (`molten_metal.png`, 16x320, e `molten_metal_flow.png`, 32x512) são faixas animadas verticais com um `.png.mcmeta` ao lado. Mantenha os dois arquivos e o layout de quadros se mexer neles.

O que a auditoria de CI realmente confere: `TextureReferenceAuditTest` (em `src/test/java/dev/gkissel/forgeweave/data/`) varre todo modelo JSON e todo literal de textura em Java e garante duas coisas: que o PNG referenciado existe no disco, e que ele está sob um prefixo que o atlas de blocos realmente empacota (`block/`, `item/`, por padrão, ou um prefixo de diretório declarado em `assets/minecraft/atlases/blocks.json`). Esse teste **não confere dimensão nem modo de cor**. Ele não vai reclamar se você entregar um arquivo do tamanho errado ou salvo em paleta indexada em vez de RGBA (achei inclusive alguns PNGs de GUI mais antigos, como `seared_furnace.png`, ainda em paleta indexada. RGBA é o padrão a seguir para arte nova). O que de fato quebra quando o tamanho está errado são os testes de composição de pixel a pixel descritos na seção 4 (`GreyscalePartTextureTest`, `PatternImprintCenteringTest`, `CastCompositeHoleTest`), porque eles comparam sua arte, pixel a pixel, contra o tamanho que a base já tem. Então trate 16x16 (ou 64x32 para armadura vestida) como regra rígida mesmo sem um teste dedicado gritando se você errar.

Alfa: fora das camadas tingidas (seção 2), a transparência é sempre funcional, nunca decorativa. Nos blocos da fundição, os buracos das janelas e medidores são por onde o fluido é desenhado; nos moldes de ouro (`cast_*.png`), a cavidade puncionada é o que faz o molde parecer um molde. Mantenha essas formas de alfa; bordas com meio-tom de alfa (antisserrilhado) seguem o mesmo julgamento visual do resto da arte pixel do jogo.

## 2. A regra do tingimento (a mais importante deste guia)

A maior parte da arte de item do Forgeweave (toda parte de ferramenta, toda camada de ferramenta montada e as duas folhas de armadura vestida) é desenhada **em tons de cinza puro** e recebe cor em tempo real, por código, de acordo com o material com que o jogador construiu aquele item. Isso está implementado em `ForgeweaveItemColors.java` (para os itens) e em `ForgeweaveItemClientExtensions.java` (para a armadura vestida, desde a issue #726); os dois aplicam exatamente o mesmo tingimento.

O que isso significa na prática, para quem desenha:

- **Desenhe em cinza puro.** Todo pixel deve ter R = G = B. Use apenas luminosidade (do preto ao branco) para expressar sombra, brilho e volume. Se você colocar matiz em algum pixel (um azul mais quente na sombra, por exemplo), essa matiz é destruída quando o motor multiplica aquele pixel pela cor do material: cinza puro é a única coisa que sobrevive à multiplicação de forma previsível.
- **O que a cor do material multiplica.** O tingimento é uma multiplicação de canal simples, o mesmo mecanismo do "ItemColor" do próprio Minecraft: `cor final = cinza da textura × cor do material ÷ 255`, canal por canal. Um pixel de luminosidade 255 (branco puro) sai exatamente na cor crua do material. Um pixel de luminosidade 128 sai na metade da intensidade dessa cor. Um pixel preto (0) continua preto não importa o material. Por isso o contraste de luz e sombra do seu desenho em cinza é o que decide o contraste final da peça tingida.
- **De onde vem a cor do material.** Cada material tem um campo `"color"` no seu JSON de datapack, em `src/main/resources/data/forgeweave/forgeweave/material/<id>.json`. Por exemplo, `cobalt.json` tem `"color": "#2882D4"`. Essa cor é dado do jogo, não é algo que você pinta. Para pré-visualizar como uma peça vai ficar tingida em um material específico, abra seu sprite em cinza no editor de imagem, jogue essa cor hex em uma camada de ajuste com modo de mesclagem "Multiplicar" por cima, e é aproximadamente isso que aparece no jogo (a matemática exata é a multiplicação de canal descrita acima).
- **Quais sprites NÃO são tingidos.** Os moldes de papel (`pattern*.png`), os moldes de ouro e de argila (`cast_*.png`, `clay_cast_*.png`), toda a arte de bloco e de interface, os itens de reagente de modificador (musgo, placa reforçada, joia de seda etc.) e ícones de efeito de status (como `lacerate.png`, do dote da cimitarra) são desenhados em cor cheia e aparecem exatamente como pintados, sem nenhum tingimento em tempo real. Pinte esses normalmente, com toda a cor que quiser.
- **O caso especial da armadura vestida (issue #726).** As duas folhas de textura de armadura vestida (`maille_layer_1.png`/`_2.png` e `plating_layer_1.png`/`_2.png`, 64x32 cada) também são cinza puro e tingidas em tempo real, pela mesma lógica de multiplicação, com a cor do material da peça de placa (`plating`) ou de malha (`maille`) equipada. Trate essas folhas exatamente como qualquer outra camada tingida.
- **Um sistema futuro que ainda não vale.** Existe um pipeline mais sofisticado de várias paradas de cor por material (`GreyToColorMapping`, `MaterialPartSprites`, `MaterialPartTextureProvider`, sob `src/main/java/dev/gkissel/forgeweave/data/sprite/`) que gera, no datagen, versões pré-tingidas de dois sprites de prova de conceito por material. Ele escreve o resultado numa pasta de teste (`textures/staging/part/`) que nenhum modelo do jogo lê hoje; é trabalho preparatório para uma reforma futura (M9) que ainda não afeta nada do que está no ar. Ignore esse sistema por enquanto: o que vale hoje, para tudo que você desenha, é a multiplicação simples descrita acima.

## 3. Anatomia de cada item montado

Cada ferramenta e cada peça de armadura é montada a partir de partes, e cada parte desenha uma camada de imagem separada quando o item é segurado ou exibido. As camadas são empilhadas por trás para frente numa ordem fixa de papel (cabo sempre atrás, depois as cabeças, depois a peça extra como guarda ou fecho), definida em `ToolArt.java`, e essa ordem **não é necessariamente a ordem em que as partes entram na Bancada de Ferramentas**. Por exemplo, o machado de guerra pesado da Vein Hammer entra na estação como cabeça, cabo, fecho, cabeça (issue #157), mas desenha na tela como cabo atrás, depois as duas cabeças, depois o fecho na frente, porque senão o cabo apareceria por cima da cabeça.

Cada camada tem um nome de papel (`handle`, `head`, `binding`, `limb`, `string`, `body`, `shaft`, `fletching`, `plating`, `maille`) e não o nome do slot da ferramenta. Quando uma ferramenta tem duas partes do mesmo papel (duas cabeças, dois cabos), a segunda em diante ganha um sufixo numérico: `head`, `head2`, `head3`. O arquivo de cada camada é `<ferramenta>_<nome_da_camada>.png`, salvo em `textures/derived/tools/`, exceto onde eu marquei arte 100% original abaixo.

A tabela a seguir foi conferida direto na lógica de `ToolArt.layerSlots`/`ToolArt.layers`, cruzada com a composição de partes de `ToolConstants.java` e `ToolAssemblyRecipes.java`. "Partes" está na ordem de montagem na estação; "Camadas" está na ordem real de desenho, de trás para frente.

| Ferramenta/peça | Partes (ordem de montagem) | Camadas (fundo → frente) | Arquivos |
| --- | --- | --- | --- |
| `pickaxe` | head=pickaxe_head, extra=tool_binding, handle=tool_handle | handle → head → binding | pickaxe_handle.png, pickaxe_head.png, pickaxe_binding.png |
| `shovel` | head=shovel_head, extra=tool_binding, handle=tool_handle | handle → head → binding | shovel_handle.png, shovel_head.png, shovel_binding.png |
| `hatchet` | head=axe_head, extra=tool_binding, handle=tool_handle | handle → head → binding | hatchet_handle.png, hatchet_head.png, hatchet_binding.png |
| `broadsword` | handle=tool_handle, head=sword_blade, extra=wide_guard | handle → head → binding | broadsword_handle.png, broadsword_head.png, broadsword_binding.png |
| `longsword` | handle=tool_handle, head=sword_blade, extra=hand_guard | handle → head → binding | longsword_handle.png, longsword_head.png, longsword_binding.png |
| `rapier` | handle=tool_handle, head=sword_blade, extra=cross_guard | handle → head → binding | rapier_handle.png, rapier_head.png, rapier_binding.png |
| `battlesign` | handle=tool_handle, head=sign_plate | handle → head | battlesign_handle.png, battlesign_head.png |
| `frying_pan` | handle=tool_handle, head=pan | handle → head | frying_pan_handle.png, frying_pan_head.png |
| `dagger` | head=knife_blade, handle=tool_handle | handle → head | dagger_handle.png, dagger_head.png |
| `warmace` | handle=tough_tool_rod, head=war_mace_head, extra=tough_binding | handle → head → binding | warmace_handle.png, warmace_head.png, warmace_binding.png |
| `mattock` | handle=tool_handle, head=axe_head, head=shovel_head | handle → head → head2 | mattock_handle.png, mattock_head.png, mattock_head2.png |
| `kama` | handle=tool_handle, head=kama_head, extra=tool_binding | handle → head → binding | kama_handle.png, kama_head.png, kama_binding.png |
| `battleaxe` | handle=tough_tool_rod, head=broad_axe_head, head=broad_axe_head, extra=tough_binding | handle → head → head2 → binding | battleaxe_handle.png, battleaxe_head.png, battleaxe_head2.png, battleaxe_binding.png |
| `scimitar` | handle=tool_handle, head=curved_blade, extra=cross_guard | handle → head → binding | scimitar_handle.png, scimitar_head.png, scimitar_binding.png |
| `katana` | handle=tool_handle, head=katana_blade, extra=hand_guard | handle → head → binding | katana_handle.png (original), katana_head.png (derivada), katana_binding.png (original) |
| `cleaver` | handle=tough_tool_rod, head=large_sword_blade, head=large_plate, extra=tough_tool_rod | handle → head → head2 → binding | cleaver_handle.png, cleaver_head.png, cleaver_head2.png, cleaver_binding.png |
| `hammer` | handle=tough_tool_rod, head=hammer_head, head=large_plate, head=large_plate | handle → head → head2 → head3 | hammer_handle.png, hammer_head.png, hammer_head2.png, hammer_head3.png |
| `excavator` | handle=tough_tool_rod, head=excavator_head, head=large_plate, extra=tough_binding | handle → head → head2 → binding | excavator_handle.png, excavator_head.png, excavator_head2.png, excavator_binding.png |
| `lumberaxe` | handle=tough_tool_rod, head=broad_axe_head, head=large_plate, extra=tough_binding | handle → head → head2 → binding | lumberaxe_handle.png, lumberaxe_head.png, lumberaxe_head2.png, lumberaxe_binding.png |
| `scythe` | handle=tough_tool_rod, head=scythe_head, extra=tough_binding, handle=tough_tool_rod | handle → handle2 → head → binding | scythe_handle.png, scythe_handle2.png, scythe_head.png, scythe_binding.png |
| `vein_hammer` | head=vein_hammer_head, handle=tough_tool_rod, extra=tough_binding, head=large_plate | handle → head → head2 → binding | vein_hammer_handle.png, vein_hammer_head.png, vein_hammer_head2.png, vein_hammer_binding.png |
| `shortbow` | limb=bow_limb, limb=bow_limb, bowstring=bow_string | limb → limb2 → string | shortbow_limb.png, shortbow_limb2.png, shortbow_string.png |
| `longbow` | limb=bow_limb, limb=bow_limb, extra=large_plate, bowstring=bow_string | limb → limb2 → binding → string | longbow_limb.png, longbow_limb2.png, longbow_binding.png, longbow_string.png |
| `crossbow` | crossbow_body=tough_tool_rod, limb=bow_limb, extra=tough_binding, bowstring=bow_string | body → limb → binding → string | crossbow_body.png, crossbow_limb.png, crossbow_binding.png, crossbow_string.png |
| `shuriken` | 4x shuriken_blade=knife_blade | head → head2 → head3 → head4 | shuriken_head.png, shuriken_head2.png, shuriken_head3.png, shuriken_head4.png |
| `arrow` | shaft=arrow_shaft, arrow_head=arrow_head, fletching=fletching | shaft → head → fletching | arrow_shaft.png, arrow_head.png, arrow_fletching.png |
| `helmet`, `chestplate`, `leggings`, `boots` | plating=plating_\<peça\>, maille=maille | maille → plating | \<peça\>_maille.png, \<peça\>_plating.png |

As três ferramentas de arcos (`shortbow`, `longbow`, `crossbow`) também têm variantes de camada por estágio de puxada (`_draw1`, `_draw2`, `_draw3`), sobre as camadas `limb`/`limbN` e `string` acima. Isso é coberto na seção 4, junto com o script que deriva essas variantes.

**Duas coisas que valem a pena saber, e que achei surpreendentes o suficiente para registrar aqui:**

1. **O terceiro slot da armadura pesada não desenha nada hoje.** As quatro peças de armadura pesada (`heavy_helmet`, `heavy_chestplate`, `heavy_leggings`, `heavy_boots`, issue #735) têm três partes na montagem: plating, maille e uma `large_plate` extra que entra nas estatísticas e nos traços do material. Mas `ToolArt.layerSlots` filtra a lista de camadas de qualquer item de armadura para conter só `MAILLE` e `PLATING`, então essa terceira parte não tem camada visual nenhuma: uma peça pesada renderiza com exatamente os mesmos dois arquivos que a peça normal da mesma família (`helmet_maille.png`/`helmet_plating.png` etc.), e a `large_plate` fica invisível no modelo. O próprio código marca isso como provisório ("armor draws only its maille and plating (...) drop this filter when it gets one"), esperando a arte de designer que o M9 deveria trazer. Se você desenhar uma terceira camada para a versão pesada, ela não vai aparecer até um programador tirar esse filtro e ligar o novo arquivo em algum lugar.
2. **O ícone do item avulso de uma parte e a camada da ferramenta montada nem sempre são o mesmo desenho.** Toda parte tem um ícone próprio quando está solta no inventário ou saindo da Bancada de Peças, em `textures/derived/item/<id_da_parte>.png` (por exemplo `tool_binding.png`). Isso é um arquivo **diferente** de `textures/derived/tools/<ferramenta>_<camada>.png`, mesmo quando os nomes parecem coincidir. Para algumas partes (a cabeça da picareta, a cabeça do martelo) os dois arquivos são pixel a pixel idênticos hoje. Para outras (a proteção larga da broadsword, o fecho da picareta) eles são diferentes, porque o ícone avulso é centralizado como um item comum e a camada da ferramenta precisa se alinhar com as outras camadas empilhadas por cima ou por baixo dela. Antes de desenhar uma parte nova, olhe o par existente (ícone solto x camada de ferramenta) de uma parte do mesmo papel para saber se você precisa desenhar um arquivo só ou dois arquivos com enquadramentos diferentes.

## 4. O que você desenha à mão e o que é gerado por script

A maior parte da arte de item do Forgeweave não é 100% desenhada à mão: é um sprite base desenhado por você, mais um script Python em `scripts/` que compõe as variantes derivadas dele (molde de papel, molde de ouro, molde de argila, arte quebrada). Isso quer dizer que, na maioria das vezes, você desenha **uma parte** e roda scripts, em vez de desenhar quatro ou cinco arquivos derivados um por um.

Os quatro scripts que você roda toda vez que muda a arte de uma parte ou de uma cabeça de ferramenta:

- **`scripts/generate_pattern_textures.py`**: lê o molde de papel em branco (`pattern.png`) e a silhueta em cinza de cada parte, e escreve `pattern_<parte>.png`, um molde de papel com a silhueta da parte gravada nele (mais escura nas bordas, um pouco mais clara no interior). Cada parte tem um deslocamento (x, y) próprio no script, para a gravação sair centralizada no molde mesmo quando a arte da parte não está centralizada no seu próprio canvas de 16x16 (a issue #337 documenta esse problema). Se você redesenhar uma parte e a arte dela mudar de posição no canvas, pode ser necessário ajustar esse deslocamento também; peça a um programador se não tiver certeza.
- **`scripts/generate_cast_textures.py`**: lê o molde de ouro em branco (`cast.png`) e a mesma silhueta em cinza de cada parte, e escreve `cast_<parte>.png`: o molde de ouro com um buraco puncionado na silhueta da parte e um bisel escurecido ao redor do buraco.
- **`scripts/generate_clay_cast_textures.py`**: lê todo `cast_<parte>.png` já existente e escreve `clay_<mesmo_nome>.png`, aplicando o mesmo multiplicador de cor malva que o Tinkers' original usa para o molde de argila (upstream nunca teve um sprite de argila próprio; ele reaproveitava o modelo do molde de ouro com uma multiplicação de cor em tempo real que o Forgeweave não tem, então esse script faz essa multiplicação de uma vez e grava o resultado).
- **`scripts/derive_broken_art.py`**: gera a arte de ferramenta quebrada. Para a maioria das ferramentas, ele copia um arquivo de "quebrado" do próprio Tinkers' Construct 1.12. Para as cinco ferramentas sem equivalente no 1.12 (adaga, katana, cimitarra, vein hammer, warmace, mais o shuriken), não existe arte de referência, então o script aplica uma transformação algorítmica (`chip()`) na própria cabeça da ferramenta: projeta os pixels opacos no eixo principal da forma e apaga os 15% das pontas de cada lado, imitando visualmente uma lasca. Só a `head` (ou o `handle` do martelo, ou a `string` dos arcos) quebra visualmente; qual camada quebra em cada ferramenta está em `ToolArt.BROKEN_LAYERS`.

Duas exceções que ficam de fora dessa automação, e que encontrei ao ler os scripts:

- **A large plate** (a placa grande, usada como cabeça extra em várias ferramentas Tool Forge e como parte da armadura pesada) tem molde de papel e molde de ouro desenhados à mão pelo próprio Tinkers' Construct original (o rosto de creeper), em vez de compostos pelo script. `generate_pattern_textures.py` e `generate_cast_textures.py` simplesmente copiam esses dois arquivos, byte a byte, do clone 1.12, em vez de gerar a partir da silhueta da parte. Se a `large_plate.png` mudar de forma, esses dois arquivos hand-drawn **não** acompanham a mudança automaticamente; teriam que ser redesenhados à parte.
- **O cabo e o fecho da katana** (`textures/tools/katana_handle.png` e `katana_binding.png`, note a pasta `tools/` sem `derived/`) são arte 100% original da Forgeweave, não derivada de nenhuma fonte externa. A cabeça da katana (`katana_head.png`) vem da Spartan Weaponry (ver seção 8) e mora em `derived/tools/` como qualquer outra camada derivada, mas essas outras duas camadas ficam fora da árvore `derived/` de propósito, porque a Spartan Weaponry não tem guarda nem cabo de katana para emprestar (a arte dela funde a guarda em três pixels do corpo da lâmina e desenha o cabo em cor fixa, sem tingimento).

Se você está adicionando uma ferramenta ou peça nova (não só substituindo arte de uma existente), esses scripts geralmente cobrem o caso automaticamente desde que você entregue a silhueta da parte no lugar certo e com o nome certo; veja a seção 7 para o passo a passo completo.

## 5. Sobreposições de modificador

Quando um jogador aplica um modificador com efeito visual (afiado, diamante, esmeralda, reforçado, sorte, chamas, fantasmagórico, entre outros) numa ferramenta, o jogo desenha uma camada extra por cima do item montado: a sobreposição do modificador. Isso está em `ModifierArt.java`, o "irmão" de `ToolArt.java` para modificadores. Diferente das camadas de parte, uma sobreposição **nunca é tingida**: ela é desenhada em cor cheia, exatamente como pintada.

Regras que confirmei no código:

- O conjunto de modificadores com sobreposição é `ModifierArt.OVERLAY_MODIFIERS`. Um modificador fora dessa lista (os modificadores originais do Forgeweave: `searing`, `magnetic_pull`, `aquadynamic`, `resonant`, `far_reach`, `extra_slot`, `wind_burst`, e os embossments gerados) não desenha nada de propósito. O próprio Tinkers' Construct 1.12 também tem modificadores sem sobreposição (o modificador criativo, por exemplo), então isso não é uma lacuna, é intencional.
- O arquivo de uma sobreposição fica em `textures/derived/tools/mods/<ferramenta>_<modificador>.png`.
- Peças de armadura **nunca** têm sobreposição de modificador: o 1.12 não tinha armadura para ter essa arte, e a família de modificadores de defesa que a armadura usa hoje aparece pelas camadas vestidas, não por um ícone de item.
- Os três arcos (`shortbow`, `longbow`, `crossbow`) têm variantes de sobreposição por estágio de puxada (`_draw1`, `_draw2`, `_draw3`), mas nem todo par ferramenta/modificador tem as três. `ModifierArt.STAGED_OVERLAYS` lista exatamente quais combinações têm arte por estágio; qualquer combinação fora dessa lista mantém a sobreposição "não puxada" em todo estágio.
- Um par (ferramenta, modificador) pode não ter sobreposição por três motivos, todos documentados em `ModifierArt.NO_UPSTREAM_ART`: o modificador não se aplica àquela categoria de ferramenta (sorte recusa lançadores, por exemplo), ou o Tinkers' Construct original simplesmente nunca desenhou aquele par (uma lacuna do jogo original, não do Forgeweave).

**Como adicionar arte para um modificador que hoje não tem nenhuma:** isso precisa de uma mudança de código, não só de um arquivo novo. Desenhe o arquivo em `textures/derived/tools/mods/<ferramenta>_<modificador>.png` (siga o mesmo enquadramento 16x16 em cor cheia dos arquivos já existentes na pasta) e peça a um programador para adicionar o `ResourceLocation` do modificador em `ModifierArt.OVERLAY_MODIFIERS`. Sem essa segunda parte, o jogo nunca vai carregar o arquivo, não importa quão certo o nome dele esteja: um teste automatizado (`ModifierArtTest`) inclusive falha de propósito se sobrar um arquivo na pasta que nenhum par (ferramenta, modificador) resolve, então um arquivo órfão nem passa despercebido.

## 6. Forged e Legacy (issue #796)

Desde a issue #796, o Forgeweave tem dois conjuntos de arte:

- **Forged**: arte original nova, desenhada pelo designer do mantenedor. É o conjunto padrão, o que todo jogador vê sem fazer nada. Fica nos caminhos normais, em `src/main/resources/assets/forgeweave/textures/...`.
- **Legacy**: a arte que o Forgeweave usava antes da #796 (majoritariamente derivada do Tinkers' Construct 1.12, mais alguns arquivos da Spartan Weaponry). Continua disponível, mas como um resource pack embutido no mod, desligado por padrão, que o jogador liga manualmente em Opções > Pacotes de Recursos se quiser o visual antigo de volta. Esse pacote fica em `src/main/resources/resourcepacks/legacy/assets/forgeweave/...`, usando exatamente os mesmos caminhos relativos da árvore padrão, porque é assim que um resource pack do Minecraft sobrepõe uma textura: mesmo caminho relativo, o pacote habilitado vence.

Isso significa que um arquivo sob `textures/derived/...` não indica mais, sozinho, se os pixels que estão no ar são derivados de upstream. Conforme cada sprite Forged é adicionado, ele substitui o arquivo no caminho padrão, e o arquivo derivado antigo que ele substituiu se muda para o mesmo caminho relativo dentro do pacote Legacy (a linha dele no `NOTICE.md` se muda junto, ver seção 8). O nome da pasta `derived/` continua do mesmo jeito para todo arquivo, então hoje ela quer dizer "é aqui que ficaria uma nota de licenciamento se os pixels que estão no ar precisassem de uma", não "estes pixels são derivados".

**O passo a passo exato para adicionar o próximo sprite Forged**, extraído de `scripts/sprite_sets.py` e do fluxo que os quatro scripts geradores já seguem:

1. Solte o arquivo novo no caminho padrão de sempre (`assets/forgeweave/textures/...`), substituindo o que estava lá.
2. Copie o arquivo que ele substituiu para o mesmo caminho relativo dentro do pacote Legacy (`resourcepacks/legacy/assets/forgeweave/textures/...`).
3. Rode de novo os quatro scripts geradores: `generate_pattern_textures.py`, `generate_cast_textures.py`, `generate_clay_cast_textures.py` e `derive_broken_art.py`.
4. Um programador confere as linhas do `NOTICE.md` que precisam se mover para o pacote Legacy (e, se o arquivo vier da Spartan Weaponry, atualiza a nota de modificação em `licenses/APACHE-2.0-SpartanWeaponry.txt`).

Cada um dos quatro scripts do passo 3 já faz as duas passadas sozinho, sem precisar de nenhuma mudança de código: ele grava a saída Forged normalmente e, numa segunda passada, recompõe a mesma saída a partir dos arquivos do conjunto Legacy (usando o arquivo que o pacote Legacy já tiver como substituto, senão caindo de volta no arquivo Forged/padrão compartilhado) e só grava um arquivo dentro do pacote Legacy se o resultado for **diferente**, byte a byte, do que a passada Forged acabou de escrever. Isso existe para o pacote Legacy nunca acumular cópias idênticas ao Forged (haveria um teste, `LegacyResourcePackTest`, que falha se isso acontecer) e para nunca sobrar um arquivo órfão que não sobrepõe nada de verdade.

## 7. Checklist de entrega

O que entregar para um sprite novo ou substituído, e os comandos que um desenvolvedor roda para colocá-lo no ar:

**Você entrega:**

1. O PNG em RGBA, no tamanho certo (16x16 para a esmagadora maioria dos sprites de item e camada, 64x32 para uma folha de armadura vestida). Veja a seção 1.
2. Cinza puro (R=G=B em cada pixel) se o arquivo for uma parte, uma camada de ferramenta montada, ou uma folha de armadura vestida (tudo que a seção 2 marca como tingido). Cor cheia se for molde, molde de ouro/argila, sobreposição de modificador, ou qualquer arte de bloco/GUI/reagente.
3. O nome de arquivo certo, no lugar certo. Para uma parte nova: `textures/derived/item/<id_da_parte>.png` (o ícone avulso) e, se a parte entra numa ferramenta como camada, `textures/derived/tools/<ferramenta>_<papel>.png` (a camada montada, ver seção 3 para o nome do papel e a possibilidade de precisar de dois enquadramentos diferentes).
4. Se você está trocando a arte de uma parte que já tem molde, molde de ouro ou arte quebrada derivados, não precisa desenhar essas variantes você mesmo: elas são geradas pelos scripts da seção 4.

**Um desenvolvedor roda, depois que seu arquivo está no lugar:**

```
python3 scripts/generate_pattern_textures.py
python3 scripts/generate_cast_textures.py
python3 scripts/generate_clay_cast_textures.py
python3 scripts/derive_broken_art.py
./gradlew runData
./gradlew test
```

`./gradlew runData` regenera qualquer arquivo de datagen que dependa de arte (por exemplo, os modelos de item), e o resultado gerado precisa ser commitado junto: existe uma checagem de CI que barra o build se o datagen ficar desatualizado. `./gradlew test` roda a suíte inteira, incluindo os testes que comparam sua arte nova pixel a pixel contra o que os scripts geraram (`GreyscalePartTextureTest`, `PatternImprintCenteringTest`, `CastCompositeHoleTest`) e os que conferem se todo caminho de textura existe e está no lugar certo (`TextureReferenceAuditTest`, `ModifierArtTest`, `LegacyResourcePackTest`). Se algum desses testes falhar depois da sua entrega, normalmente quer dizer que um dos scripts acima precisa rodar de novo, ou que um deslocamento de composição (a issue #337 na seção 4) precisa de ajuste.

## 8. Licenciamento, em um parágrafo

Arte 100% original do Forgeweave (a maior parte do conjunto Forged, mais uma arte antiga como as duas camadas de cabo/fecho da katana) não precisa de nenhuma linha no `NOTICE.md`: é sua, sem obrigação nenhuma. Arte derivada de upstream (a maior parte do conjunto Legacy hoje, e qualquer arquivo Forged que ainda não tenha sido substituído) carrega uma linha por arquivo no `NOTICE.md`, na raiz do repositório, com a licença de origem (a maioria é MIT, do Tinkers' Construct; um punhado, como as lâminas da katana e da cimitarra, é Apache-2.0, da Spartan Weaponry, o que exige uma nota extra em `licenses/APACHE-2.0-SpartanWeaponry.txt`). Quando um sprite Forged substitui um arquivo derivado no caminho padrão (seção 6), a linha do `NOTICE.md` daquele arquivo não desaparece, ela se muda para o caminho do pacote Legacy, porque é lá que os pixels derivados continuam existindo de verdade. Você, como designer, nunca precisa ler nem copiar nada do Tinkers' Construct, da Mantle ou da Spartan Weaponry para desenhar arte Forged: o trabalho de mover e anotar as linhas do NOTICE é de quem revisa o seu arquivo antes de fazer o commit, não seu.

## Exemplo prático: do desenho ao jogo

Digamos que você vai redesenhar a cabeça da picareta (`pickaxe_head`), a parte mais simples que existe no jogo. O caminho completo:

1. Você abre `src/main/resources/assets/forgeweave/textures/derived/item/pickaxe_head.png` (16x16, RGBA) e redesenha a silhueta em cinza puro, mantendo o contraste de luz e sombra que quiser (lembre da seção 2: luminosidade é o que vai decidir o contraste final tingido).
2. Você entrega esse arquivo para um desenvolvedor, avisando se a silhueta mudou de posição dentro do canvas de 16x16 (isso importa para o deslocamento do molde de papel, seção 4).
3. O desenvolvedor copia o mesmo arquivo (ou uma versão reenquadrada, se a posição mudou) para `src/main/resources/assets/forgeweave/textures/derived/tools/pickaxe_head.png`, a camada da ferramenta montada, porque hoje os dois são pixel a pixel idênticos para a picareta (o que nem sempre é o caso, ver seção 3).
4. O desenvolvedor copia o `pickaxe_head.png` antigo (o que estava no ar antes da sua troca) para o mesmo caminho relativo dentro de `resourcepacks/legacy/...`, preservando o visual antigo como Legacy.
5. Ele roda `scripts/generate_pattern_textures.py` e `scripts/generate_cast_textures.py`, que recompõem `pattern_pickaxe_head.png` e `cast_pickaxe_head.png` a partir da nova silhueta (e gravam as versões Legacy correspondentes, porque a silhueta antiga do molde e do molde de ouro agora é diferente da nova).
6. Ele roda `scripts/generate_clay_cast_textures.py`, que recompõe `clay_cast_pickaxe_head.png` a partir do `cast_pickaxe_head.png` que acabou de mudar.
7. `pickaxe_head` está entre as cabeças com arte quebrada portada direto do Tinkers' Construct 1.12 (não uma das cinco que usam a transformação `chip()`), então `derive_broken_art.py` não precisa rodar de novo para ela: a arte quebrada dessa cabeça é um arquivo independente, não derivado da arte intacta.
8. `./gradlew runData` regenera os modelos gerados que citam essas texturas, e `./gradlew test` confirma que a picareta nova bate com o que os scripts geraram e que nenhum caminho de textura ficou órfão.
9. No jogo: a picareta montada com cabeça de cobalto mostra sua nova silhueta multiplicada pelo azul `#2882D4` do material; o molde de papel e o molde de ouro da cabeça de picareta na Bancada de Peças mostram a nova silhueta gravada e puncionada; e quem ligar o resource pack Legacy continua vendo a cabeça de picareta antiga, do jeito que era antes da sua troca.
