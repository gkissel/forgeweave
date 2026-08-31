# Forgeweave 0.5.0-alpha.1 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-alpha.1.jar` (Release `mc1.21.1-v0.5.0-alpha.1`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag fecha o **M6 (expansão de materiais)** — epic [#824](https://github.com/gkissel/forgeweave/issues/824), issue de fechamento [#848](https://github.com/gkissel/forgeweave/issues/848). M6 tem duas trilhas: **Track A** (materiais existence-gated de outros mods, ~40 presets) e **Track B** (escada própria, auto-contida, sem mod externo, 30 materiais). A promessa de save-compat continua valendo (linha 0.4.x em diante). Defeitos viram issues `needs-triage`.

**Nada aqui foi visto rodando por um humano** além do que os testes automatizados e o harness de screenshot cobrem: os agentes trabalham sem display e sem outros mods instalados. Este playtest é o que confirma a tese central do M6 — presença/ausência correta — e é por isso que a seção A precisa rodar **duas vezes**.

## A1. Passe 1 — Forgeweave sozinho (sem mods de compat)
1. [ ] Com **nenhum** mod de compat instalado, abrir criativo, JEI, o livro de guia e o Part Builder: **nenhum** material da Track A aparece em lugar nenhum — não é "presente mas incraftável", é ausente mesmo.
2. [ ] Todo material da **Track B** (30 materiais, escada própria) está presente e jogável nas quatro superfícies: criativo, JEI, livro, Part Builder.
3. [ ] Minerar os minérios da Track B, fundir na smeltery, ligar na mesa de liga (alloy table) e craftar uma ferramenta completa só com materiais Track B.

## A2. Passe 2 — com um provedor instalado (Mekanism, referência)
4. [ ] Com o Mekanism instalado, os materiais que ele fornece (osmium, obsidiana refinada, glowstone refinado, HDPE, fluorita, …) aparecem nas quatro superfícies.
5. [ ] Craftar uma parte no Part Builder a partir do **lingote do próprio Mekanism** (sem craft intermediário para um lingote "forgeweave").
6. [ ] Montar uma ferramenta com um material modded e observar o trait dela disparando em combate/mineração.
7. [ ] Se outro mod provedor estiver instalado além do Mekanism (ex.: AE2, Immersive Engineering), repetir os itens 4–5 para ele também.

## B. Comportamentos novos (biblioteca ADR-0004)
8. [ ] Montar uma ferramenta com um trait de **dano escalonado** (lib A, #827) e observar o efeito em combate.
9. [ ] Montar uma ferramenta com um trait de **efeito ao acertar** (lib B, #828) e observar o efeito disparando.
10. [ ] Montar uma ferramenta com um trait de **utilidade/economia** (lib C, #829) e observar o efeito.
11. [ ] Montar uma ferramenta com o **buffer de energia** (Forge Energy, #830): carregar de qualquer fonte FE e confirmar que a energia é gasta **antes** da durabilidade.

## C. Conteúdo de mundo (blood, End/Deep Core)
12. [ ] Matar um mob genérico do overworld sobre a smeltery e fundi-lo em **blood**.
13. [ ] Fundir um blaze e obter **blazing blood**; usá-la como combustível da smeltery e confirmar que queima **mais quente que lava**.
14. [ ] Fundir um dragon breath (se disponível em mundo/criativo).
15. [ ] Transformar um **Nether Core** em **End Core** e depois em **Deep Core**, observando o passo de rendimento (yield) em cada patamar — a transformação não deve completar nem estornar sozinha ao recarregar o chunk no meio do processo.

## D. UI/schema no roster final (128 materiais)
16. [ ] Aba criativa com o roster completo abre sem travar e sem stacks fantasmas.
17. [ ] Livro de guia: seção de materiais pagina corretamente (não é mais uma página só) com os 128 materiais.
18. [ ] Part Builder responde sem atraso perceptível ao trocar de material no slot com o roster completo.
19. [ ] Sync de material (login em servidor dedicado) é instantâneo, sem travar a entrada no mundo.

## E. Revisão visual (nenhum agente conseguiu ver isto rodando)
20. [ ] **Progressão de cor da Track B**: revisar as partes tintadas com os materiais da escada Track B **em sequência** (não um de cada vez) em `build/screenshots/` (`scripts/screenshots.sh`) — confirmar que a progressão de cor/tier faz sentido visualmente do primeiro ao último material.
21. [ ] Grid de seleção e preview da Tool/Armor Station com um material Track A e um Track B.

## F. JEI
22. [ ] **Com** o Mekanism instalado: receitas de craft de parte e montagem para materiais Track A aparecem no JEI.
23. [ ] **Sem** nenhum mod de compat instalado: o jogo carrega normalmente e o JEI não mostra nenhuma entrada fantasma de material Track A.
24. [ ] Receitas novas de melting/alloying/casting/embossing/trait-application (Track B e os dois núcleos novos) aparecem no JEI sem qualquer alteração de código do plugin.

## G. Sensação de jogo
25. [ ] Os ~30 novos comportamentos parametrizados (dano, on-hit, utilidade, energia) têm impacto perceptível e não parecem redundantes entre si.
26. [ ] O roster de 128 materiais não deixa a progressão confusa — o jogador ainda entende "o que é melhor que o quê" olhando o livro.

## H. Save-compat e publish (obrigatório — a promessa vale desde a 0.4.0-beta.4)
27. [ ] **Mundo da `mc1.21.1-v0.4.0-beta.5`** (release anterior) carrega: uma ferramenta construída com um material M6 (Track A ou B) no inventário mantém partes, modifiers e durabilidade.
28. [ ] Um **End Core** ou **Deep Core** formado sobrevive a um save/restart no meio de uma transformação, sem duplicar nem perder o progresso (`transform_progress`).
29. [ ] Uma ferramenta com o **buffer de energia** carregado sobrevive a save/restart com a carga intacta.
30. [ ] **Spark** no dedicado ocioso, com o roster completo carregado: aba criativa, seção de materiais do livro e Part Builder abertos uma vez cada — nenhum custo de tick adicional além do heartbeat já conhecido da smeltery formada.
31. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## I. Decisões pendentes suas
- **JC1 watch issues** (#857 Botania, #858 Blood Magic): nada a testar agora — presets dormentes só ativam quando esses mods publicarem build 1.21.1.
- **#831** (biblioteca de traits de armadura), **#832** (registro de traits via datapack), **#847** (mecânicas não-materiais do parity target) seguem `ready-for-human`, fora do escopo desta tag — o epic #824 continua aberto até você decidir os três.
- Nomes da Track B (JC9): confirme se as coinages ficaram reconhecíveis o suficiente sem se aproximar de nomes registrados (dois nomes da lista de referência são marcas registradas da Marvel — não devem ter sido usados; confirme na revisão visual acima).
