# Forgeweave 0.5.0-beta.7 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.7.jar` (Release `mc1.21.1-v0.5.0-beta.7`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag responde à sua diretriz de 2026-09-04 (#965): a escada do Draconic passa a ter os quatro tech levels do próprio DE, e não três. Entram o material `draconium_core` (só Part Builder), o metal de fusão `duskweld` no tier inerte, a trait `evolving` abaixo de `evolved`, e a grade de módulos nova (2x3, 2x6, 4x5, 6x6). Nenhum id que já foi publicado mudou de sentido: `evolved` continua wyvern, `evolved2` continua draconic, `evolved3` continua chaotic. A promessa de save-compat continua valendo. Defeitos viram issues `needs-triage`.

**Nada do Draconic rodou num cliente aqui**: o DE nunca entra num classpath de execução, só de compilação e de teste unitário. As seções B, C e D são as que este playtest existe para confirmar.

## A. Save-compat da escada (obrigatório, faça antes do resto)
1. [ ] Mundo da `mc1.21.1-v0.5.0-beta.6` com uma ferramenta de emberweld carrega, e ela continua sendo tier wyvern: passa no rung wyvern da fusão e é recusada no draconic. Uma de starweld continua draconic, uma de voidweld continua chaotic.
2. [ ] A mesma ferramenta de emberweld mostra "Draconic upgrades: n de 12" no tooltip, não mais "de 4". O número muda porque a grade cresceu, não porque a ferramenta mudou de tier.
3. [ ] Ferramentas com upgrade de fusão do beta.6 seguem com os modifiers e com os slots livres que tinham.

## B. Tier inerte: material e metal (#965, precisa do DE)
4. [ ] ⚠ `draconium_core` aparece no Part Builder como sexto material do DE, com stats abaixo de `wyvern`. Não tem fluido, não tem melting e não tem casting, igual aos outros três cores.
5. [ ] ⚠ `draconium_core` carrega `evolving` e `coremend`; nenhum dos outros três cores carrega `evolving`.
6. [ ] ⚠ Duskweld existe como lingote, nugget, bloco e fluido, na mesma linha da aba criativa que emberweld, starweld e voidweld. A cor ameixa se distingue das outras três a olho nu.
7. [ ] ⚠ A receita de fusão do lingote de duskweld pede Weldheart no core, mais draconium core, bloco de esmeralda e dois lingotes de draconium nos injetores, a 1.000.000 RF no tier draconium. Custa menos que emberweld (4.000.000 RF) e sai do mesmo Weldheart.
8. [ ] ⚠ Duskweld derrete com magma (1600), abaixo do brimspar que emberweld pede.
9. [ ] ⚠ Ferramenta de duskweld carrega `evolving` e `soulwick`; soul wick cura menos que soul rend.

## C. Os quatro rungs de fusão (#965, precisa do DE)
10. [ ] ⚠ Ferramenta de duskweld passa no rung draconium das oito linhas de upgrade e é recusada no wyvern. Ferramenta de emberweld é recusada no draconium (já passou dele) e passa no wyvern.
11. [ ] ⚠ Ferramenta com cabeça de `draconium_core` passa no rung draconium e é recusada no wyvern, do mesmo jeito que a de duskweld.
12. [ ] ⚠ Ferramenta comum, sem parte do Draconic, continua recusada em todos os quatro rungs.
13. [ ] ⚠ No JEI, a categoria de fusão do DE mostra o catalisador do rung draconium como ferramenta de duskweld, e o resultado nomeado, por exemplo "Duskweld Pickaxe + Haste I".

## D. Grade de módulos nova (#965, precisa do DE)
14. [ ] ⚠ A tela de módulos do DE abre com grade 2x3 numa ferramenta de tier inerte, 2x6 no wyvern, 4x5 no draconic e 6x6 no chaotic. O tooltip mostra 6, 12, 20 e 36 respectivamente.
15. [ ] ⚠ **Shield controller (2x2)**: cabe no peitoral em todos os quatro tiers, inclusive no inerte. Era isso que você queria; se não for, é aqui que a grade inerte muda.
16. [ ] ⚠ **Energy link (4x4)**: não cabe no inerte nem no wyvern (as duas grades têm só 2 de largura) e cabe no draconic e no chaotic. Confirme que essa é a divisão que você quer, porque ela é da forma da grade e não da contagem de células.
17. [ ] ⚠ Com a grade maior, dá para instalar mais de um módulo do mesmo tipo até o limite do próprio DE; nada de módulo sumindo ou de grade desenhando fora da tela.
18. [ ] ⚠ Módulos instalados no beta.6 continuam instalados depois do restart nesta versão, com a grade maior em volta.

## E. Sem o Draconic
19. [ ] Sem o DE instalado o jogo carrega normal: sem `draconium_core`, sem duskweld, sem Weldheart, sem receita de fusão e sem tecla de módulos.

## F. Publish (obrigatório)
20. [ ] **Spark** no dedicado ocioso: a grade maior não adiciona custo por tick.
21. [ ] Jars GitHub/Modrinth/CurseForge byte-idênticos (`sha256sum`).

## G. Decisões pendentes suas
- Grade do tier inerte em 2x3: você pediu para confirmar. Ela é a única das quatro que não tem contraparte no DE.
- Nome e cor do tier inerte: `duskweld` em ameixa `#7E2A6E`, e `draconium_core` em azul-ardósia `#3E4A63`. Ambos passam no `scripts/audit_palette.py`.
- Nomes das traits: `evolving` entrou embaixo e os três `evolved` ficaram como estavam, então os algarismos romanos leem um degrau atrás do rung. As descrições passaram a citar o tier do DE (draconium, wyvern, draconic, chaotic) em vez do número. Se preferir renomear os shipped, é uma decisão sua.
- Stats de `draconium_core` e de duskweld foram interpolados entre os vizinhos. Ajuste depois de sentir no jogo.
