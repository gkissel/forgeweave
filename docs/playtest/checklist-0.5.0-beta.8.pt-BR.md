# Forgeweave 0.5.0-beta.8 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.8.jar` (Release `mc1.21.1-v0.5.0-beta.8`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag responde à sua diretriz de 2026-09-05: o padrão de sprite volta a ser 16x16 e fica assim; o pacote Legacy só carrega arte nativa do Tinkers' (1.12 ou 1.20), então katana, scimitar e warmace usam a arte do Forgeweave nos dois conjuntos; e as paredes do smeltery passam a seguir o tier do core, com a troca se espalhando a partir dele em uns três segundos. Nada do Draconic mudou nesta tag. Defeitos viram issues `needs-triage`.

## A. Arte 16x16 (cliente)
1. [ ] Dagger, rapier, longsword, katana, scimitar e warmace montados na mão e no inventário aparecem no mesmo tamanho das outras ferramentas (16x16), sem borda cortada e sem parecer "grande demais" como os renders 32px do beta.7.
2. [ ] Quebrada, cada uma das seis mostra a camada quebrada desenhada à mão, não um chip automático.
3. [ ] O ícone de parte `curved_blade`, `war_mace_head` e `katana_blade` no Part Builder é a arte nova; pattern, cast de ouro e cast de argila dessas três partes mostram a silhueta nova.
4. [ ] O item Seared Brick (tijolo) mostra o sprite novo.

## B. Pacote Legacy só nativo do Tinkers' (cliente)
5. [ ] Com o pacote "Forgeweave Legacy Art" ligado, dagger, rapier e longsword voltam para a arte antiga (derivada do Tinkers'), inclusive a camada quebrada.
6. [ ] Com o pacote ligado, katana, scimitar e warmace continuam iguais ao padrão: nenhuma camada, ícone de parte ou camada quebrada muda.
7. [ ] Com o pacote ligado, os casts e patterns de katana, curved blade e war mace mostram o molde antigo (base Legacy) com a silhueta nova.

## C. Paredes seguem o tier do core (servidor dedicado)
8. [ ] Smeltery 3x3 de seared bricks com Standard Core: paredes e piso ficam como sempre.
9. [ ] Trocar o Standard Core por um Nether Core: as bricks vizinhas mudam para a textura do Nether Core quase na hora, e a troca avança até o canto mais longe em cerca de três segundos, com partículas do bloco novo em cada brick que vira. Piso e cantos entram na onda.
10. [ ] Derramar dragon breath no Nether Core (vira End Core) e depois deep blood (vira Deep Core): a cada transformação a onda roda de novo com a textura do tier novo.
11. [ ] Voltar para um Standard Core: a onda devolve tudo para seared bricks normais.
12. [ ] Colocar uma seared brick nova numa parede de um smeltery de tier alto já formado: ela vira sozinha em até um segundo.
13. [ ] Quebrar uma brick de tier alto dropa o item Seared Bricks normal; colocar ela fora de um smeltery dá a brick padrão.
14. [ ] Spark no servidor ocioso: smeltery formado e parado continua sem custo por tick além do heartbeat que já existia; nenhuma brick fica ticando depois que a onda termina.
15. [ ] Mundo da `mc1.21.1-v0.5.0-beta.7` com um smeltery formado carrega; as bricks aparecem no tier padrão e, no primeiro scan, viram para o tier do core.

## D. Sanidade
16. [ ] JEI: nenhuma categoria nova; seared bricks continuam com as mesmas receitas.
17. [ ] Sem JEI o jogo carrega.
