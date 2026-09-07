# Forgeweave 0.5.0-beta.13 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.13.jar` (Release `mc1.21.1-v0.5.0-beta.13`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. Precisa do Draconic Evolution instalado; nada disso roda num cliente aqui.

Esta tag responde às suas diretrizes de 2026-09-06 sobre o compat do Draconic: welds e cores deixam de ser intercambiáveis (weld hospeda módulos, core recebe upgrade de fusão), ferramenta de tier chaotic fere os cristais do Chaos Guardian, e o awakened core ganha o trait Shieldbreaker. Os itens da beta.12 continuam valendo. Defeitos viram issues `needs-triage`.

## A. Weld x core (servidor dedicado)
1. [ ] Picareta de emberweld no crafting core de fusão com a receita de haste wyvern: a receita não aceita; a mesma picareta com cabeça de wyvern core aceita e sai com Haste II.
2. [ ] O JEI da categoria de fusão mostra como catalisador ferramentas de draconium core, wyvern, awakened e chaotic, não mais as de weld.
3. [ ] Ferramenta de emberweld abre a tela de módulos do DE e aceita um módulo; ferramenta de wyvern core não abre a tela.
4. [ ] Tooltip: ferramenta de weld mostra "Draconic modules: n of m" (n = módulos instalados); ferramenta de core mostra "Fusion upgrades: n of 8". Ferramenta com cabeça de core e cabo de weld mostra as duas linhas.
5. [ ] As descrições dos traits Evolving e Evolved I a III falam das duas classes e citam o tier certo.
6. [ ] Mundo da `mc1.21.1-v0.5.0-beta.12` com um weld que tinha upgrade de fusão: o modifier continua na ferramenta e ela continua hospedando módulos; ela só não recebe mais upgrades de fusão novos.

## B. Chaotic contra os cristais (servidor dedicado)
7. [ ] Bater num cristal do Chaos Guardian com uma espada de voidweld ou de chaotic core: o cristal perde escudo a cada golpe e é destruído depois de golpes suficientes (o dano é o attack damage da ferramenta).
8. [ ] A mesma espada de starweld ou awakened não fere o cristal (só o som de escudo).
9. [ ] Bater em qualquer outro mob com a espada chaotic continua igual (seams, traits, modifiers).
10. [ ] Com o config `chaoticBypassCrystalShield` do DE desligado, o cristal volta a ignorar o golpe.

## C. Shieldbreaker (servidor dedicado, dois jogadores)
11. [ ] Alvo com peitoral modular do DE com shield controller carregado: cada golpe de uma ferramenta com parte de awakened core tira quatro vezes o attack damage do escudo; o escudo cai em poucos golpes e o dano começa a passar.
12. [ ] Mesma ferramenta contra alvo sem escudo do DE: nada além do dano normal.
13. [ ] O trait aparece no tooltip da parte de awakened e da ferramenta montada.
