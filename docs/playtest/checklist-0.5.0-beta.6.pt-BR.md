# Forgeweave 0.5.0-beta.6 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.6.jar` (Release `mc1.21.1-v0.5.0-beta.6`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag responde ao playtest de 2026-09-04 com o Draconic Evolution instalado: fusion que gastava slot e não se explicava no JEI (#952), roster do Draconic com cores e traits certos (#953), materiais de mod atrás de combustíveis melhores (#954), tooltip com o estado das traits de contagem (#955), ferramentas evolved como host de módulos do DE (#956, fases 1 e 2) e o clear glass colorido (#951). A promessa de save-compat continua valendo. Defeitos viram issues `needs-triage`.

**Nada do Draconic rodou num cliente aqui**: o DE nunca entra num classpath de execução, só de compilação e de teste unitário. As seções D e E são as que este playtest existe para confirmar.

## A. Clear glass (#951)
1. [ ] ⚠ Os 16 clear stained glass têm cada um a sua cor no mundo, no inventário e no JEI; nenhum aparece cinza.
2. [ ] ⚠ Um painel 3x3 de clear glass (ou de uma cor) desenha como uma chapa só, com borda apenas no contorno de fora, igual ao 1.12. Um bloco isolado mostra a moldura completa.
3. [ ] Vidro colocado antes desta versão carrega com as bordas cheias até um vizinho mudar; colocar ou quebrar um bloco ao lado atualiza.

## B. Materiais de mod e temperatura (#954)
4. [ ] Lingote de mod de tier ferro derrete com lava; de tier diamante só com blazing blood ou mais quente; de tier netherite só com magma ou mais quente. O JEI mostra a temperatura em cada receita.
5. [ ] Um bloco de metal de mod pede a mesma temperatura que o lingote dele, não mais.
6. [ ] Draconium derrete com magma (1600); awakened draconium só com brimspar (1800); starweld e voidweld só com pyrealloy (2000).

## C. Tooltips de estado (#955)
7. [ ] Ferramenta de hollowsteel mostra sob `bloodtally` os kills, o bônus atual e o cap; sem kill nenhum a linha não aparece.
8. [ ] Ferramenta de warspar mostra sob `warmemory` até três tipos de mob com bônus e contagem, e a linha do cap.
9. [ ] Ferramenta com parte evolved mostra "Draconic upgrades: n de m" com m = 2, 4 ou 8 pelo tier mais alto entre as partes.

## D. Roster e fusion do Draconic (#952, #953, precisa do DE)
10. [ ] ⚠ Cinco materiais do DE no Part Builder: `draconium` (lingote, azul-roxo) e `draconium_awakened` (lingote, laranja) com casting; `wyvern`, `awakened` e `chaotic` (cores) só no Part Builder, sem fluido e sem melting.
11. [ ] ⚠ Cores carregam `evolved` I, II e III mais uma trait própria cada (stonewake, ruthless, chaosmark); os lingotes não carregam `evolved`.
12. [ ] ⚠ Fazer um upgrade de fusão numa ferramenta evolved: o modifier sobe e a contagem de slots livres na Tool Station **não muda**; depois disso ainda dá para aplicar um modifier normal no slot que sobrou. Repair e troca de parte mantêm isso.
13. [ ] ⚠ No JEI, a categoria de fusão do DE mostra o catalisador como ferramenta do metal do tier (emberweld, starweld, voidweld) e o resultado nomeado, por exemplo "Emberweld Pickaxe + Haste II".
14. [ ] ⚠ Ferramenta com cabeça de wyvern core passa no upgrade de tier 1 e é recusada no tier 2; de chaotic core passa em todos.

