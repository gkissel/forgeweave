# Forgeweave 0.5.0-beta.11 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.11.jar` (Release `mc1.21.1-v0.5.0-beta.11`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag traz o terceiro lote de sprites do designer (battleaxe, cleaver, longsword, lumberaxe e scythe) e o Nether Core "v2": um segundo visual que o core veste enquanto o combustível do smeltery está acima de 1600 graus. A textura v2 ainda é um placeholder gerado (laranja mais quente); quando o sprite do designer chegar, entra no mesmo caminho. Os itens da beta.10 continuam valendo. Defeitos viram issues `needs-triage`.

## A. Sprites novos (cliente)
1. [ ] Battleaxe (as duas cabeças), lumberaxe (cabo, as duas cabeças), scythe (fecho, os dois cabos, cabeça) e o fecho do longsword aparecem com a arte nova montados na mão e no inventário, em 16x16.
2. [ ] Cleaver, lumberaxe, scythe e longsword quebrados mostram a camada quebrada desenhada à mão.
3. [ ] No Part Builder, os ícones de `hand_guard`, `broad_axe_head` e `scythe_head` são o mesmo desenho das camadas novas; pattern, cast e clay cast dessas três partes têm a silhueta nova.
4. [ ] Com o pacote Legacy ligado, tudo do item 1 a 3 volta para a arte derivada do Tinkers' 1.12.

## B. Nether Core v2 (servidor dedicado)
5. [ ] Smeltery com Nether Core formado e lava no tanque: o core fica no visual normal.
6. [ ] Trocar a lava por molten magma (1700): em até um segundo o core muda para o visual v2 (lado, topo e frente), mesmo sem nada derretendo. Brimspar e pyrealloy fazem o mesmo; blazing blood (1500) não.
7. [ ] Esvaziar o tanque quente: o core volta ao visual normal em até um segundo.
8. [ ] Enquanto derrete com magma, o core continua aceso (frente ativa) no visual v2, e o rendimento segue 2x.
9. [ ] As paredes ficam no tier Nether normal; só o core muda para v2.
10. [ ] Standard, End e Deep Core não têm visual v2, mesmo com pyrealloy.
11. [ ] Mundo da `mc1.21.1-v0.5.0-beta.10` com um Nether Core carrega; o core aparece no visual normal e só muda quando o combustível for quente.
