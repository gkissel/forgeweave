# Forgeweave 0.5.0-beta.10 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.10.jar` (Release `mc1.21.1-v0.5.0-beta.10`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag responde às suas diretrizes de 2026-09-06: as texturas dos três cores passam a ser o mesmo recolor das paredes (geradas a partir do seared bricks e da frente do Standard Core novos), o Nether Core deixa de ter receita shaped e passa a ser feito com blazing blood derramado sobre um Standard Core, e a cena do Ponder foi refeita com a escada completa. Os itens da beta.9 continuam valendo. Defeitos viram issues `needs-triage`.

## A. Cores e paredes iguais (cliente)
1. [ ] Smeltery com Nether Core formado: o lado e o topo do core têm exatamente a mesma cor e o mesmo desenho dos seared bricks das paredes; nenhum detalhe carmim nos cantos do core.
2. [ ] O mesmo para End Core (roxo) e Deep Core (teal).
3. [ ] A frente acesa de cada core mantém o fogo laranja; só o tijolo em volta muda de cor. A frente apagada é tijolo tingido sem fogo.

## B. Nether Core por fluido (servidor dedicado)
4. [ ] A receita shaped do Nether Core (anel de seared bricks com netherite) sumiu do JEI e da mesa de crafting.
5. [ ] JEI mostra as três receitas de transformação de core: blazing blood (500 mB) sobre Standard Core, dragon breath (1000 mB) sobre Nether Core, deep blood (2000 mB) sobre End Core.
6. [ ] Derreter 25 blazes no smeltery rende os 500 mB de blazing blood; com um faucet sobre o Standard Core alimentado por um tanque com esse fluido, clicar no faucet transforma o core em Nether Core e a onda de tier corre pelas paredes.
7. [ ] Dragon breath ou deep blood sobre um Standard Core não fazem nada e não consomem fluido.
8. [ ] Mundo da `mc1.21.1-v0.5.0-beta.9` com um Nether Core craftado carrega e o core continua funcionando com 2x de rendimento.

## C. Ponder (cliente)
9. [ ] A cena "Smeltery Cores" mostra, nessa ordem: Standard Core, faucet com blazing blood virando Nether Core (com a onda nas paredes), dragon breath virando End Core, deep blood virando Deep Core, e a legenda final sobre fluido errado.
10. [ ] Nenhuma legenda cita netherite nem "craftado" para o Nether Core.
