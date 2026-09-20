# Forgeweave 0.6.0-beta.2 — checklist de playtest (pt-BR)

Build: `forgeweave-0.6.0-beta.2.jar` (Release `mc1.21.1-v0.6.0-beta.2`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. ⚠ = só dá para conferir em cliente; nenhum agente viu isso rodando com o mod parceiro instalado.

Esta tag junta o que entrou depois da beta.1: o botão único de armadura (#1081), os cristais empowered do Actually Additions e a correção da Part Builder para os cristais comuns (#1069), a API de addons (#1008: #1065, #1066, #1067, #1083) e o brief do designer em português. O checklist da beta.1 continua valendo inteiro, e o que ficou sem marcar lá segue pendente. Defeitos viram issues `needs-triage`.

## A. Botão único de armadura (#1081)
1. [ ] ⚠ Tool Station: a barra lateral tem um botão "Armor" só, no lugar dos quatro de antes. Os outros botões não mudaram de lugar nem de ordem.
2. [ ] ⚠ Tool Forge: tem "Armor" e "Heavy armor", este com o selo do large plate no ícone. Nenhum botão por peça.
3. [ ] Com o botão "Armor", cada uma das quatro platings leves mais maille monta a peça certa: helmet plating dá capacete, chestplate plating dá peitoral, e assim por diante. Vale na Tool Station e na Tool Forge.
4. [ ] Com "Heavy armor" na Tool Forge, cada plating pesada mais maille mais large plate monta a peça pesada certa.
5. [ ] Plating pesada na Tool Station continua recusada com "This is too large to assemble here. Build it at a Tool Forge."
6. [ ] ⚠ Os slots do layout leve ficam na posição do 1.20: plating em cima, maille embaixo. Com o slot vazio aparece o fantasma genérico de plating, "Plating" na lista de componentes e o armor stand vazio. Com uma plating colocada, o fantasma, a lista, o preview grande e o armor stand acompanham a peça.
7. [ ] Modificar, reparar e renomear uma peça pronta pelo mesmo botão funciona para as oito peças.
8. [ ] ⚠ JEI continua mostrando oito receitas de armadura em duas categorias, e o "+" de transferência leva cada uma para o layout novo. O capítulo de armadura do livro, a página da Tool Station e a cena de Ponder falam de um botão só.
9. [ ] Conhecido: troca de parte não funciona em armadura, nunca funcionou (#1087 em andamento).

## B. Actually Additions (#1069, com o mod instalado)
10. [ ] ⚠ Um cristal avulso de restonia, palis, diamatine, void, emeradic ou enori monta parte na Part Builder, sem precisar compactar nove num bloco. Era esse o defeito dos seis cristais comuns. Black quartz continua montando.
11. [ ] ⚠ Os seis cristais empowered aparecem como materiais, um degrau acima dos comuns (tier netherite), e também montam na Part Builder a partir do item avulso e do bloco.
12. [ ] ⚠ Nenhum cristal derrete na smeltery nem tem molde: são só de Part Builder.
13. [ ] ⚠ Os traits dos empowered se leem como versões mais fortes dos comuns. O `empowered_emeradic_bulwark` estava ao contrário na beta.2 e foi corrigido depois dela (#1091): ele usava `damage_floor`, que é uma desvantagem, não proteção. Em vez de "o portador nunca fica abaixo de 2 corações", como a descrição dizia, todo golpe que acertava quem vestia a peça passava a causar pelo menos 2 corações, e o cristal empowered ficava pior que o comum. Agora ele dá 0,3 de resistência a repulsão com a ferramenta na mão, o dobro do `verdant_ward` do cristal comum (armadura de netherite dá 0,1 por peça). Conferir que o tooltip fala de repulsão e que segurar a ferramenta realmente segura o jogador no lugar.

## C. API de addons (#1008)
14. [ ] O jogo abre, entra num mundo e carrega um mundo da beta.1 sem erro: a parte 1 moveu `Modifier`, `Trait`, `UpgradeHosts` e os seams de combate para `dev.gkissel.forgeweave.api`, e nada salvo muda de formato.
15. [ ] Toda ferramenta e parte que já existia aparece na Stencil Table, na Part Builder e na Tool Station no mesmo lugar e na mesma ordem de antes. Os slots das ferramentas na Tool Station e na Tool Forge não andaram um pixel.
16. [ ] O log de startup do servidor traz a linha com o total real de sincronização de materiais.
17. [ ] Smeltery: as estruturas que já existiam continuam formando igual (paredes, piso, tanques, drains, tanque energizado, os quatro tiers). As paredes agora são lidas de tags `forgeweave:smeltery/*`.
18. [ ] ! Opcional, para quem tem um pack: um datapack que põe um bloco vanilla na tag `forgeweave:smeltery/wall_addon` faz esse bloco valer como parede.
19. [ ] ! Opcional, com KubeJS: um script que escreve uma receita do Forgeweave com `event.createFromJson(...)` num dos doze registros carrega e funciona. `docs/addons.md` tem o exemplo.

## D. Documentos para ler
20. [ ] `docs/design/designer-brief.md`: está em português, sem o que já tem design, com lingotes, nuggets, minério bruto, minérios e blocos numa seção só. Conferir se a lista bate com o que o designer já entregou.
21. [ ] `docs/addons.md` e `docs/adr/0006-addon-api-stability.md`: a promessa de estabilidade é a que você quer fazer em público?

## Decisões pendentes
- ~~`empowered_emeradic_bulwark` (item 13): manter o piso de 2 corações ou trocar por algo menos forte.~~ Resolvida por #1091: o piso de 2 corações era uma desvantagem lida como proteção, e saiu. Fica a pergunta menor de sempre, o número: 0,3 de resistência a repulsão com a ferramenta na mão está bom, ou é muito perto do `heavy` (1,0, imunidade)? O mesmo vale para o `compressed_iron_heft`, que foi de `damage_floor` para 0,1 pela mesma razão, e para o `naga_ward`, que virou `stacking_resistance`.
- O cabo e a amarração da katana e o ícone `lacerate.png` são gerados por script, não desenhados. O brief os conta como prontos. Devolver à lista do designer ou deixar.
- `ToolLeveling.addXp` ficou fora do pacote `api`, e `socketed` e `goggles` seguem como modifiers internos (#1078). Aceitar ou pedir a mudança.
- O teto de sincronização de materiais está em 161,8 KB de 160 KiB (163.840 bytes). O próximo lote de materiais sobe o teto de novo; decidir se vale um limite mais folgado.
- O anexo do `/humanizer` enviado em 2026-09-19 era para substituir o skill do repo?
- Seguem abertas da beta.1: a regra de energia do jetpack do Mekanism, o toggle do Elementarium como exceção à D-M8-5, a tag `#forgeweave:large_tools` com armadura pesada dentro, o reagente do `socketed`, os três baldes do Just Dire Things na aba criativa, #971 (EMI em cliente) e #1032 (upgrades do JDT, bloqueada no mod de origem).
