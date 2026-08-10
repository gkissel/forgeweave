# Manifesto de texturas para designers

*(English version: [texture-manifest.md](texture-manifest.md))*

Toda textura que o jogo renderiza é um PNG real em `src/main/resources/assets/forgeweave/textures/`. Nada é composto em tempo de execução. Substituir um arquivo por um **PNG de mesmo nome e mesmas dimensões** e recompilar o mod (`./gradlew build`) é tudo que é preciso — nenhum outro passo. O teste de CI `TextureReferenceAuditTest` quebra o build se uma textura referenciada sumir, então esta lista não descola silenciosamente do que está no jogo.

## Regras de pastas

| Pasta | Significado |
| --- | --- |
| `textures/derived/**` | Arte derivada do Tinkers' Construct 1.12 (procedência por arquivo no `NOTICE.md` na raiz do repositório). **São os alvos de substituição da reescrita com arte original (M9)** — substituí-las por arte original é o objetivo. |
| `textures/item`, `textures/block`, `textures/gui` | Arte original do Forgeweave. Já é nossa; redesenhe à vontade. |

## Legenda das flags

- **T (greyscale tingida)** — o arquivo é em tons de cinza e o jogo aplica a cor via código. Texturas de partes/ferramentas são tingidas por material (madeira marrom, cobalto azul, …); as duas faixas de metal fundido são compartilhadas por **todos os nove fluidos** e tingidas por fluido. Mantenha em tons de cinza — pintar cor nelas quebra todas as variantes tingidas. As cores em si são código/dados, não pixels.
- **G (gerada por script)** — produzida por um script em `scripts/` (`generate_cast_textures.py`, `recolor_raw_ore.py`). Substituições pintadas à mão funcionam, mas rodar o script de novo as sobrescreve.
- **L (contrato de layout)** — painel de GUI: o código desenha slots, medidores, abas e texto em coordenadas de pixel fixas alinhadas a essa arte (um teste unitário chega a fixar a geometria da grade de fusão ao PNG da fundição). Redesenhe cores/detalhes à vontade, mas mantenha as dimensões do painel e a posição de cada slot/janela/medidor.
- **A (alfa funcional)** — a transparência aparece no gameplay: os buracos de tanque/medidor/janela são onde o fluido é renderizado; as cavidades dos moldes leem como relevo. Mantenha as formas do alfa.
- **M (animada)** — tem um `.png.mcmeta` ao lado controlando a animação; mantenha os dois arquivos e o layout dos quadros (faixa vertical).

## Blocos — `derived/block/` (todas 16×16, salvo indicação)

| Arquivo(s) | Notas |
| --- | --- |
| `seared_bricks`, `seared_stone`, `seared_cobblestone`, `seared_cracked_bricks`, `seared_fancy_bricks`, `seared_square_bricks`, `seared_triangle_bricks`, `seared_small_bricks`, `seared_creeper`, `seared_paver`, `seared_road`, `seared_tile` | Família decorativa "seared". `seared_bricks` também é lateral/topo do Núcleo Padrão. |
| `standard_core_front_active`, `standard_core_front_inactive` | Frente do Núcleo Padrão da fundição (aceso/apagado). |
| `nether_core_front_active`, `nether_core_front_inactive`, `nether_core_side` | Núcleo do Nether — avermelhado de propósito para distinguir o tier. |
| `seared_tank_side`, `seared_tank_top`, `seared_gauge_side`, `seared_window_side`, `seared_window_top` | **A** — os buracos das janelas são recortes de alfa; o fluido aparece através deles. |
| `seared_drain_front`, `seared_drain_back` | Dreno. |
| `faucet.png` | Torneira. |
| `casting_table_top/side/bottom`, `casting_basin_top/side/bottom` | Blocos de moldagem. |
| `cobalt_ore`, `ardite_ore` | Minérios do Nether (compostos sobre netherrack). |
| `grout.png` | Bloco de grout. |
| `molten_metal.png` (16×320), `molten_metal_flow.png` (32×512) | **T, M** — único par still/flow em tons de cinza compartilhado pelos 9 fluidos fundidos, tingido por fluido no código. Faixas animadas com `.mcmeta`. |
| `part_builder_top/side`, `tool_station_top`, `crafting_station_top/side`, `stencil_table_top`, `pattern_chest_front/side/top`, `part_chest_front/side/top` | Blocos das estações do M1. |

