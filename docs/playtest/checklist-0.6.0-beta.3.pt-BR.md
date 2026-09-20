# Forgeweave 0.6.0-beta.3 — checklist de playtest (pt-BR)

Build: `forgeweave-0.6.0-beta.3.jar` (Release `mc1.21.1-v0.6.0-beta.3`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. ⚠ = só dá para conferir em cliente.

Esta tag junta o que entrou depois da beta.2: a troca de parte em armadura (#1089), a série de correção de traits (#1091, #1092, #1093, #1097) e o preview das estações com a skin do jogador (#1099). Os checklists da beta.1 e da beta.2 continuam valendo, e o que ficou sem marcar lá segue pendente. Defeitos viram issues `needs-triage`.

## A. Troca de parte em armadura (#1089)
1. [ ] Na Tool Station, uma peça de armadura pronta mais uma plating de outro material troca a plating. O mesmo com a maille. Vale para as quatro peças leves.
2. [ ] Na Tool Forge, o mesmo para as peças pesadas, incluindo o large plate.
3. [ ] A peça trocada mantém modifiers, nível, XP, nome e o dano já sofrido. Se o dano não couber na durabilidade nova, ou se os modifiers não couberem nos slots do material novo, a troca é recusada e a peça volta intacta. Uma plating de outra peça (peitoral num capacete) é recusada com aviso.
4. [ ] Com `returnExchangedParts` ligado, a parte antiga volta para o jogador. Desligado, não volta.
5. [ ] Upgrades hospedados de outros mods que não cabem na parte nova voltam para o jogador, com a config ligada ou desligada.

## B. Traits que estavam ao contrário ou mortos (#1091, #1092)
6. [ ] `empowered_emeradic_bulwark` e `compressed_iron_heft` dão resistência a repulsão (0,3 e 0,1) e não fazem mais o portador tomar dano mínimo. O tooltip fala de repulsão.
7. [ ] `naga_ward`: apanhar várias vezes seguidas reduz o dano aos poucos (até 8% por peça) e o efeito some depois de uns 5 segundos sem apanhar.
8. [ ] `alpha_yeti_resilience`: a janela sem dano depois de um golpe é visivelmente maior que a do vanilla.
9. [ ] Seared stone: a picareta derrete o que minera (`searing`) e a armadura protege de fogo (`fire_protection`). Antes os dois não faziam nada e a estação mostrava a chave de tradução crua.
10. [ ] Necrotic bone: acertar um alvo cura 10% do dano causado (`necrotic`).
11. [ ] ⚠ Nenhum trait aparece na estação como `trait.forgeweave.algo.name`. Se aparecer, anotar o material.
12. [ ] ⚠ Textos corrigidos: `crystalline_ward`, `rubberize` e `prismward` falam de repulsão, não de redução de dano. `smolderveil` e `shattermail` não se comparam mais a outro trait. O tooltip do `deflect` do battlesign descreve o bloqueio de golpe corpo a corpo também.

## C. Todo material funciona dos dois lados (#1093, #1097)
13. [ ] Pegar cinco materiais quaisquer que fazem ferramenta e armadura. Em cada um, a ferramenta mostra pelo menos um trait que age na mão e a armadura pelo menos um que age vestida. Nenhum trait aparece num lado onde não faz nada.
14. [ ] Materiais pesados (osmium, lead, compressed iron, os dois emeradic, osmiridium, end steel, amethyst, pink slime): a resistência a repulsão vale vestida também. Uma peça dá 1/4 do valor da ferramenta e o set de 4 peças chega no valor cheio, sem passar.
15. [ ] Companheiros de família, um de cada para conferir o efeito vestido:
    - `swiftward` (rose gold, aluminium…): +6% de velocidade por peça.
    - `stormward` (nickel, fluix…): raio não causa dano.
    - `venomward` (deathworm chitin, nahuatl): metade das vezes envenena quem bate.
    - `bloodward` (soularium, soulium…): regeneração por 3 s ao apanhar.
    - `voidward` (dark matter, void crystal…): 10% de esquiva por peça.
    - `surgeward` (draconium, dragon bone…): janela sem dano de 26 ticks.
16. [ ] Mystical Agriculture (⚠ com o mod): a escada sobe nos dois lados. Inferium dá +0,5 de dano e 0,75 de proteção mágica por peça; insanium dá +3,5 e 3,75.
17. [ ] Extreme Reactors (⚠ com o mod): quem bate em alguém vestindo uraninite fica fraco (30%), cyanite lento (40%), blutonium e ludicrite com wither (50% e 60%).
18. [ ] `melee_protection` ficou só em steel, tungsten, knightmetal e certus quartz. Emerald, black quartz e shardline usam `warded`; dark steel usa `battleworn`; dragon bone e steeleaf usam `surgeward`.
19. [ ] Conhecido: `string`, `feather`, `leaf`, os três `slimeleaf_*` e `slimevine_purple` só fazem corda ou empenagem e seguem sem trait, como no 1.12.

## D. As sete decisões da auditoria (#1097)
20. [ ] ⚠ O trait do aluminium aparece como "Featherlight" e diz "3% mais velocidade de movimento".
21. [ ] Queen's slime: uma ferramenta com 1000 de durabilidade tem reserva de overslime de 150 (50 do `overslime` mais 100 do `overlord`). O tooltip do `overlord` fala da reserva.
22. [ ] ⚠ Auto-reparo: toda descrição diz a taxa em segundos. `sunmend` e `duskmend` têm a mesma (22 s por ponto); `smolderveil` é o mais rápido (20 s) e só de noite.
23. [ ] `projectile_protection` no ferro: o set de 4 peças dá 0,05 de resistência a repulsão, não 0,2. Uma peça sozinha dá 0,0125.
24. [ ] `dragonsteel_ice_calm`, `deorum_temper` e `arctic_insulation`: apanhando em sequência, a redução chega a 16%, 14% e 12%. Antes mal dava para notar.

## E. Preview com a skin do jogador (#1099)
25. [ ] ⚠ Com a config de cliente `stationPreviewModel = ARMOR_STAND` (padrão), Tool Station, Tool Forge e Modifier Worktable mostram o armor stand como antes.
26. [ ] ⚠ Com `PLAYER`, aparece o seu personagem com a sua skin e as camadas ativadas no menu de skin. Ele começa de frente para você, a cabeça gira junto com o corpo, a ferramenta fica na mão direita e a armadura vestida. Sem name tag.
27. [ ] ⚠ Arrastar o anel gira o boneco nos dois modos. Trocar a config vale na próxima vez que a estação abrir.
28. [ ] ⚠ Em servidor dedicado com outro jogador: cada um vê a própria skin, e a config de um não muda a tela do outro.

## Decisões pendentes
- `projectile_protection` (item 23): o total do set bate com o upstream, mas uma peça sozinha dá 0,0125 aqui e 0,05 lá. Deixar assim ou copiar o mecanismo de máximo entre peças.
- Os sete materiais de corda e empenagem sem trait (item 19): manter a paridade com o 1.12 ou criar traits para eles.
- O armor stand continua na pose de três quartos do upstream. Se quiser ele de frente como o boneco do jogador, é uma linha.
- Continuam abertas as da beta.2: o cabo, a amarração e o `lacerate.png` da katana gerados por script; `ToolLeveling.addXp` fora do pacote `api`; o teto de sincronização de materiais, agora em 166,3 KB de 170 KB.
