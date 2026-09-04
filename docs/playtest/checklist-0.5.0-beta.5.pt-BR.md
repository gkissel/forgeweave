# Forgeweave 0.5.0-beta.5 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.5.jar` (Release `mc1.21.1-v0.5.0-beta.5`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag fecha o **M7** (epic [#917](https://github.com/gkissel/forgeweave/issues/917)): ferramentas e armaduras ganham XP fazendo aquilo para que foram feitas, e cada nível dá exatamente um slot de modificador. Também entram as cinco correções do playtest de 2026-09-02 (#927 datapack de gametest vazando, #928 paleta, #929 cristal de fulmenite, #931 JEI de fusão de mobs e dragon breath a 1600, #932 temperatura efetiva em todo lugar) e o compat de fusion crafting do Draconic Evolution ([#915](https://github.com/gkissel/forgeweave/issues/915)). A promessa de save-compat continua valendo (linha 0.4.x em diante), sem exceções declaradas nesta tag. Defeitos viram issues `needs-triage`.

**Nada aqui foi visto rodando por um humano**: os agentes trabalham sem display e sem outros mods instalados. Os itens marcados com ⚠ são os que só um cliente real confirma.

## A. XP por fonte (M7-2, M7-3)
1. [ ] **Mineração**: quebrar blocos que a picareta é efetiva contra dá 1 XP cada; quebrar terra com picareta não dá nada. A linha `XP: n / 500` do tooltip sobe a cada bloco.
2. [ ] **Corpo a corpo**: matar um mob com a espada dá XP igual ao dano arredondado do golpe fatal. Bater sem matar não dá nada na hora.
3. [ ] **Ficha de dano**: bater num mob com a espada, guardar a espada na mochila, trocar de arma e matar o mob com outra coisa. As duas armas recebem o dano que cada uma causou, no momento da morte.
4. [ ] O mesmo com o mob salvo entre as pancadas: bater, sair do servidor, voltar, matar. O pagamento ainda acontece.
5. [ ] **À distância**: acertar um mob com flecha de arco longo dá `ceil(5 × drawTime / (20 × drawSpeed))`; o arco curto dá menos que o longo, a besta tem o número dela. Errar não dá nada.
6. [ ] **Utilidades**: arar com o mattock, colher com kama e com foice (uma por bloco colhido) e fazer caminho com a pá dão 1 XP cada.
7. [ ] **Bloqueio**: segurar o battlesign e aparar um golpe dá `max(1, arredondado do dano que vinha)`, não do que foi absorvido.

## B. Curva e slots (M7-1, M7-4)
8. [ ] Picareta nova: 500 blocos efetivos para o nível 1, mais 500 para o 2, 1000 para o 3, 2000 para o 4. Os dois primeiros níveis custam o mesmo de propósito (paridade com o upstream).
9. [ ] **Martelo**: o primeiro nível custa 4500 (ferramenta de área, base ×9). Escavadeira, lumberaxe, foice e vein hammer idem.
10. [ ] Cada nível dá **um** slot de modificador e nada mais: sem ganho de dano, de velocidade ou de durabilidade. O painel da Tool Station mostra o slot novo e ele aceita um modificador de verdade.
11. [ ] Slots de trait, de `extra_slot` e de nível somam juntos no mesmo total.

## C. Feedback do nível (M7-5)
12. [ ] ⚠ Ao subir de nível: mensagem no chat, som de chime e a linha `Nível` do tooltip mudando de nome. Conferir num servidor dedicado, não em single player.
13. [ ] ⚠ **A cor da linha de nível gira entre os níveis** (matiz `frac(0,277777 × nível)`). Este é o item que nenhum teste automático enxerga, olhar dois níveis seguidos lado a lado.
14. [ ] ⚠ Editar `tool_level` com `/data` numa ferramenta na mão: nível 12 mostra o nome do 0 com um `+`, nível 24 com dois `+`, e o 42 tem nome próprio (assim como 19, 66 e 99).

## D. Armadura (M7-6, design original)
15. [ ] Vestir um conjunto de ferro completo e apanhar: cada peça vestida e não quebrada ganha XP pelo que ela mitigou, a peitoral mais que a bota.
16. [ ] Peça **quebrada** não ganha nada; as outras três continuam ganhando. Peça só carregada na mochila não ganha nada.
17. [ ] Overslime de knightslime absorvendo dano paga a peça que gastou o overslime.
18. [ ] Uma peça que sobe de nível dá o mesmo chat, o mesmo chime e um slot próprio, gastável na Armor Station.