## E. Módulos do Draconic em ferramenta Forgeweave (#956, precisa do DE)
15. [ ] ⚠ **Fase 1, GUI**: segurando uma ferramenta evolved, a tecla de módulos do DE (padrão `Ctrl`) abre a tela de módulos dele. A grade é 2x1 no `evolved I`, 2x2 no II e 4x2 no III; uma ferramenta de ferro não abre nada ("no modular items"). Instalar um módulo de energia e conferir que a linha `Stored Energy` do tooltip cresce com a capacidade dele, e que continua sendo **uma** linha só. Peitoral evolved aceita os módulos de voo, escudo e undying do DE e eles funcionam vestidos. Picareta não aceita módulo de arco, arco não aceita módulo de mineração.
16. [ ] ⚠ **Fase 2, energia**: uma ferramenta evolved sem trait `energized` e sem módulo de energia tem buffer zero, e aí todo efeito com custo fica parado. Instalar o módulo de energia primeiro e conferir que os efeitos abaixo passam a valer, e que voltam a não valer com o buffer vazio. Em nenhum caso a ferramenta fica pior do que era sem módulo nenhum.
17. [ ] ⚠ **Velocidade de mineração**: com um módulo de velocidade a ferramenta quebra bloco visivelmente mais rápido, e cada bloco gasta `energyHarvest` do buffer. Com o buffer vazio ela volta à velocidade própria, não para de minerar.
18. [ ] ⚠ **Área de mineração**: módulo de área raio 1 numa picareta evolved quebra 3x3 usando o sweep das ferramentas grandes (drops, durabilidade e XP iguais aos de sempre). Raio 2 dá 5x5. Num martelo evolved soma ao 3x3 dele, e o martelo **não** fica mais lento.
19. [ ] ⚠ **Dano de ataque**: módulo de dano soma os pontos dele em cima do dano que a ferramenta já mostrava; a linha Attack Damage mostra o total. Sem energia o número volta ao da ferramenta sozinha.
20. [ ] ⚠ **Área no combate**: com módulo de área, o golpe carregado acerta os mobs em volta dentro do raio, à frente do jogador, sem acertar aliados nem o alvo duas vezes. O golpe mirado continua o normal; a varredura cobra energia por mob.
21. [ ] ⚠ **Arco**: módulo de projétil num arco evolved deixa a flecha mais rápida, mais certeira e mais forte, cobrando a energia por tiro do DE. Com o buffer curto o arco atira normal em vez de recusar o tiro. **Penetração e antigravidade não foram ligadas**: moram na entidade de flecha do DE.
22. [ ] ⚠ Módulos instalados sobrevivem a save/restart e a uma troca de parte da ferramenta.

## F. Sem o Draconic
23. [ ] Sem o DE instalado o jogo carrega normal: sem materiais do DE, sem Weldheart, sem receita de fusão, sem tecla de módulos, e as ferramentas evolved (se importadas de um mundo com DE) só perdem os efeitos.

## G. Save-compat e publish (obrigatório)
24. [ ] **Mundo da `mc1.21.1-v0.5.0-beta.5`** carrega: ferramentas niveladas, armaduras, cores em transformação, buffer de energia e ledger de dano intactos.
25. [ ] O mesmo mundo com uma ferramenta que tinha upgrade de fusão do beta.5: ela carrega, e o slot que o upgrade tinha gastado **não** volta sozinho (só upgrades feitos a partir desta versão preservam o slot). Anotar aqui se isso incomodar.
26. [ ] **Spark** no dedicado ocioso: host de módulos e tooltips de estado não adicionam custo por tick.
27. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## H. Decisões pendentes suas
- Grade de módulos por tier (2x1, 2x2, 4x2) contra a do próprio DE (4x4, 6x5, 8x6): um shield controller (2x2) não cabe no `evolved I`.
- Ferramentas evolved sem energia inata: o DE dá energia base ao próprio equipamento, o Forgeweave não. Decidir se `evolved` traz um buffer mínimo.
- Números do roster do DE (#953) e do mapa de temperaturas (#954) após o playtest.
- Upgrades feitos no beta.5 gastaram slot e não são corrigidos retroativamente.
