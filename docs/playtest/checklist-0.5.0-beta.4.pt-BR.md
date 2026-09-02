# Forgeweave 0.5.0-beta.4 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.4.jar` (Release `mc1.21.1-v0.5.0-beta.4`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag fecha o lote de 2026-09-02 do **M6** (epic [#824](https://github.com/gkissel/forgeweave/issues/824)): escada de combustível no formato TAIGA (#897, #903, #910), minérios da Track B nas dimensões do TAIGA (#909), biblioteca de traits de armadura (#831), traits por datapack + KubeJS (#832), captura de mob do murkiron (#886), overlay Jade/WTHIT (#720), três cenas Ponder (#891), slimesling 1:1 (#902), multiplicador de fusão (#847) e a remoção dos raw de liga (#911). A promessa de save-compat continua valendo (linha 0.4.x em diante), com **uma exceção declarada** na seção J. Defeitos viram issues `needs-triage`.

**Nada aqui foi visto rodando por um humano**: os agentes trabalham sem display e sem outros mods instalados. Os itens marcados com ⚠ são os que só um cliente real confirma.

## A. Escada de combustível (lava 1300 → blazing blood 1500 → magma 1700 → brimspar 1900 → pyrealloy 2100)
1. [ ] Fundir um **magma block** na smeltery: sai 1000 mB de **molten magma**; usá-lo como combustível e confirmar que funde mais rápido que blazing blood.
2. [ ] Encontrar **brimspar** no Nether (netherrack, 3 veios/chunk): minerar vários blocos e ver a explosão de colheita disparar às vezes (25%) — o bloco que explode **não dropa**; explodir um veio com TNT e ver a cadeia (50%).
3. [ ] Fundir um **cristal de brimspar**: 144 mB de `molten_brimspar`; queimar a 1900° e confirmar que alcança receitas que magma não alcança.
4. [ ] Ligar **magma + flarealloy** (192 + 32 mB) em 144 mB de **pyrealloy**; confirmar que pyrealloy drena **100 mB por 500 ticks** (queima longa) em vez de 50/100.
5. [ ] **Twinalloy não existe mais**: nenhum balde, fluido, receita de ametista ou entrada no JEI. `quakestone` (alt) e `glowveil` agora consomem `molten_brimspar`.
6. [ ] **Fulmenite** também explode (10% colheita, 50% cadeia, blast 2.0). ⚠ Avaliar se a taxa de colheita atrapalha a mineração normal.
7. [ ] `meltSpeedMultiplier` no config do servidor: 2.0 dobra a velocidade de fusão de qualquer combustível; 1.0 é idêntico ao anterior.

## B. Dimensões dos minérios da Track B
8. [ ] **Nether** (netherrack): fulmenite, murkiron, warspar, brimspar. **End** (end stone): duskspar, nightshale, hollowstone, voidglass (outer islands). **Overworld** (deepslate): hardcinder, voltcinder (y −64..−48), resonite, starfall_stone (stone, y 62–90). Nenhum minério aparece na dimensão errada.
9. [ ] ⚠ Texturas dos oito minérios movidos: base netherrack/end stone correta, sem retângulo roxo, tint do material reconhecível na sequência.
10. [ ] Quantidade por chunk aceitável: nem raro demais (brimspar, hollowstone) nem entulhando (fulmenite 6 veios).

## C. Traits de armadura (#831, 14 instâncias)
11. [ ] Montar peças com pelo menos quatro destes e observar o efeito: `bloodtoll` (piso de dano), `hexward` (efeito no atacante), `windstep` (evasão 10%), `swiftstride` (+velocidade), `bracingplate` (resistência acumulada), `lastbreath` (salva da morte, 100 dur + 5 min cooldown), `stormrind` (imune a raio), `blastvent` (explosão vira knockback).
12. [ ] `nightveil`: mobs demoram mais para notar o jogador no escuro. `emberdrink`: dano de fogo cura.
13. [ ] `stonebound` continua igual (agora é instância de `stat_scales_with_wear`).
14. [ ] **Magnitudes propostas** na thread de #831 — validar em combate e decidir.