## E. Config (D-M7-3, D-M7-9)
19. [ ] `toolLeveling = false` no config do servidor: nenhum XP acumula em nenhum caminho, nenhuma linha de nível ou XP no tooltip, nenhum chime. **Os slots já ganhos continuam contando** e os modificadores gastos neles continuam funcionando.
20. [ ] `maximumLevels = 3`: a ferramenta para exatamente no 3 e a linha de XP some do tooltip. O padrão `-1` é sem limite.

## F. Livro guia (#924)
21. [ ] ⚠ A página de nivelamento abre no livro, com o texto da curva e do slot por nível. Nenhuma categoria nova no JEI por causa dela.

## G. Correções do playtest de 2026-09-02
22. [ ] **#927**: a categoria de combustível do JEI lista **5** combustíveis (lava, blazing blood, molten magma, molten brimspar, pyrealloy). Nenhum balde de água a 5000 e nenhuma entrada com nome `gametest_`.
23. [ ] ⚠ **#928 paleta**: alinhar os ingots da Track B na aba do criativo e conferir que nenhum par se confunde. Os cinco combustíveis agora são laranja, amarelo, vermelho escuro, verde e rosa, um por faixa de matiz. Fluido, ingot, nugget, bloco, minério e cristal de um material são a mesma cor.
24. [ ] **#929**: o minério de fulmenite dropa `fulmenite_crystal` (contagem escala com fortune, silk touch dropa o bloco). O cristal funde em molten fulmenite; `raw_fulmenite` não existe mais em lugar nenhum.
25. [ ] **#931**: a categoria `Fusão de criaturas` no JEI mostra as sete receitas mais a regra padrão (qualquer outro ser vivo vira blood), com a entidade renderizada, o fluido, os mB e o dano por golpe. O controller da smeltery é o catalisador.
26. [ ] **#931**: dragon breath só funde a partir de 1600, ou seja, lava (1300) e blazing blood (1500) não conseguem e molten magma (1700) é o primeiro que consegue.
27. [ ] **#932**: toda temperatura na tela é o número cru que as receitas usam. Lava lê 1300, blazing blood 1500, magma 1700, brimspar 1900, pyrealloy 2100, no JEI, na tela da smeltery e nos tooltips. A opção `temperatureCelsius` não muda mais nada.

