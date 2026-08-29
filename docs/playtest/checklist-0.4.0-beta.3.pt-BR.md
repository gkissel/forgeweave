# Forgeweave 0.4.0-beta.3 — checklist de playtest (pt-BR)

Build: `forgeweave-0.4.0-beta.3.jar` (Release `mc1.21.1-v0.4.0-beta.3`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag fecha o **M4 (armors)** e traz a rodada de correções do playtest da beta.1 (#693–#790) mais armadura pesada, voos, Armor Station, JEI reformulado e o novo modifier de veinmine. **A promessa de save-compat vale a partir daqui** (linha 0.4.x): a seção G é obrigatória. Defeitos viram issues `needs-triage`.

**Nada aqui foi visto rodando por um humano** além das telas que o harness captura: os agentes trabalham sem display. As seções E e F são as que mais precisam do seu olho.

## A. Armadura — teste de aceitação do M4
1. [ ] **Obsidian chestplate plating** no Part Builder e **vine maille**; o Part Builder recusa plating de ferro.
2. [ ] **Cast bootstrap**: ouro sobre o plating de obsidiana vira o cast (sem receita de bancada).
3. [ ] **Iron plating ×4 + maille** fundindo ferro e castando (custos 3/6/5/2, maille 2).
4. [ ] **Armor Station** (bloco novo) monta capacete, peitoral, calças e botas. A **Tool Station e o Tool Forge recusam** armadura; a Armor Station recusa ferramenta.
5. [ ] **Tooltip e painel**: armor/toughness/knockback/durabilidade com os valores 1.20 (peitoral de ferro: armor 5, durabilidade 240).
6. [ ] **Plating errado na linha errada** é recusado.
7. [ ] **Render 3ª pessoa**: plating tintado sobre a maille, sem textura roxa; material de datapack sem PNG também tinge (tint em runtime, #726).
8. [ ] **Dano** desgasta o plating e é reduzido; peça em 0 fica equipada e não protege; **reparo** com lingote de ferro (5%).
9. [ ] **Fire protection** no peitoral + **thorns** nas calças: lava reduzida, zumbi levando thorns.
10. [ ] **Cobalto** dá *melee protection*; **knightslime** dá **overslime** (barra azul-clara, recarrega com bolas de slime — 20 verde / 50 azul).
11. [ ] **Nahuatl agora é obtenível**: obsidiana sobre tábuas vira **nahuatl board**, que serve de material no Part Builder.
12. [ ] Save/restart/reload: as quatro peças mantêm partes, modifiers e durabilidade.

## B. Armadura pesada e voos
13. [ ] **Heavy** capacete/peitoral/calças/botas montam com plating + maille + **large plate**; armadura ×1,4 (ferro: 165/2.8, 240/7.0, 225/5.6, 195/2.8) e **−5% de velocidade por peça** (~81,5% com o set).
14. [ ] **Elytra flight** (elytra) na peitoral heavy plana como elytra; **creative flight** (**cristal do End + nether star**, exige elytra flight antes) só com o set heavy completo, e some ao tirar/quebrar uma peça.
15. [ ] **Netherite** (template + lingote) aplica **mesmo sem slot livre**; um lingote sozinho continua dando `extra_slot`.

## C. Correções do playtest da beta.1
16. [ ] **Besta**: flecha na direção certa em 1ª e 3ª pessoa; modelo na pose do 1.12 (config `heldBowPose` alterna para a pose 1.21.1 se preferir).
17. [ ] **Magnetic** puxa nos três eixos e o fluxo é contínuo, sem saltos.
18. [ ] **Shift-click** faz bulk craft no Part Builder **e** na Crafting Station; o resultado vai primeiro para o inventário.
19. [ ] **Shuriken e flechas de material aparecem** ao voar; durabilidade continua 10 por uso e o tooltip mostra **Ammo: x/y**.
20. [ ] **Sling**: menos força horizontal, mais vertical. **Botas de slime** não fazem nada voando no criativo nem dentro d'água; quicam no survival.
21. [ ] **Off-hand**: cleaver, rapier, battlesign e arcos com pose espelhada correta; arco parado visível em 1ª pessoa.
22. [ ] **Tool Station** com a parte de baixo em madeira (não crafting table).
23. [ ] **Pattern chest** atualiza os slots na hora quando algo entra pela Stencil Table, com o menu aberto.
24. [ ] **Core do smeltery** perde o vermelho ao fechar a estrutura **longe** do controlador, sem clicar. Idem seared furnace e reservoir.
25. [ ] **i-frames**: mob atingido não toma dano a cada clique; traits secundários (rapier, fiery, shocking, bleed) não furam a invulnerabilidade.
26. [ ] **Slimes azuis dropam bola azul**; efeitos das bolas coloridas conforme o 1.12.
27. [ ] **Veinmine**: só o vein hammer minera veio com a tecla (padrão: crase). O **modifier veinmine** (prismarine shard, até nível 5 = 20 blocos) faz picareta/machado/pá minerarem em veio **só** com a tecla e só na whitelist — machado não pega tábua nem escada.
28. [ ] **Esmeralda + diamante**: picareta de cabeça de pedra com os dois quebra obsidiana **em qualquer ordem de aplicação**.

## D. Livro, Ponder e JEI
29. [ ] **Livro**: descrição e imagem da ferramenta na **mesma página**; página de modifier ilustra a ferramenta/armadura certa (não picareta sempre); seção de armadura abre.
30. [ ] **Ponder**: cena do smeltery mostra dreno e vidro de verdade (não tudo seared bricks); cenas novas de tamanhos e de channels/faucets; blocos virados para a câmera.
31. [ ] **JEI**: categorias com a arte do 1.20 (alloy, casting, melting, station); receitas **E** (netherite, glowing, creative flight) mostram **um slot por reagente**; sem JEI o jogo carrega.
32. [ ] **Hover**: Mending Moss, casts, bolas/drops de slime, tábua de nahuatl e demais reagentes têm descrição.

## D2. Arte Forged e Legacy (beta.2)
32.a [ ] **Padrão é a arte Forged**: pattern e cast em branco, tool binding, tough binding, lâmina de katana e topo da Armor Station com os sprites novos, incluindo os padrões e casts compostos a partir deles.
32.b [ ] **Pacote Legacy** aparece em Opções > Pacotes de Recursos, liga sem erro e devolve o visual antigo; desligar volta ao Forged.
32.c [ ] **Itens nas estações ficam sobre a mesa**, não no meio do bloco (Crafting Station, Stencil Table, Part Builder, Tool Station, Tool Forge, Armor Station).
32.d [ ] **Livro e JEI** ilustram cada modifier com algo que ele aceita: expansores de largura/altura numa ferramenta de mineração, wind burst na warmace, voos na peitoral heavy.

## D3. Arte e JEI (beta.3)
32.e [ ] **Marcas dos casts centralizadas** no molde, em todas as peças (era o defeito da beta.2).
32.f [ ] **Katana montada** usa a lâmina, o binding e o cabo novos; a versão quebrada é a desenhada à mão.
32.g [ ] **Sprites do segundo lote** aparecem: ponta e haste de flecha, cabeça de machado, limbo e corda de arco, cabo de ferramenta, topo da Armor Station. Conferir no item **e** na ferramenta montada.
32.h [ ] **JEI igual ao 1.20**: cada categoria com o painel do upstream, sem área cinza sobrando, sem slot fantasma, sem seta duplicada; fluidos com textura, não cor chapada. Ver alloying, casting (mesa e bacia), melting, modifiers, embossing, part crafting, reparo e as três de montagem.
32.i [ ] **Bindings por ferramenta ainda são os antigos** (pickaxe, shovel, hatchet, kama, warmace, battleaxe, excavator, lumberaxe, vein hammer) — esperado, falta arte do designer.

## E. Revisão visual (nenhum agente conseguiu ver isto rodando)
33. [ ] Telas do JEI, cenas do Ponder, grid de seleção e preview da Tool/Armor Station, armadura vestida (ferro, cobalto, obsidiana) em 1ª e 3ª pessoa. Compare com `build/screenshots/` rodando `scripts/screenshots.sh`.
34. [ ] Grade de abas: hoje 6 colunas. Com a armadura fora da Tool Station cabe em 5 — diga se prefere.

## F. Sensação de jogo
35. [ ] Dano das armas do mod **depois** dos i-frames: ainda parece alto? (a decisão de baixar magnitudes ficou pendente esperando este teste)
36. [ ] Proteções agora aplicam em arma **melee** empunhada, não só em armadura. Faz sentido em jogo?
37. [ ] Balanço dos voos: elytra e nether star + cristal do End são caros o bastante?

## G. Save-compat e publish (obrigatório — a promessa vale a partir desta tag)
38. [ ] **Mundo da 0.3.5-beta.1** carrega: ferramentas mantêm partes/modifiers/durabilidade, smeltery formado, livro na página marcada.
39. [ ] **Mundo da 0.4.0-alpha.1** (com armadura montada) carrega e as peças continuam válidas mesmo com a Armor Station nova.
40. [ ] Mundo da beta.1 salvo → reaberto após restart do dedicado.
41. [ ] **Spark** no dedicado ocioso: armadura vestida não custa tick; estações 0; smeltery formado ≈ 1/s.
42. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## H. Decisões pendentes suas
- **#758**: a barra de patterns do Part Builder só aparece com Pattern Chest **+ Crafting Station + Stencil Table** no grupo (regra do 1.12). Confirmar se foi isso que você viu.
- **beheading**: upstream é pérola + obsidiana; aqui junta 10 obsidianas num craft. Converter exige aplicar combo em etapas.
- **Tooltips de piada** do 1.12 (Width++/Height++) foram trocados por descrições reais.
- **Modrinth/CurseForge**: a beta.1 anterior ficou em Draft esperando descrição, ícone e "Submit for review".
