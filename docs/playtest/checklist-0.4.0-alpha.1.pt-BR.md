# Forgeweave 0.4.0-alpha.1 — checklist de playtest (pt-BR)

Build: `forgeweave-0.4.0-alpha.1.jar` (Release `mc1.21.1-v0.4.0-alpha.1`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. Esta alpha fecha o **M4 (armors)**: #676–#682 mergeados. O playtest da beta.1 continua pendente e independente (`checklist-0.3.5-beta.1.pt-BR.md`). Defeitos viram issues `needs-triage` (regra de regressão vale). Tag alpha: pode quebrar save; a promessa de save-compat continua valendo para mundos da beta.1 (seção F).

## A. Teste de aceitação do SCOPE (D23) — na ordem
1. [ ] **Obsidian chestplate plating** no Part Builder (não-metal = parte do Part Builder); **vine maille** também. Part Builder **recusa** plating de ferro.
2. [ ] **Cast bootstrap**: ouro despejado sobre o plating de obsidiana vira o cast de plating (sem receita de crafting table).
3. [ ] **Iron plating ×4 + iron maille** fundindo ferro no smeltery e castando (custos 3/6/5/2, maille 2 lingotes).
4. [ ] **Tool Station** monta capacete, peitoral, calças, botas (plating + maille); **Tool Forge** também; sem gate de `large_tools`.
5. [ ] **Tooltip** mostra armor/toughness/knockback resistance/durabilidade dos valores 1.20 (peitoral de ferro: armor 5, durabilidade 240). Painel da estação idem. Linhas vanilla "+5 Armor" ainda aparecem abaixo (conhecido, #687) — anote se incomoda.
6. [ ] **Plating errado na linha errada** (plating de capacete no slot do peitoral) é recusado.
7. [ ] **Render em 3ª pessoa**: camada de plating tintada de ferro sobre a camada de maille; sem textura roxa; ícone no inventário tintado por material.
8. [ ] **Dano**: durabilidade cai no plating; dano reduzido pela armadura calculada.
9. [ ] **Peça em 0 de durabilidade** fica equipada, protege nada, marcada como quebrada.
10. [ ] **Reparo** na Tool Station com lingote de ferro (desconto 5%); maille não entra no reparo.
11. [ ] **Fire protection** no peitoral (seared brick) + **thorns** nas calças (cacto): lava reduz o dano de fogo; zumbi toma dano de thorns.
12. [ ] **Capacete de cobalto** → trait *melee protection* aparece e reduz dano corpo a corpo.
13. [ ] **Save/restart/reload**: as 4 peças mantêm partes, modifiers e durabilidade.
14. [ ] **JEI** mostra casting de plating/maille, montagem de armadura e receitas de modifiers sem código JEI novo; **sem JEI** o jogo carrega.
15. [ ] **Livro**: seção de armadura abre (intro, peças com diagrama de partes, traits, modifiers); **Ponder** sobre a Tool Station abre a cena de montagem de armadura; `ForgeweavePonderHint` continua onde não há cena.

## B. Traits ARMOR (#680) — um material por trait, sanidade rápida
16. [ ] ferro *projectile protection* · cobre *depth protection* (abaixo de y=0?) · obsidiana *blast protection* · manyullyn *warded* · amethyst bronze *crystalstrike* · prata *consecrated* (undead) · knightslime *overshield* + *overslime* (barra azul-clara; slime na estação recarrega, golpe e desgaste gastam) · osso *piercing guard* · cacto *thorns* · chorus *enderclearance* (teleporte ao ser atingido) · vinha azul *skyfall* (dano de queda).

## C. Modifiers de armadura (#681)
17. [ ] fire/blast/projectile/magic/melee protection, knockback resistance, thorns aplicam em armadura e **recusam em picareta**; harvest-only (ex. fortify de mineração) **recusa em peitoral**; contagem de slots igual à das ferramentas.
18. [ ] Reagentes: seared brick, crying obsidian, lingotes de ouro/cobalto/ferro — **cobalto/ferro colidem com reparo**: em peça danificada o reparo vence. Diga se o comportamento agrada.
19. [ ] Modifiers genéricos (reinforced, mending moss, soulbound, extra slot) aplicam em armadura.

## D. Materiais e render
20. [ ] Plating existe para os 18 (15 do clone + ardite/netherite/nahuatl); maille para 23 (+vinha, chorus, osso, cacto, vinha azul). Madeira/pedra/papel **não** têm plating.
21. [ ] **Nahuatl plating/maille inobtenível** (composite despeja obsidiana sobre madeira) — confirme e decida (#683 comentário, item 3).
22. [ ] Peça de material sem PNG gerado (material de datapack) renderiza textura faltando — conhecido (#688); decidir tint em runtime.
23. [ ] **Grade de abas da Tool Station em 6 colunas** (era 5) — visual ok?

## E. Harness (revisar PNGs em `build/screenshots/`)
24. [ ] `armor_iron.png`, `armor_cobalt.png`, `armor_obsidian_chestplate.png` + `_firstperson`, `book_armor.png`, `book_armor_chestplate.png` conforme `docs/releasing.md`.

## F. Save-compat e publish
25. [ ] **Mundo da beta.1** carrega na alpha.1 com um set vestido; tudo da beta.1 (ferramentas, smeltery, livro) intacto.
26. [ ] Mundo da alpha.1 salvo → reaberto após restart do dedicado.
27. [ ] **Spark** no dedicado ocioso: armadura vestida não adiciona custo por tick; estações 0; smeltery formado ≈ 1/s.
28. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## G. Decisões pendentes (ver comentário em #683)
- Valores interpolados ardite/netherite/nahuatl · sync budget 32 KB · nahuatl inobtenível · abas 6 colunas · tint em geração vs runtime · overslime só em armadura · reagentes/armorOnly · efeitos secundários do clone não portados.