## H. Compat Draconic Evolution (#915, #946, precisa do DE instalado)
28. [ ] ⚠ Com o **Draconic Evolution** instalado: craftar um **Weldheart** (4 lingotes de draconium nos cantos, 4 ender eyes nas bordas, cast de lingote no meio) e, no multiblock de fusão do DE, fazer um lingote de **emberweld** (tier wyvern, 4M RF), depois **starweld** (awakened, 16M RF) e **voidweld** (chaotic, 64M RF). Os três aparecem no JEI, no livro e no Part Builder só com o DE presente.
29. [ ] ⚠ Ferramenta de emberweld carrega `evolved I` e soul rend I (roubo de vida em acerto); starweld II, voidweld III. Uma ferramenta de ferro **não** entra como catalisador de upgrade de fusão; uma de emberweld passa no tier 1 e é recusada no tier 2.
30. [ ] ⚠ Fazer um upgrade de fusão numa ferramenta evolved (haste, sharpness, veinmine...) e confirmar que o modificador sobe para o nível do tier e **não gasta slot**. Uma ferramenta já no nível é recusada. Nenhuma receita de fusão promove core de smeltery (removidas no #946).
31. [ ] **Sem o DE instalado** o jogo carrega normal, os três metais e o Weldheart não existem, e nenhuma receita de fusão aparece no JEI. Este é o caso que os testes cobrem, os de cima não.
31a. [ ] ⚠ **#956, fase 1 — módulos do DE**: segurando uma ferramenta evolved, a tecla de módulos do DE (padrão `Ctrl`) abre a tela de módulos dele. A grade é 2x1 no `evolved I`, 2x2 no II e 4x2 no III; uma ferramenta de ferro ou uma evolved nenhuma não abre nada ("no modular items"). Instalar um módulo de energia e conferir que a linha `Stored Energy` do tooltip do Forgeweave cresce com a capacidade dele, e que continua sendo **uma** linha só. Peitoral evolved aceita os módulos de voo, escudo e undying do DE e eles funcionam vestidos. Picareta não aceita módulo de arco, arco não aceita módulo de mineração. Nada disto rodou num cliente aqui: o DE nunca entra num classpath de execução.
31b. [ ] ⚠ **#956, fase 2 — energia é o que liga tudo**: uma ferramenta evolved sem trait `energized` e sem módulo de energia tem buffer zero, e aí todo efeito com custo fica parado. Instalar o módulo de energia primeiro e conferir que os efeitos abaixo passam a valer, e que voltam a não valer com o buffer vazio. Em nenhum caso a ferramenta fica pior do que era sem módulo nenhum.
31c. [ ] ⚠ **#956, fase 2 — velocidade de mineração**: com um módulo de velocidade instalado a ferramenta quebra bloco visivelmente mais rápido do que a mesma ferramenta sem ele, e cada bloco quebrado gasta `energyHarvest` do buffer. Com o buffer vazio ela volta à velocidade própria dela, não para de minerar.
31d. [ ] ⚠ **#956, fase 2 — área de mineração**: um módulo de área raio 1 numa picareta evolved quebra 3x3 no lugar de um bloco só, usando o mesmo sweep das ferramentas grandes do Forgeweave (drops, durabilidade e XP de cada bloco iguais aos de sempre). Raio 2 dá 5x5. Num martelo evolved o mesmo módulo soma ao 3x3 que ele já tem, e o martelo **não** fica mais lento por causa disso.
31e. [ ] ⚠ **#956, fase 2 — dano de ataque**: um módulo de dano soma os pontos dele em cima do dano que a ferramenta já mostrava, e a linha Attack Damage do tooltip mostra o total. Sem energia para o golpe o número volta ao da ferramenta sozinha.
31f. [ ] ⚠ **#956, fase 2 — área no combate**: com módulo de área, bater num mob com o golpe carregado (barra cheia) também acerta os mobs em volta dentro do raio, à frente do jogador, sem acertar aliados nem o próprio alvo duas vezes. O golpe mirado continua o normal; a varredura é extra e cobra energia por mob atingido.
31g. [ ] ⚠ **#956, fase 2 — arco**: um módulo de projétil num arco evolved deixa a flecha mais rápida, mais certeira e mais forte, e cada tiro cobra a energia por tiro do DE. Com o buffer curto o arco atira normal, com os números dele mesmo, em vez de recusar o tiro como o arco do próprio DE faz. **Penetração e antigravidade do módulo não foram ligadas**: elas moram na entidade de flecha do DE, que substituiria a do Forgeweave. Nada da fase 2 rodou num cliente aqui, pelo mesmo motivo da linha 31a.

## I. Save-compat e publish (obrigatório)
32. [ ] **Mundo da `mc1.21.1-v0.5.0-beta.4`** carrega: ferramentas, armaduras (overslime), cores em transformação e buffer de energia intactos.
33. [ ] O mesmo mundo com uma ferramenta **pré-M7**: ela continua no nível 0, sem linha de nível e sem linha de XP, exatamente como antes. Uma ferramenta nivelada nesta tag mantém nível, XP e slot ganho depois de salvar, sair e voltar.
34. [ ] Carregar um mundo com ferramentas niveladas usando `toolLeveling = false`: nada quebra, nenhum slot é perdido, os modificadores gastos nos slots ganhos continuam ativos.
35. [ ] **Spark** no dedicado ocioso: o nivelamento não adiciona custo por tick além do heartbeat da smeltery já conhecido. A ficha de dano só existe em mobs que apanharam de ferramenta Forgeweave.
36. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## J. Decisões pendentes suas
- **Magnitudes do XP de armadura** (#923): `ARMOR_BASE_XP = 1` para o conjunto leve e o pesado, ou seja, o primeiro nível de uma peça custa os mesmos 500 de uma ferramenta de alvo único. Uma peitoral que absorve 4 de dano por golpe chega ao nível 1 em cerca de 125 golpes tomados; uma bota no piso de 1 leva os 500. Confirmar ou ajustar depois de apanhar bastante.
- **Números de explosão de fulmenite e brimspar** (10/50/2.0 e 25/50): seguem pendentes desde a beta.4.
- **Magma a 1000 mB por bloco** (#907): idem.
- **Upgrade de fusão não gasta slot** (#915): é a única exceção à economia de slots do mod. Confirmar que o caminho endgame pode ser mais barato em slots que a Tool Station.
- **Dois pares de cor travados por paridade** (#928): `gold`/`electrum` (dEok 0,026) e `glass`/`silver` (0,027) vieram 1:1 do clone 1.12 e estão citados por hex no NOTICE.md. Mexer neles é desvio de paridade, então a auditoria só os lista. Dizer se movem.
- Watch issues #857 (Botania) e #858 (Blood Magic): sem build 1.21.1 publicado ainda.
