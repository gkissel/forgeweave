# Forgeweave 0.5.0-beta.9 — checklist de playtest (pt-BR)

Build: `forgeweave-0.5.0-beta.9.jar` (Release `mc1.21.1-v0.5.0-beta.9`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial.

Esta tag fecha as diretrizes de 2026-09-05 e 06 (#978, #979 e o ícone do dagger): a onda de tier do core passa a cobrir todos os blocos de parede e piso, aparece também no Ponder, e os ícones de parte das ferramentas novas são o mesmo sprite da camada montada. Os itens da checklist da beta.8 continuam valendo; os abaixo são só o que mudou. Defeitos viram issues `needs-triage`.

## A. Onda de tier em todos os blocos (servidor dedicado)
1. [ ] Smeltery 3x3 com paredes misturadas: seared bricks, seared cobblestone, vidro, tanque, gauge, window, drain, duct e chute, piso de paver. Trocar Standard Core por Nether Core: todos viram para a cor do Nether Core, espalhando do core em uns três segundos.
2. [ ] O tanque na parede continua com o fluido que tinha depois de virar; o duct mantém o filtro; o drain e o chute continuam funcionando.
3. [ ] Derramar dragon breath e depois deep blood no core: a onda roda de novo nas duas transformações, com a cor de cada tier.
4. [ ] Voltar para um Standard Core: tudo volta ao visual padrão.
5. [ ] Quebrar cada tipo de bloco em tier alto dropa o item normal dele.
6. [ ] Seared furnace e seared reservoir montados só com esses blocos continuam formando; os blocos deles ficam no tier padrão (esses controladores não têm tier).
7. [ ] Mundo da `mc1.21.1-v0.5.0-beta.8` com smeltery formado carrega e, no primeiro scan, todos os blocos de parede viram para o tier do core.

## B. Ponder (cliente)
8. [ ] Na cena "Smeltery Cores" (pelo Standard Core no JEI ou na aba), a cada troca de core os tijolos, o tanque e o vidro da parede mudam de cor em anéis a partir do core, e a legenda do Nether Core fala disso.

## C. Ícones de parte (cliente)
9. [ ] No Part Builder, o ícone de `knife_blade` (cabeça do dagger) é o mesmo desenho da lâmina do dagger montado; pattern, cast e clay cast dessa parte mostram a silhueta nova.
10. [ ] Com o pacote Legacy ligado, o ícone de `knife_blade` volta para o antigo (derivado do Tinkers' 1.20); o do warmace, katana e scimitar não mudam.
