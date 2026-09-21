# Forgeweave 0.6.0-beta.4 — checklist de playtest (pt-BR)

Build: `forgeweave-0.6.0-beta.4.jar` (Release `mc1.21.1-v0.6.0-beta.4`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. ⚠ = só dá para conferir em cliente.

Esta tag responde à review pública de um jogador (issue #1101): tudo mal explicado, texto quebrado no lugar de descrição, redação inconsistente nos modifiers, traits repetidos com nomes diferentes, livro de materiais sem ordem e materiais que não fazem nada. Entraram #1102 a #1106 e #1112 a #1114, mais a #1118. Os checklists anteriores continuam valendo. Defeitos viram issues `needs-triage`.

Quase tudo aqui mexe em texto, livro, som e partícula, então quase tudo é ⚠. Nenhum agente abriu um cliente, fora as capturas do livro.

## A. Save antigo (#1103)
1. [ ] Abrir um mundo da beta.3 com ferramentas e armaduras guardadas. Toda peça continua mostrando nome de trait de verdade, nunca `trait.forgeweave.algo.name`, e o efeito continua valendo (uma picareta de ironwood ainda resiste a repulsão). 123 ids antigos foram aposentados e apontam para o substituto.
2. [ ] Conquistas já ganhas na beta.3 continuam marcadas.

## B. Traits em famílias com nível (#1103)
3. [ ] ⚠ Ferramentas de inferium, imperium e insanium mostram "Keen Edge", "Keen Edge II" e "Keen Edge IV": mesma frase, número trocado. Nenhuma descrição aparece com `%s` ou `%` sobrando.
4. [ ] Nenhum efeito aparece com dois nomes. Imunidade a fogo é sempre "Fireward" (antes eram seis nomes).
5. [ ] Força: um set de Swiftward dá 32% de velocidade e dá para sentir andando. Uma peça de Heft segura você contra um creeper. Soul Rend I cura de forma visível numa luta. Uma ferramenta de wood danificada recupera 1 ponto a cada 10 s.
6. [ ] Netherite começa com cinco slots de modifier, não quatro (Writable II).
7. [ ] Set completo não vira imunidade: Voidward esquiva cerca de 25% (nível I) ou 34% (II), Magic Protection corta 48/64/80%, os wards de efeito (Blightward, Witherward, Venomward, Chillback) respondem a cerca de 3 em cada 5 golpes.
8. [ ] Arcing I só arqueia em golpe totalmente carregado; Arcing II (fluix, ludicrite) arqueia em todo golpe.
9. [ ] Draconic: ferramenta de duskweld hospeda módulos e não aceita fusion; wyvern e awakened aceitam. Azure silver e steeleaf, que agora têm Ruthless, não são aceitos como catalisador.

## C. Traits de assinatura (#1114)
10. [ ] Veinseeker (hollowstone): quebrar um bloco de um veio leva o veio conectado inteiro, até 12 blocos, pagando 4 de durabilidade por bloco extra. Bloco isolado não custa nada a mais.
11. [ ] Warcharge (warspar): três abates seguidos, o tooltip lê "Banked: 3 charges", o próximo golpe dói bem mais e zera o contador.
12. [ ] Backlash (faultsteel, set completo): apanhando de um grupo, a peça estoura com partícula, som e dano em tudo a 4 blocos.
13. [ ] Sunforged (sunsteel): minerar ao sol do meio-dia é 40% mais rápido; fora do sol, 10% mais lento.
14. [ ] Glowveil tem lado bom além do veneno (alvo brilhando, armadura esconde no escuro). Fulmenite arqueia em cadeia no golpe carregado.
15. [ ] Corda, pena e folha: a velocidade de saque do arco não mudou. Corda de string agrupa bem mais perto num tiro totalmente puxado. Empenagem de pena cai visivelmente menos num tiro longo. Empenagem de folha, mais ou menos uma vez em quatro, não gasta a flecha e avisa com partícula e som.
16. [ ] Steadfast (bronze, tin, invar): +20% de durabilidade, crescendo junto com o material.
17. [ ] Pegar dez materiais ao acaso, de qualquer mod instalado: todos têm pelo menos um trait que dá para sentir na ferramenta e um na armadura.

## D. Curva de progressão (#1113)
18. [ ] Riftalloy ganha de murkiron puro em durabilidade e dano. Subindo glowveil, sunsteel e truesteel, cada passo dá um salto visível. Truesteel (1800/13,0/12,8) é o melhor metal próprio do mod.
19. [ ] Combustível: só com lava, um lingote de glowveil não derrete e o núcleo diz que falta calor. Com blazing blood derrete. Truesteel pede molten magma. O pó de truesteel também recusa lava.
20. [ ] Obsidiana continua talhável no Part Builder, nível netherite e 139 de durabilidade, como no 1.12. `ancient` (netherite scrap) agora é nível netherite e não minera mais minério de hardcinder.
21. [ ] Peitoral de truesteel tem durabilidade na mesma proporção da cabeça (1107 contra 646 antes). Arco de truesteel puxa mais rápido e bate mais.
22. [ ] Proteção: set de seared stone no fogo, obsidiana contra explosão, cobalt contra golpe direto. São 4 pontos por peça por tipo de dano (3 para golpe corpo a corpo) e dá para ver a diferença contra um set sem trait.
23. [ ] ⚠ Os seis cristais da Actually Additions têm fichas diferentes no livro: palis é o rápido, enori o durão, void o frágil que bate forte.

## E. Aviso quando o trait ativa (#1112)
24. [ ] ⚠ Esquiva solta uma nuvenzinha e som de escudo. Efeito aplicado no golpe solta partícula de efeito e som baixo de fervura. Auto-reparo solta partícula verde e som de bigorna só quando um ponto volta. Roubo de vida aparece em cima de você. Salvamento de morte usa partícula e som de totem.
25. [ ] ⚠ Arma rápida: o aviso sai no máximo a cada meio segundo. Anotar se o volume (0,35) incomoda em luta longa e se algum som não combina, em especial o trovão do choque.
26. [ ] ⚠ Tooltip com Shift: `Braced: X% off a blow, N of M stacks` sobe a cada golpe e some depois; `Can save again in Xs` conta para baixo e some.
27. [ ] ⚠ `traitFeedback = false` em `config/forgeweave-client.toml` desliga partículas e sons novos e mais nada. Em servidor dedicado, a config de um jogador não muda a tela do outro.

## F. Livro como tutorial (#1105)
28. [ ] ⚠ O índice mostra nove capítulos numa página: Introduction, Smeltery, Casting, Alloys, Materials, Modifiers, Armor, Leveling, Tools. Todo capítulo menos o último termina com "What next" apontando o próximo.
29. [ ] ⚠ Ler "The Whole Ladder" como quem nunca jogou Tinkers'. Anotar o que não ficou claro.
30. [ ] Seguir ao pé da letra, sem JEI: "Your First Pickaxe" (um log só), "Grout and Seared Bricks" (a conta de 56 tijolos fecha?), "Making a Cast" e "Your First Iron Pickaxe".
31. [ ] ⚠ Conferir uma cadeia de "The Long Chains" (truesteel ou stormalloy) contra o JEI, receita por receita.
32. [ ] ⚠ Alguma página tem texto cortado ou saindo da folha? As páginas novas são longas.

## G. Capítulo de materiais (#1104)
33. [ ] ⚠ Abre em "The Material Ladder", sete estágios com a frase do que destrava cada um. Clicar num estágio abre a grade de ícones dele. Próprios e vanilla primeiro, depois um grupo por mod, e no fim os que só fazem corda, haste e empenagem.
34. [ ] ⚠ Página do manyullyn: "Stage: Deep alloys" e "Alloyed from: 2 parts Molten Cobalt / 2 parts Molten Ardite", com cada linha levando à página do insumo. Página do ferro: "Melts from" com o lingote primeiro e no máximo três linhas.
35. [ ] ⚠ Clicar no nome de um trait abre a página da família: níveis com o número de cada um e os materiais que concedem, clicáveis de volta.
36. [ ] ⚠ `atomic_matter_alloy` aparece no último estágio, depois de truesteel.

## H. Conquistas (#1106)
37. [ ] ⚠ A raiz é o livro guia, já concluída, com dois galhos: ferramentas e smeltery. A árvore cabe na aba sem galho saindo da tela?
38. [ ] Padrão, Part Builder, Tool Station e primeira ferramenta acendem na hora e em ordem. Reparo e troca de parte acendem cada um a sua.
39. [ ] "Nada de sobra" acende ao gastar o último slot. Subir de nível uma ferramenta e uma peça de armadura acende duas conquistas em galhos diferentes. O set de quatro peças aceita leve e pesada misturadas.
40. [ ] Molde de ouro, núcleo do Nether e balde de blazing blood: três conquistas. Liga de dois passos (tideiron) e de três (sunsteel): conta fundir o lingote, não o fluido.

## I. Modifiers e redação (#1102, #1118)
41. [ ] ⚠ Shift sobre uma ferramenta com modifier: a linha mostra nome, nível e a descrição, como trait. Sem Shift, só nome e nível. No JEI, passar o mouse no modifier mostra a descrição.
42. [ ] ⚠ Ler vinte descrições ao acaso, de trait e de modifier: todas dizem o que fazem e quanto, em segundos e nunca em ticks; efeito de armadura diz o valor por peça e o total do set; modifier diz quanto vale um nível, o máximo e o custo em slots.

## Decisões pendentes
- As interjeições de flavour dos modifiers ("Ka-Boom!") saíram. Voltar com elas no formato do 1.12 (linha em itálico, depois o mecanismo) pede mudança no render do tooltip, da estação e do livro.
- Truesteel e as ligas de quatro passos ficaram acima do endgame de compat em stats. Aceitar ou subir o endgame de compat.
- A referência de traits ficou no fim do capítulo de materiais. Virar capítulo próprio divide o índice em duas páginas.
- As quatro páginas de modifiers cross-mod ainda vêm antes das 48 geradas.
- `projectile_protection`: uma peça sozinha ainda dá 1/4 da repulsão do upstream.
- Continuam abertas as da beta.2 e beta.3: o cabo, a amarração e o `lacerate.png` da katana gerados por script; `ToolLeveling.addXp` fora do pacote `api`; o armor stand de frente ou em três quartos; o teto de sincronização de materiais, agora em 166,6 KB de 170 KB.
