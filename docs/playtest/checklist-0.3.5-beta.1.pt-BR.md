# Forgeweave 0.3.5-beta.1 — checklist de playtest (pt-BR)

Build: `forgeweave-0.3.5-beta.1.jar` (Release `mc1.21.1-v0.3.5-beta.1`; Modrinth `lVdUPPLr`, CurseForge `1657493`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo. "!" = parcial. **A alpha.4 nunca foi playtestada** — este passe cobre a alpha.4 inteira (seção A, resumida do checklist anterior) + o que entrou na beta.1 (B–E). A partir desta tag a promessa de save-compat vale: a seção F é obrigatória. Defeitos viram issues e saem na beta.2 pelo pipeline normal.

## A. Herdado da alpha.4 (nunca verificado)
1. [✓] **Renomear na Tool Station**: nome completo persiste; dois jogadores veem o mesmo.
2. [✓] **Melee minera como espada**, inclusive com modificador (broadsword+redstone: lenta em tábua, rápida em teia).
3. [✓] **Partes/sharpening kit dropados** sobrevivem lava/fogo/explosão.
4. [✓] **Flecha no arco** só aparece puxando; tipped/spectral com cor certa; besta só carregada.
5. [!] **Besta**: flecha sai na direção certa (1ª/3ª pessoa); braços seguem o draw real; modelo centrado.
   - 5.a Flecha sai certa só em 3ª pessoa; modelo ainda com offset, espelhado → #693
6. [✗] **Magnetic** puxa em 3D, só na janela pós-uso.
   - 6.a Puxa só na horizontal e em "ticks", não fluxo contínuo → #694
7. [✓] **Balde** tira fluido do drain/tank; tank enche/esvazia por clique.
8. [✓] **Shard/troco** no Part Builder: input que vale mais devolve shards; troco bloqueia se o slot estiver ocupado.
   - 8.a Sem shift-click (bulk craft) em partes nem shards → #695
9. [✓] **Channels**: clique por face conecta, sneak inverte, hitbox generosa.
10. [✓] **Esmeralda** casta/esfria no mesmo tempo dos outros blocos.
11. [✓] **Large plate**: pattern e cast com rosto de creeper.
   - 11.a Rosto do creeper um pouco menor que o original → #696
12. [✓] **Enchantability por material** (`allowVanillaEnchanting=true`): ouro alto, pedra baixo — anote o que parecer errado.
13. [✓] **Seared glass** recicla stained glass no basin.
14. [!] **Shuriken**: 4 lâminas, atira, dano por material, munição = durabilidade.
   - 14.a Shuriken sem modelo in-game; durabilidade gasta de 10 em 10 (esmeralda dá totais não divisíveis por 10) → #697
15. [✓] **Ilhas de slime** (`/locate structure forgeweave:slime_island`) e **magma slime islands** no Nether; blue slime spawna; saplings crescem; vinhas azuis/roxas viram corda de arco.
16. [!] **Slime boots** (quica, sem dano de queda) + **Slimesling** (carrega, arremessa, combo).
   - 16.a Sling: muita força horizontal, pouca vertical — decisão: −15 % horizontal, +60 % vertical → #698
   - 16.b Botas: no criativo não quicam mas mantêm uncap/aceleração/"deslize" (stack com voo); tudo isso também ativa na água em ambos os modos → #698
17. [✓] **Família colorida de slime**: bolas/congealed/blocos por cor, loop de crafting; substitutos antigos revertidos (knightslime alloy, slimy mud, cristais).
18. [✓] **Partículas**: slash no golpe carregado; corações em hits secundários; strip de machado em área.
19. [✓] **Melting** de ferramenta quase quebrada funde os lingotes cheios (pinado, paridade 1.12).
20. [✗] **Off-hand**: cleaver/rapier/battlesign/arcos com pose correta.
   - Poses de off-hand erradas → #699

## B. Flechas de material e munição (#653, PR #662)
21. [!] **Flecha de material** monta na Tool Station (haste + ponta + empena); tooltip mostra dano/precisão/munição por material; munição = durabilidade, estaca no inventário.
   - 21.a Durabilidade em múltiplos de 10 → #697
   - 21.b Flechas sem render in-game → #697
22. [✓] **Prioridade de munição**: com flechas de material e vanilla no inventário, o arco dispara a de material primeiro.
23. [✓] **Traits de munição**: *breakable* (≈50% quebra ao bater em bloco), *hovering* e *endspeed* (voo diferente, visível), *freezing* (Slowness acumula até IV), *splitting* (uma vira duas no disparo).
24. [✓] **Enderference em ponta de endstone** funciona (escopo projectile sobrevive ao head-only alien).
25. [✓] **Fins** aplica só em projéteis; recusa em arco/picareta.
26. [ ] **Multishot**: N flechas por uma munição; as extras **não** podem ser pegas do chão; a consumida pode.
27. [✓] **Aba de flecha na Tool Station** não crasha (#669 era crash); tints de preview da haste/ponta/empena batem com o 1.12.
28. [✓] **Harness/poses de arco** (#673): só sanidade — arco puxado em 1ª/3ª pessoa não trava o braço.

## C. Slime drops e Slimesling colorido (#649, PR #657)
29. [ ] **Cinco slime drops** (azul JUMP III, roxo LUCK, blood HEALTH BOOST, magma FIRE RES, verde) — comer dá o efeito por 90 s; arte/nome por cor.
30. [ ] **Slimesling por cor** (green/blue/purple/blood/magma): receita com o congealed da cor; comportamento e arte por cor; o antigo sling genérico sumiu/convertido.

## D. Mundo e smeltery (#647, #369, #639)
31. [ ] **Vinhas na ilha de slime** pendem do exterior da ilha (não só dentro); crescem para baixo.
32. [ ] **Seared stairs/slabs** contam **só no teto** do smeltery; na parede/piso a estrutura não forma.
33. [ ] **Melting pós-1.12** (lista 1.20 inteira — sua decisão #639): chain, lantern, crossbow vanilla, chainmail, pistão, spyglass, sino, etc. fundem com os valores certos; **subproduto** de melting aparece quando o item tem mais de um metal.

## E. Livro e Ponder (#651, #664)
34. [ ] **Livro data-driven completo**: índice gerado, intros de estação, bullets de propriedade/efeito só do Forgeweave (nada de "tinker"), páginas de tool/modifier com o diagrama de modificação, folha esquerda com padding (texto não encosta na borda).
35. [ ] **Página de estrutura do smeltery** com o esquema 3D girando; camadas navegáveis; performance ok ao deixar aberta.
36. [ ] **Ponder** (jar-in-jar com Flywheel): tecla de Ponder sobre o Smeltery Controller abre a cena de montagem; sem crash **sem** Flywheel/Ponder externos instalados; se instalar Create/Ponder por fora, sem conflito de versão.
37. [ ] **ForgeweavePonderHint** (fallback) ainda aparece onde não há cena.

## F. Save-compat e publish (obrigatório — primeira tag com promessa)
38. [ ] **Mundo da alpha.4** (ou alpha.3) carrega na beta.1: ferramentas mantêm partes/modifiers/durabilidade; besta carregada; katana ramp; shocking charge; smeltery formado com fluidos; livro volta na página marcada.
39. [ ] **Mundo da beta.1 salvo → reaberto na beta.1** após restart do servidor dedicado (baseline para a beta.2).
40. [ ] **Jar do Modrinth e do CurseForge** são byte-idênticos ao do GitHub Release (`sha256sum`); dependências listadas nos stores (NeoForge range) batem com `neoforge.mods.toml`.
41. [ ] **Sem JEI** o jogo carrega; **com JEI** todas as categorias novas aparecem (flecha, melting pós-1.12, slime drops).
42. [ ] **Spark** no servidor dedicado: smeltery formado ocioso ≈ heartbeat 1/s, estações ociosas 0.

## H. Pontos extras do playtest (2026-08-24)
- Jade: mostrar % de "secagem" na casting table → decisão pendente (Jade é compat M8).
- Armaduras sem hover de status / sem modifiers / status inconsistentes → confirmar em qual build (beta.1 não tem armadura Forgeweave; alpha.1 do M4 tem).
- Ponder: blocos virados para o lado errado; variações de tamanho do smeltery; demo de channels/faucets → #700
- Veinminer: blacklist por tipo de ferramenta (machado só troncos) e keybind própria → decisão pendente.
- i-frames em mobs atingidos por armas do mod → #701; reduzir dano base/adicional → decisão pendente (magnitudes).
- Traits só em hit "full" (fora do cooldown) → #701

## G. Decisões pendentes para você responder
- **#580 encerrada wontfix** (nome RARE em ferramenta com encanto concedido) — confirmar que não incomoda em jogo.
- **Modrinth**: ainda Draft — precisa descrição + ícone e "Submit for review". **CurseForge**: fila de aprovação.
- Nada mais bloqueia; o que sair ✗/! aqui vira issue `needs-triage` → beta.2.
