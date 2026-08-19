# Forgeweave 0.3.5-alpha.4 — checklist de playtest (pt-BR)

Build: `forgeweave-0.3.5-alpha.4.jar` (Release `mc1.21.1-v0.3.5-alpha.4`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo. "!" = parcial. Só o que mudou desde a alpha.3 + os ✗/! do passe anterior.

## A. Reteste do que estava quebrado (alpha.3 ✗/!)
1. [ ] **Renomear na Tool Station**: digitar nome completo funciona (campo não reseta); dois jogadores veem o mesmo texto.
2. [ ] **Armas melee mineram como espada** — e agora TAMBÉM com modificador aplicado (a regressão era o rebake): broadsword com redstone continua lenta em tábua e rápida em teia. Obs: martelo/escavadeira/lumberaxe modificados agora mineram mais devagar que na alpha.3 (correto pelo 1.12).
3. [ ] **Partes dropadas** (e sharpening kit) sobrevivem lava/fogo/explosão.
4. [ ] **Flecha no arco**: só aparece puxando; tipped/spectral com a cor certa; besta só quando carregada. Obs: no 1.12 original a flecha aparecia sempre — esconder foi decisão sua.
5. [ ] **Flecha da besta** sai visualmente na direção certa (1ª e 3ª pessoa).
6. [ ] **Besta 3ª pessoa**: braços acompanham o TEMPO REAL de draw (não travam no fim); modelo centrado na mão.
7. [ ] **Magnetic**: puxa em 3D (item acima/abaixo vem), fluido, só na janela pós-uso.
8. [ ] **Balde tira fluido**: clique com balde vazio no drain/tank tira água/fluido; tank enche/esvazia por clique.
9. [ ] **Shard/troco**: agora alcançável — o PR lista a receita concreta (ex.: input que vale mais que o custo devolve shards); troco bloqueia a fabricação se o slot estiver ocupado.
10. [ ] **Channels**: interação nova (clique por face = conexão, sneak inverte ciclo, hitbox generosa) — está menos "duro"?
11. [ ] **Esmeralda**: bloco casta/esfria no MESMO tempo dos outros blocos.
12. [ ] **Large plate**: pattern E cast com o rosto de creeper.

## B. Decisões suas implementadas
13. [ ] **Enchantability por material** (`allowVanillaEnchanting=true`): mesa oferece encantos diferentes conforme o material (ouro alto, pedra baixo). Valores são chute calibrado — anote o que parecer errado.
14. [ ] **Seared glass recicla stained glass**: qualquer vidro colorido no basin + seared stone → seared glass.

## C. Conteúdo novo grande
15. [ ] **Livro estilo Mantle**: capa/spread/setas 1:1 do 1.12, escala com a janela; seções abrem em página de navegação; página de material com layout do upstream; **bookmark** — fechar e reabrir o livro volta na página.
16. [ ] **Shuriken**: monta com 4 lâminas, atira, dano por material, munição = durabilidade.
17. [ ] **Partes de flecha** (haste/empena/ponta com stats SHAFT/FLETCHING/PROJECTILE) no Part Builder e stencil.
18. [ ] **Ilhas de slime** no céu do overworld — `/locate structure forgeweave:slime_island` FUNCIONA; ilha tem lago de slime, árvores, vinhas; vinhas azuis/roxas viram corda de arco.
19. [ ] **Magma slime islands** no Nether — `/locate structure forgeweave:magma_slime_island`; sapling laranja, sem vinhas (paridade).
20. [ ] **Blue slime**: spawna nas ilhas, drop, comportamento.
21. [ ] **Slime boots**: quica, sem dano de queda. **Slimesling**: carrega e arremessa; combo com as botas.
22. [ ] **Família colorida de slime**: bolas/congealed/blocos por cor, loop de crafting; substitutos antigos revertidos (knightslime alloy, slimy mud, cristais).
23. [ ] **Saplings/árvores de slime** crescem; folhas/madeira coerentes.
24. [ ] **Partículas**: slash por arma no golpe carregado; corações nos hits secundários; strip de machado em área (com Width++).
25. [ ] **Melting**: picareta de ferro quase quebrada funde pelos 3 lingotes CHEIOS (1.12 não escala por durabilidade — pinado).
26. [ ] **Mão esquerda**: segurar cleaver/rapier/battlesign/arcos na off-hand — pose correta (não espelhada 2×).

## D. Decisões pendentes para você responder
- **#580**: modificador que concede encantamento (silky) recolore o nome da ferramenta para RARE. Opções: (a) mixin para forçar COMMON, (b) redesenhar como o encanto é concedido, (c) aceitar como desvio cosmético. Qual?
- **#639**: fundir itens pós-1.12 (chain, lantern, crossbow vanilla, chainmail, pistão, spyglass, sino...) — adotar a lista 1.20 inteira, parcial, ou não?
