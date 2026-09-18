# Forgeweave 0.5.0-beta.16 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.16.jar` (Release `mc1.21.1-v0.5.0-beta.16`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. ⚠ = só dá para conferir em cliente; nenhum agente viu isso rodando.

Esta tag sai antes do fim do slice por causa do crash com o Replication (#1023). Junto vem o que o M8 já tinha mesclado: pattern do warmace (#989), modifiers de armadura na armadura leve (#1005), goggles do Create (#1007), tanque energizado (#972), `socketed` do Apotheosis (#969), Powah e `surgebound` (#996), formas de material (#992) e a Armor Station aposentada (#1006). Nada das integrações foi visto com o mod parceiro instalado. Defeitos viram issues `needs-triage`.

## A. Crash do Replication (cliente, com Replication)
1. [ ] ⚠ Abrir o jogo com Replication 1.21.1-1.2.7 instalado, entrar num mundo, sair e entrar de novo, e forçar um reload de recursos (F3+T). Nada de `Cannot get config value before config is loaded`.
2. [ ] Sem mundo carregado, no menu principal, JEI ou outro mod que liste receitas não derruba o jogo.
3. [ ] Dentro do mundo as opções continuam valendo: `matchVanillaSlimeblock`, `allowVanillaEnchanting`, `chestsKeepInventory`, `reuseStencils`.

## B. Pattern do warmace (#989)
4. [ ] A Stencil Table não oferece mais a pattern da cabeça do warmace.
5. [ ] Blank pattern + Heavy Core na crafting table (shapeless) dá a pattern. O Heavy Core é consumido.
6. [ ] Blank pattern sozinha não crafta nada. Com a pattern, o Part Builder faz a cabeça como antes.

## C. Armadura: leve, pesada e as estações (#1005, #1006)
7. [ ] `elytra_flight` e `creative_flight` entram no chestplate leve; continuam recusados em capacete, calça e bota.
8. [ ] Voo criativo funciona com o set completo de qualquer peso, inclusive peças leves e pesadas misturadas, e cai na hora que uma peça sai ou quebra.
9. [ ] ⚠ Tool Station: as quatro peças leves aparecem na barra lateral, com os dois slots fantasma (plating em cima, maille embaixo) e o preview tingido. Não há aba de peça pesada.
10. [ ] ⚠ Tool Forge: peças leves e pesadas na barra lateral; o terceiro slot do layout pesado (large plate) está legível.
11. [ ] Plating pesada na Tool Station mostra "This is too large to assemble here. Build it at a Tool Forge."
12. [ ] Mundo antigo com uma Armor Station colocada e itens dentro: o bloco carrega como Tool Station e os itens continuam lá. Armor Station guardada em baú ou inventário vira Tool Station.
13. [ ] ⚠ JEI: as categorias se chamam "Assembly" e "Assembly (Tool Forge only)" e as receitas de armadura apontam a estação certa. A página da Armor Station sumiu do livro; a cena de Ponder da armadura roda em volta de uma Tool Station.

## D. Tanque energizado (#972)
14. [ ] Craftar o tanque, colocar na parede de uma smeltery formada, encher a amostra de combustível com balde e carregar FE por cabo. A smeltery derrete na temperatura da amostra.
15. [ ] Buffer vazio: não derrete nada. Material que pede mais calor que a amostra fica sem derreter.
16. [ ] Dois tanques com amostras diferentes: só o mais quente gasta energia.
17. [ ] Overdrive: nesta build ainda é clique com a mão vazia no bloco, com aviso na action bar. Ligado, dobra o custo e a velocidade. A tela com botão vem na próxima tag (#1018).
18. [ ] Com `energizedTank = false` o bloco fica dormente, o mundo carrega, e amostra e buffer voltam intactos ao religar.
19. [ ] ⚠ Linha do tanque na categoria de combustível do JEI, tooltips, página do livro e as duas texturas do bloco.

## E. Modifiers de integração (com o mod instalado)
20. [ ] ⚠ Create: `create:goggles` num capacete do Forgeweave (leve ou pesado) não gasta slot, e os overlays do Create (stress, fluido) aparecem com o capacete vestido.
21. [ ] ⚠ Apotheosis: aplicar `socketed` com o Sigil of Socketing, encaixar uma gem e conferir o bônus no tooltip e no efeito. Tirar o Apotheosis do pack e abrir o mundo: a ferramenta não quebra.
22. [ ] ⚠ Powah: `surgebound` sobe um nível por vez (energized steel, blazing, niotic, spirited, nitro); pular um nível é recusado com mensagem. Cada nível dá +25% de capacidade de energia e +5% de velocidade; o nitro vale o dobro.
23. [ ] ⚠ Powah: os quatro cristais aparecem como materiais. Com `powahModifiers = false` as receitas do `surgebound` somem e o efeito fica inerte, sem apagar o modifier da ferramenta.

## F. Formas de material (#992)
24. [ ] Aba criativa: cada metal com lingote tem nugget, bloco, dust, small dust, tiny dust, plate, double plate, rod, gear e wire. Brimspar e fulmenite só têm os três dusts.
25. [ ] Crafts: 2 lingotes → 1 plate; 2x2 plates → double plate; 2 lingotes na vertical → 2 rods; 4 lingotes em cruz → 1 gear; 3 nuggets → 1 wire. Escada de dusts ida e volta.
26. [ ] Dust derrete na smeltery como o lingote, small dust como um terço, tiny dust como nugget. Dust de outro mod com a mesma tag `c:dusts/<id>` também derrete.
27. [ ] ⚠ Sprites 16x16 das formas novas: nenhum roxo de textura faltando, tint coerente com o material.

## Decisões pendentes
- Proporções de craft da família de placas (item 25): foram escolha do agente, de propósito mais caras que uma prensa. Confirmar ou trocar.
- Heavy Core consumido no craft da pattern do warmace (item 5), ou devolvido?
- Tag de fluido por metal (`c:molten_<id>`), sem uma tag-mãe `c:molten_metals`: o agente não achou convenção no ecossistema 1.21.1.
- Reagente do `socketed`: o Sigil of Socketing é o item certo?
- Peças pesadas entraram na tag `#forgeweave:large_tools`, que deixou de ser só de ferramentas.
- #971: conferir as 14 categorias de receita com EMI instalado (lista no PR #1009).
