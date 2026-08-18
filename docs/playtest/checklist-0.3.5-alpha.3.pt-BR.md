# Forgeweave 0.3.5-alpha.3 — checklist de playtest (pt-BR)

Build: `forgeweave-0.3.5-alpha.3.jar` (GitHub Release `mc1.21.1-v0.3.5-alpha.3`), Minecraft 1.21.1 + NeoForge. Teste em **servidor dedicado**, mundo novo, sem cheats onde disser "survival". JEI recomendado. Marque ✓ / ✗ / ? e anote o que viu; qualquer coisa estranha, mande print + passo a passo.

## A. Ferramentas e estações
1. [ ] **Nomes**: uma picareta de cobalto se chama "Cobalt Pickaxe"; multi-material vira "Cobalt-Iron Pickaxe"; partes idem ("Cobalt Pickaxe Head"). Renomear na Tool Station mantém o nome dado.
2. [ ] **Tool Station – reparo**: colocar item de reparo em QUALQUER slot livre (3º, 4º, 5º também) repara. Reparo aceita tora/bloco/nugget/shard do material (valor proporcional), não só o item base.
3. [ ] **Tool Station – renomear**: colocar só a ferramenta + digitar nome → renomeia sem outros inputs; dois jogadores na mesma station veem o mesmo texto no campo.
4. [ ] **Modificadores – slots**: redstone/lápis/etc. em qualquer slot livre; vários de uma vez.
5. [ ] **Metais só por cast**: Part Builder recusa lingote de ferro/cobalto/etc. em padrão (só madeira/pedra/flint/osso/…); metais saem do smeltery + molde. Config `craftCastableMaterials` volta a permitir.
6. [ ] **Nível de mineração**: pedra mina ferro? não. ferro mina diamante? não. diamante mina obsidiana; cobalto/ardite/manyullyn minam tudo (netherite tier). Tooltip mostra o tier certo.
7. [ ] **Part Builder**: troco (shard) não é destruído se o slot de troco estiver cheio com outro item — a fabricação é bloqueada.
8. [ ] **Drops**: jogar uma ferramenta/parte na lava/fogo/explosão → não some, não desespawna. Netherite não tem mais o trait "fireproof".
9. [ ] **Espadas/frigideira/placa/adaga/warmace** minam como espada (teia rápido, madeira lenta), não como machado.
10. [ ] **Ferramentas vanilla-tag**: pá achata grama em caminho e apaga fogueira; enxada/mattock ara; ferramentas contam como picareta/machado etc. em receitas modded.
11. [ ] **Baús**: Pattern Chest = um de cada padrão (stack 1), modo cast; Part Chest só empilha iguais; GUI escala com o conteúdo (barra de rolagem); clique direito no baú com item insere; **quebrar o baú mantém o inventário** (config `chestsKeepInventory`).
12. [ ] **Crafting Station**: baús ao lado aparecem no painel lateral, exceto as próprias mesas do workshop.
13. [ ] **Abas criativas**: General / Tools / Tool Parts / Smeltery.
14. [ ] **Sons**: serra na Tool Station ao montar, bigorna na Forge, "boing" na frigideira.
15. [ ] **Advancements vanilla**: montar picareta de pedra na station dá "Getting an Upgrade"; de ferro dá "Isn't It Iron Pick".