## D. Traits por datapack e KubeJS (#832)
15. [ ] Datapack com `data/<pack>/forgeweave/trait_definition/<id>.json` (`behavior` + parâmetros) + lang keys: o trait aparece numa ferramenta feita com material que o referencia e dispara.
16. [ ] `behavior` desconhecido → erro visível no log, trait não registra silenciosamente. Definição com `neoforge:conditions` falsa não registra.
17. [ ] Com **KubeJS** instalado: script de startup `ForgeweaveEvents.traits(e => e.register('pack:id').onAfterHit(...))` funciona; sem KubeJS o jogo carrega normal.

## E. Murkiron: captura de mob (#886)
18. [ ] Ferramenta com handle de murkiron: **sneak + hit** num mob com ≤15% de vida captura numa **Dusk Cage**; sem sneak não captura; acima de 15% não captura; ender dragon/wither/capitão de raid nunca.
19. [ ] Botão direito com a cage num bloco solta o mob **com a vida da captura**; a cage é consumida.
20. [ ] Cage com mob sobrevive a save/restart e a cópia no criativo não gera UUID duplicado.

## F. Overlay Jade / WTHIT (#720)
21. [ ] ⚠ Com **Jade**: mirar numa casting table/basin durante o resfriamento mostra a porcentagem subindo; no controller da smeltery, **shift** lista os fluidos com mB.
22. [ ] ⚠ Com **WTHIT** no lugar do Jade: mesmas duas informações. Sem nenhum dos dois o jogo carrega.

## G. Ponder (#891)
23. [ ] ⚠ Cenas **Seared Furnace**, **Seared Reservoir** e **Smeltery Cores**: pours animados de dragon breath (Nether → End Core) e deep blood (End → Deep Core), nenhuma placa vazia, blocos direcionais de frente.

## H. Slimesling (#902)
24. [ ] Lançamento horizontal de carga cheia **não** é cortado em ~3.9 blocos/tick (o cliente mantém a velocidade própria). Vertical de carga cheia para cima ≈ 2.0 (1.12 exato; a tunagem do #698 foi revertida).

## I. JEI
25. [ ] Categoria de combustível lista **5** combustíveis (lava, blazing blood, magma, brimspar, pyrealloy) com temperaturas; pyrealloy mostra 100 mB/500 ticks.
26. [ ] Receitas de brimspar (melting, alloy) e de magma aparecem sem alteração do plugin; nenhuma entrada de twinalloy, `raw_manyullyn` ou `raw_rose_gold`.

## J. Save-compat e publish (obrigatório)
27. [ ] **Mundo da `mc1.21.1-v0.5.0-beta.3`** carrega: ferramentas, armaduras (overslime), End/Deep Core em transformação e buffer de energia intactos.
28. [ ] **Exceção declarada** (#910): um tanque/balde com `molten_twinalloy` do mundo anterior **perde o fluido** (vira vazio), sem crash. `raw_manyullyn`/`raw_rose_gold` em inventário viram ar. Registrar aqui se isso incomodou o bastante para pedir o alias de migração.
29. [ ] Ferramenta com `bracingplate`/`lastbreath` em cooldown sobrevive a save/restart (`resistance_stacks`, `death_save_cooldown`).
30. [ ] **Spark** no dedicado ocioso: os 14 traits de armadura vestidos e um brimspar ore carregado não adicionam custo de tick além do heartbeat da smeltery.
31. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## K. Decisões pendentes suas
- **Magnitudes de #831** (thread da issue) e **números de fulmenite** (10/50/2.0) e **magma 1000 mB/bloco** (#907): confirmar ou ajustar após o playtest.
- **Voidglass** ficou no End (#883) apesar de o TAIGA ter o obsidiorite no Overworld — única divergência deliberada do #909.
- **Sceptres** (M-L) e **Artifacts** (M) seguem no backlog do SCOPE.md; **fusion crafting** virou compat do Draconic Evolution em [#915](https://github.com/gkissel/forgeweave/issues/915) (M8).
- Watch issues #857 (Botania) e #858 (Blood Magic): sem build 1.21.1 publicado ainda.