## GUI — `derived/gui/`

| Arquivo | Tamanho | Notas |
| --- | --- | --- |
| `tool_station.png` | 256×256 | **L** |
| `part_builder.png` | 176×166 | **L** |
| `stencil_table.png` | 176×166 | **L** |
| `smeltery.png` | 256×256 | **L** — painel em L; a grade de fusão fica no recorte transparente (geometria fixada por teste). |
| `generic.png` | 64×64 | **L** — tiles compartilhados de slot/moldura (incl. o tile de slot vazio que a grade de fusão reutiliza). |
| `info_panel.png` | 256×256 | **L** — painel lateral de informações. |
| `station_icons.png` | 256×256 | **L** — folha de ícones das abas de estação. |

## Itens — `derived/item/` (16×16)

| Arquivo(s) | Notas |
| --- | --- |
| `pickaxe_head`, `shovel_head`, `axe_head`, `tool_binding`, `tool_handle`, `shard` | **T** — sprites de partes em tons de cinza, tingidos por material. |
| `pattern`, `pattern_pickaxe_head`, `pattern_shovel_head`, `pattern_axe_head`, `pattern_tool_binding`, `pattern_tool_handle` | Moldes de papel (sem tingimento). |
| `cast.png`, `cast_ingot`, `cast_nugget` | Moldes de ouro; a cavidade usa alfa (**A**). |
| `cast_pickaxe_head`, `cast_shovel_head`, `cast_axe_head`, `cast_tool_binding`, `cast_tool_handle` | **G, A** — compostos por `generate_cast_textures.py` (base de ouro + cavidade da parte). |
| `cobalt_ingot/nugget`, `ardite_ingot/nugget`, `manyullyn_ingot/nugget` | Itens de metal (derivados do upstream). |
| `rose_gold_ingot/nugget` | Derivados por recoloração da arte de manyullyn. |
| `moss`, `mending_moss`, `reinforced_plate`, `silky_cloth`, `silky_jewel`, `extra_modifier` | Reagentes de modificadores. |
| `seared_brick.png` | Item de tijolo seared. |

## Ferramentas — `derived/tools/` (16×16)

| Arquivo(s) | Notas |
| --- | --- |
| `pickaxe_head/binding/handle`, `shovel_head/binding/handle`, `hatchet_head/binding/handle` | **T** — camadas por parte da ferramenta segurada, em tons de cinza, tingidas por material. |

## Arte original — `item/` (16×16)

| Arquivo(s) | Notas |
| --- | --- |
| `raw_cobalt.png`, `raw_ardite.png` | **G** — `recolor_raw_ore.py` a partir do ouro bruto / sucata de netherite da vanilla (especificação do mantenedor). |
| `raw_manyullyn.png`, `raw_rose_gold.png` | Arte original do Forgeweave. |

## Regras práticas

1. Mesmo nome + mesmas dimensões + mesma pasta → substituição direta, só recompilar.
2. Arquivos novos ou renomeações exigem mudanças de modelo/código — fale com um dev antes.
3. Mantenha os arquivos **T** em tons de cinza, as formas de alfa dos **A**, a geometria de layout dos **L**, e as faixas de quadros + `.mcmeta` dos **M**.
4. O `NOTICE.md` na raiz lista a origem upstream de cada arquivo derivado — ao substituir um por arte original, a linha correspondente do NOTICE sai na mesma mudança.