## B. Combate, traits e modificadores
16. [ ] **Hellish/Sharp na cimitarra aço+netherrack** (bug anterior): dano por clique cai quando spam-clica; bleed 0,33/tick sem knockback; golpe cheio ~13,5.
17. [ ] **Sharp**: bleed sem knockback a cada tick.
18. [ ] **Electrum (Shocking)**: item não "reseta" na mão ao andar; carga cheia → faísca/som/glint; golpe cheio → +dano, Speed VI, descarrega; **flecha** de arco electrum descarrega o arco na mão e não dá speed infinito.
19. [ ] **Incompatibilidades**: Silky + Luck recusado; Luck + Silk Touch; Squeaky + Silky/Luck; autosmelt + silk touch — mensagens no painel.
20. [ ] **Extra-info**: hover do modificador mostra "+x% attack speed" (armas) / "Bonus-Speed +x%" (arcos), Smite/Bane/Fiery/Necrotic/Reinforced/Shulking/Mending Moss têm linha explicando; nomes por nível ("Haster", "Unbreakable" no reinforced 5); cores por modificador.
21. [ ] **Autosmelt/Searing**: dá XP de fornalha, partículas de fogo, e não funciona com silk touch/squeaky.
22. [ ] **Magnetic**: puxa itens só ~1,5 s depois de usar a ferramenta, não o tempo todo.
23. [ ] **Bloqueio**: traits defensivos contam escudo levantado (qualquer mão) e battlesign; carregar espada longa não conta como bloqueio.
24. [ ] **Reagentes**: Smite = **solo consagrado** (dirt+carne podre+bonemeal → graveyard soil → fornalha), 24 por nível; Necrotic = **osso necrótico** (drop de wither skeleton); Luck aceita bloco de lápis (=9); Sharpness aceita bloco de quartzo (=4); Knockback aceita pistão grudento; glowstone/caveira **não** funcionam mais.
25. [ ] **Novos modificadores**: Width++/Height++ (expander) aumentam a área do martelo/escavadeira; **Blasting** (3 TNT) mina blocos não-efetivos com chance de destruir drop; **Glowing** (glowstone + olho de ender) coloca luz no escuro gastando 1 durabilidade.
26. [ ] **Reparo por parte**: martelo repara com a cabeça (fator 2,5), cleaver 2×, ferramentas de vários materiais reparam com qualquer parte de reparo; sharpening kit repara na station e na grade de crafting.
27. [ ] **Fortify** só em ferramentas de mineração; overlay na cor do material.
28. [ ] **Ferramentas específicas**: hatchet +0,5 dano e quebra folhas rápido de graça; martelo +3..6 vs mortos-vivos; foice tosquia várias ovelhas de uma vez; cleaver não passa clique direito para a mão secundária; hatchet/mattock/lumber axe/rapier têm knockback diferente; espada longa/frigideira andam mais devagar ao carregar; established dá XP extra ao quebrar bloco; cheap só em cabeça de pedra.
29. [ ] `allowVanillaEnchanting=true`: mesa de encantamento oferece encantos para ferramentas; livro na bigorna funciona; com false, nada.

## C. Arcos
30. [ ] Puxar mostra a **flecha** no arco; besta só quando carregada.
31. [ ] **Crosshair 1.12**: quadrado (arcos) / T (besta), abre com a carga.
32. [ ] Haste no arco muda o draw speed no painel/tooltip; besta em terceira pessoa tem pose de armar/carregada; andar na diagonal ao puxar = reto.

## D. Smeltery
33. [ ] **Fundir**: pedra/cobblestone/grout → seared stone fundido; ferramentas/armaduras/baldes/trilhos vanilla de ferro/ouro fundem; esmeralda (minério/gema/bloco); areia/vidro/painel → vidro fundido; gelo/neve → água; itens de ferro **modded** via tags `c:`.
34. [ ] **Casting**: seared brick/block/cobble/glass no basin/mesa; shard cast (ouro e argila, 72 mB); bloco de esmeralda no basin; painel de vidro e clear glass; **balde vazio na mesa enche** com o fluido; terracota/tijolo; red sand do sangue.
35. [ ] **Novos multiblocos**: **Seared Furnace** (controlador + estrutura + teto de escadas/lajes) cozinha em massa; **Seared Reservoir/Tinker Tank** (controlador + estrutura) guarda fluidos, drena pelo drain; **Seared Channels** conectam e roteiam fluidos (lado/baixo, redstone).
36. [ ] Hopper em cima do controller da smeltery insere itens.
37. [ ] Temperatura de minério: minério exige mais calor que lingote (derivado da quantidade dobrada).
38. [ ] Partículas: controller ativo solta fogo/fumaça; mesa de casting solta fumaça ao esfriar.
39. [ ] Grout em massa: bloco de argila + 4 areia + 4 cascalho = 8 grout; 3 cascalho = flint.
40. [ ] Blocos de cobalto/ardite/manyullyn/rose gold aceitos no Part Builder (valor 18) — só se `craftCastableMaterials` ligado.

## E. Livro-guia
41. [ ] Novo jogador recebe o livro ao entrar (config `spawnWithBook`).
42. [ ] Páginas longas **paginam** (não vazam da folha).
43. [ ] Textos revisados: descrição do arco curto, ordem das ferramentas.

## F. Mundo / misc
44. [ ] Veios de cobalto/ardite aparecem em duas faixas de altura no Nether.
45. [ ] Tags `c:` (glass, seared brick, casts, patterns, parts) — receitas de outros mods reconhecem.
46. [ ] `neoforge.mods.toml`: nome/autores/logo aparecem na lista de mods.

## Decisões pendentes que você pode responder aqui
- Flecha survival recolhível do chão (upstream) — manter?
- Besta perde a carga ao disparar sem flecha (upstream) — manter?
- Sons originais do Shocking são CC-BY 3.0 — portar ou ficar com vanilla?
- Enchantability fixa 14 (ferro) com `allowVanillaEnchanting` — OK ou por material?
- Cast de seared glass aceita só vidro incolor (T38) — ampliar para stained?
