# Forgeweave 0.6.0-beta.1 — checklist de playtest (pt-BR)

Build: `forgeweave-0.6.0-beta.1.jar` (Release `mc1.21.1-v0.6.0-beta.1`), MC 1.21.1 + NeoForge, servidor dedicado, mundo novo, sem cheats. "!" = parcial. ⚠ = só dá para conferir em cliente; nenhum agente viu isso rodando.

Esta é a tag de fechamento do M8 (deep compat). Nenhuma das integrações foi vista com o mod parceiro instalado: nenhum deles entra no classpath de build ou de teste (JC-B), então tudo que está aqui foi lido no código-fonte do parceiro, nunca executado junto. É por isso que o checklist é tão longo: ele é a única verificação que essas integrações vão receber antes de sair.

Os blocos estão na ordem do risco, do mais provável de quebrar para o menos. Blocos A a N precisam do mod parceiro instalado; O a Q rodam sem nenhum deles. O bloco R é o teste de aceitação do M8 escrito em `docs/SCOPE.md` e precisa do pack completo. Defeitos viram issues `needs-triage`.

## A. Elementarium e Allthemodium (#1034)

Este é o bloco que mais tem chance de falhar: o gerador do Elementarium lê famílias de tags contra as quais não consegue compilar, então ele está certo no formato e não verificado no conteúdo.

1. [ ] Elementarium instalado: os seis elementos curados aparecem como materiais, com stats interpolados que fazem sentido no Tool Station e no Part Builder. Se as tags `c:ingots/*` reais do mod não tiverem o que o gerador espera, é aqui que aparece.
2. [ ] Uma ferramenta montada com cada um dos seis monta, repara e mostra a linha de trait esperada.
3. [ ] Com `elementariumMaterials = false` e o mod instalado, nenhum dos materiais registra, e uma ferramenta já feita com um deles carrega sem quebrar.
4. [ ] Allthemodium: uma picareta de resonite mina minério de unobtainium, e uma picareta de unobtainium mina minério de resonite. As duas direções.
5. [ ] Os minérios Track B geram dentro da dimensão de mineração do Allthemodium. O terreno é plano e em camadas, então pode acontecer de o bloco hospedeiro de um minério não cair na faixa de altura dele.
6. [ ] Com `allthemodiumTiers = false`, a equivalência de tier some e nada mais muda.

## B. Mystical Agriculture (#1036)

A integração troca a classe com que o item registra. É a forma que mais come componente quando dá errado.

7. [ ] ⚠ A Tinkering Table abre com uma ferramenta do Forgeweave dentro e instala um augment, e o augment funciona. Comece por aqui: é o que um jogador notaria faltando.
8. [ ] Plantar uma semente de resonite em farmland de supremium sobre um bloco de resonite e colher a essência. Repetir para um dos materiais-combustível.
9. [ ] Uma semente crafta, e os sprites gerados parecem o material em vez de template cinza.
10. [ ] Os nove presets aparecem como materiais; um lingote de essência é aceito no Part Builder e como item de reparo. Insanium só aparece com o Mystical Agradditions junto.
11. [ ] `mysticalAgricultureAugments = false` com o mod instalado: a Tinkering Table não oferece slot de augment, e uma peça que já carrega augments mantém todos. Não precisa reiniciar: a leitura é em runtime (#1036). A issue #975 pede um reinício aqui e está desatualizada.
12. [ ] Ligar de volta sem reiniciar: o augment volta a funcionar na mesma peça, sem reload.
13. [ ] Toda ferramenta do Forgeweave é aceita no tier 5 com um slot de augment, e nove formas de ferramenta não são augmentáveis. Conferir se isso incomoda na prática. É limitação da interface `ITinkerable`, que não carrega tier por stack (decisão do mantenedor de 2026-09-18: sem mixin por enquanto).

## C. Mekanism, fases 1 e 2 (#1040, #1060)

14. [ ] `atomic_matter_alloy` só é feito no Antiprotonic Nucleosynthesizer (liga atômica + antimatéria), e o JEI mostra a receita.
15. [ ] Uma ferramenta feita do metal abre a Modification Station do Mekanism e aceita um módulo.
16. [ ] Silk touch e fortune mudam o drop; o nível do fortune aparece onde apareceria o nível de um encantamento.
17. [ ] Escavação acelera a ferramenta e nunca a deixa mais lenta; blasting alarga a quebra; vein mining veia o minério e para no limite configurado. Cada um gasta `mekanismEnergyPerBlock` do buffer, e com o buffer vazio a ferramenta volta aos números dela.
18. [ ] Um módulo de absorção do MekaSuit em armadura do Forgeweave reduz o golpe e gasta energia; com o buffer vazio não absorve nada, em vez de absorver de graça.
19. [ ] Armadura com chapeamento do metal bloqueia radiação por completo.
20. [ ] Teleporte: move o jogador até onde ele olha, no máximo `mekanismTeleportMaxDistance`, gasta `mekanismEnergyPerTeleport`, e não faz nada com o buffer vazio.
21. [ ] Farming: ara, achata e descasca no clique direito da ferramenta, e nunca pega um bloco para o qual a própria ferramenta já tem clique direito. Shearing tosquia ovelha e colmeia.
22. [ ] Attack amplification sobe o dano e aparece no tooltip.
23. [ ] Elytra: a unidade do Mekanism no chestplate plana, o modifier `elytra_flight` também, e os dois juntos se comportam como um só.
24. [ ] Modulação gravitacional dá voo só com o chestplate e gasta `mekanismEnergyPerFlightTick`.
25. [ ] Jetpack: sobe com o pulo segurado, paira no modo hover, e para com o buffer vazio. Confirmar a regra de energia: hoje ele cobra energia sempre que o portador está no ar com o módulo ligado, não só quando está propulsionando.
26. [ ] As quatro cadeias de minério rodam nos onze minérios Track B: Enrichment Chamber (2x), Purification Chamber (3x), Chemical Injection Chamber (4x) e Crusher. Não existe 5x nem forma de cristal, e #1060 explica por quê. A issue #975 pede "2x até 5x" e está desatualizada.
27. [ ] O JEI mostra os seis passos de cada minério.
28. [ ] `rayward` aplica no Tool Station a partir de um lingote de chumbo; no nível IV a armadura corta radiação por completo e no nível II pela metade.
29. [ ] Trocar a cabeça de metal de uma ferramenta com módulos instalados devolve os itens dos módulos ao jogador e não deixa nenhum na ferramenta (#1040).
30. [ ] `mekanismModules = false`: a tela de módulos não abre, as receitas da cadeia de minério somem, todo efeito fica inerte, e o `rayward` continua aplicando e protegendo. Ligar de volta restaura um módulo instalado sem reload.
31. [ ] Sem o Mekanism instalado: o metal existe no livro e no registro, mas as formas de item dele ficam escondidas e inalcançáveis, e o log fica quieto.

## D. Apotheosis: sockets, affixes e encantamento (#1017, #1027)

32. [ ] ⚠ Aplicar `socketed` com o Sigil of Socketing, encaixar uma gem, e conferir o bônus no tooltip e no efeito. Cada nível custa um slot de modifier e a contagem de slots livres do painel cai junto.
33. [ ] Subir o nível da ferramenta pelo M7 e gastar o slot ganho em outro nível de `socketed`. O socket novo aceita uma segunda gem, e um bônus de gem que divide grandeza com um trait ou modifier soma em vez de substituir.
34. [ ] Uma ferramenta do Forgeweave com affix sai de um baú de loot, o texto do affix renderiza junto das linhas do Forgeweave, o bônus aplica, e a ferramenta continua reparando, aceitando modifiers e subindo de nível.
35. [ ] ⚠ Um arco do Forgeweave pega affix de arco, não de espada. É a única coisa para que o arquivo de override existe e a única que nenhum teste consegue confirmar.
36. [ ] ⚠ As linhas de affix caem depois do bloco inteiro do Forgeweave, inclusive depois da dica "Hold Shift".
37. [ ] Reforjar e augmentar uma ferramenta do Forgeweave nas mesas do próprio Apotheosis.
38. [ ] Encantar na mesa do Apothic Enchanting com `allowVanillaEnchanting` e `apotheosisEnchanting` desligados um de cada vez. Com os dois ligados o encantamento convive com modifiers, sockets e nível, e não gasta slot.
39. [ ] `apotheosisAffixes = false` num mundo que tem o Apotheosis instalado, onde a guarda de ausência do mod não está mascarando o toggle.
40. [ ] Tirar o Apotheosis do pack e abrir o mundo: a ferramenta com socket e a com affix carregam intactas, e nada some.

## E. Occultism (#1037)

41. [ ] Um crusher spirit de cada rank mói os minérios que o `min_tier` dele permite e recusa os acima. Em particular, um Foliot Crusher não toca em resonite.
42. [ ] Um mining spirit devolve minérios Track B nas taxas da tabela de peso: fulmenite comum, resonite muito raro.
43. [ ] Os quatro rituais: o pentáculo valida, as tigelas aceitam gem e essência, uma ferramenta montada do Forgeweave na Golden Sacrificial Bowl é aceita, e a ferramenta que cai carrega o modifier no nível listado com os slots gastos.
44. [ ] Uma ferramenta sem slot livre é recusada, não consumida. É o único caminho em que um bug comeria a ferramenta; confira antes de gastar uma boa.
45. [ ] ⚠ As quatro linhas na categoria de ritual do JEI do Occultism, e se `ritual_dummy` e `result` lêem bem no layout dele.
46. [ ] `spirit_attuned_gem` no Part Builder e no manual de materiais, e `silver` presente num pack com Occultism e sem Immersive Engineering.
47. [ ] `occultismRituals = false`: os rituais somem e uma ferramenta que já ganhou um modifier por ritual mantém o modifier funcionando.

## F. Powah (#1015, #1028)

48. [ ] ⚠ `surgebound` sobe um nível por vez, na ordem energized steel, blazing, niotic, spirited, nitro; pular nível é recusado com mensagem. Cada nível dá +25% de capacidade de energia e +5% de velocidade; o nitro vale o dobro dos dois.
49. [ ] Os quatro cristais aparecem como materiais, e os seis presets do Powah aparecem.
50. [ ] Um thermo generator lê molten blazing blood (1500), molten magma (1700), molten brimspar (1900) e molten pyrealloy (2100) como fontes de calor nessas temperaturas.
51. [ ] `powahModifiers = false`: as receitas do `surgebound` somem e o efeito fica inerte, sem apagar o modifier da ferramenta. `powahHeatSources = false`: as entradas do data map param de contribuir e nada mais é tocado.

## G. Create, Immersive Engineering e EnderIO (#1028)

52. [ ] Create: uma das quatro ligas básicas (manyullyn, rose_gold, embercast, osmiridium) mistura no mixer aquecido a partir de dois lingotes com tag. Um minério Track B vira pó nas crushing wheels. Um lingote do Forgeweave vira plate na prensa.
53. [ ] ⚠ Uma ferramenta do Forgeweave num deployer do Create se comporta como qualquer outra ferramenta ali. Nenhum código foi escrito para isso; é verificação manual pura.
54. [ ] ⚠ Create: `create:goggles` num capacete do Forgeweave (leve ou pesado) não gasta slot, e os overlays do Create aparecem com o capacete vestido.
55. [ ] Immersive Engineering: a mesma liga sai do arc furnace por tag (lingote primário mais aditivo). Um minério Track B vira pó no crusher. Um lingote vira plate pelo `mold_plate` na metal press.
56. [ ] EnderIO: a mesma liga sai do alloy smelter (entrada de dois ou três slots). Os minérios moem no SAG mill.
57. [ ] `createRecipes`, `immersiveEngineeringRecipes` e `enderIoRecipes` desligados um a um: as linhas correspondentes param de resolver, nada mais é tocado, e um `/reload` com o toggle ligado de volta traz tudo.

## H. Just Dire Things e Eternal Ores (#1039, #1041, #1062)

58. [ ] Just Dire Things: os quatro materiais aparecem no Tool Station e no Part Builder. Os três tiers em forma de lingote (`ferricore`, `blazegold`, `eclipsealloy`) fundem e são vazados do fluido molten na smeltery. São `cast_only`, então vazar é o caminho normal deles. `celestigem` crafta direto no Part Builder a partir da gema bruta, porque não tem fluido.
59. [ ] Uma ferramenta ou peça de armadura de cada um mostra a linha de trait esperada (pisada firme, imunidade a fogo, o buffer de energia, ou energia mais a proteção contra morte do `eclipsealloy`).
60. [ ] Defeito conhecido: os três baldes de fluido do Just Dire Things aparecem na aba criativa e no JEI mesmo sem o mod instalado. É lacuna deliberada de #1062; confirmar que continua sendo só isso e que nada mais vaza.
61. [ ] Upgrades do Just Dire Things não alcançam equipamento do Forgeweave (D-M8-22, bloqueio no mod de origem). Pôr uma picareta do Forgeweave no slot base de uma smithing table e qualquer upgrade do Just Dire Things no slot de adição: o slot de resultado fica vazio. Repetir com um capacete e `upgrade_nightvision`. Conferir que as ferramentas do próprio Just Dire Things continuam aceitando os upgrades dela normalmente, e que nenhum dos dois mods loga nada sobre o outro.
62. [ ] Eternal Ores sozinho (sem Mekanism, Immersive Engineering, Modern Industrialization, BigReactors, Powah, RefinedStorage nem Occultism): os 19 materiais de dedupe registram, fundem do lingote ou do material bruto do próprio Eternal Ores, voltam a lingote vazados, e uma ferramenta ou armadura monta e repara.
63. [ ] Eternal Ores junto de um dos provedores originais (Mekanism e Eternal Ores dando `tin`, por exemplo): material único, sem entrada duplicada no JEI nem na aba criativa, e o lingote de qualquer um dos dois funciona.

## I. Presets Track A do segundo lote (#1062, #1063)

64. [ ] Twilight Forest: os oito materiais aparecem nas listas; os quatro tiers com fusão são vazados do fluido molten; os quatro só-Part-Builder craftam direto do item bruto; a linha de trait lê bem.
65. [ ] Ice and Fire: os dez materiais registram; dragon bone e os três dragonsteels fundem e são vazados; death worm chitin e troll leather craftam direto no Part Builder.
66. [ ] Silver com Ice and Fire mais um provedor original (Immersive Engineering ou Occultism): material único, sem duplicata, lingote de qualquer provedor serve.
67. [ ] Silent Gear: os cinco materiais aparecem, são vazados do fluido molten, e a linha de trait lê bem.
68. [ ] PneumaticCraft: Repressurized: `compressed_iron` registra, funde e é vazado de `pneumaticcraft:ingot_iron_compressed`, e monta e repara.
69. [ ] Forbidden and Arcanus: `deorum` registra com o nome corrigido, funde e é vazado de `forbidden_arcanus:deorum_ingot`, e o trait de têmpera reforçada lê bem ao lado do conjunto `REINFORCED_DEORUM` do próprio mod.
70. [ ] The Aether: `zanite` e `ambrosium` craftam direto no Part Builder da gema/estilhaço bruto (sem fluido); `gravitite` idem a partir de `enchanted_gravitite`.
71. [ ] L_Ender's Cataclysm: `ignitium` e `cursium` registram, fundem e são vazados dos lingotes de drop de boss, e os traits de imunidade a fogo e de maldição lêem bem.
72. [ ] Dois quaisquer desses mods instalados juntos: nenhum material duplicado, nenhuma entrada duplicada no JEI ou na aba criativa.

## J. Draconic Evolution (#1054)

73. [ ] Upgrades de fusão continuam aplicando na escada de 8 linhas por 4 tiers.
74. [ ] ⚠ A tela de módulos abre em equipamento evoluído, e os efeitos dos módulos disparam.
75. [ ] Montar uma weld tool evoluída, instalar uma mistura de módulos 1x1 e multi-célula (shield controller, energy link) perto das bordas do grid, e trocar a cabeça por uma weld de tier mais baixo ou por um material não-weld no Tool Station: o inventário de módulos volta correto em cada caso, e um módulo de energia devolvido não vem com carga.
76. [ ] `draconicFusion = false` e `draconicModules = false`: nenhuma linha de upgrade casa, nenhuma capability de hospedagem é anexada, e o equipamento que já carrega upgrades e módulos mantém tudo. Ligar de volta faz tudo voltar a funcionar sem perda.

## K. EMI (#1009, #1030, issue #971 aberta)

77. [ ] ⚠ Desinstalar o JEI, instalar só o EMI, e abrir: as 14 categorias de receita do Forgeweave renderizam e cada transfer handler ainda preenche a estação. Nenhum plugin nativo foi escrito: a ponte de plugin JEI do EMI carrega tudo (spike de #1009), e esta linha é a confirmação em cliente que falta para fechar a #971. A lista das 14 categorias está no PR #1009.
78. [ ] ⚠ A categoria Entity Melting pela ponte: a entidade fica centrada na abertura e não é cortada, `1.0` não encosta no coração, o slot de combustível cicla os combustíveis, e o tooltip de fluido lê bem. Foi o defeito que o screenshot do mantenedor pegou, corrigido em #1030.

## L. Jade e WTHIT (#1016)

79. [ ] ⚠ Jade instalado: o resfriamento do casting e o conteúdo da smeltery continuam lendo certo, e o nível de mineração aparece.
80. [ ] ⚠ WTHIT instalado, sem o Jade: a mesma coisa.
81. [ ] `overlays = false` com um dos dois instalado: os providers param de responder e nada mais muda.

## M. KubeJS (#832)

82. [ ] Um trait escrito por script registra e dispara numa ferramenta montada.
83. [ ] `kubejsTraits = false`: o trait não registra, e uma ferramenta que já carrega o id dele carrega sem quebrar.

## N. Better Combat e Epic Fight (#1051, #1055)

84. [ ] ⚠ Better Combat: dar swing com broadsword, katana, hammer e arco, e confirmar que cada um toca a animação própria em vez do swing vanilla, e que o dano mostrado continua batendo com o número do tooltip da ferramenta.
85. [ ] ⚠ Epic Fight: equipar katana (`uchigatana`), warmace (`axe`) e um set pesado completo. O combo e a postura da arma batem com o tipo atribuído, o peso da armadura se sente (ataque mais lento, stagger mais curto ao apanhar) e é maior no set pesado que no leve. O dano de tooltip do Forgeweave não muda com nenhum dos dois mods.
86. [ ] ⚠ O warmace **não tem arquivo de capability do Epic Fight**, igual à mace do vanilla, e por isso se comporta como ela (#1055). O Better Combat mantém `vanilla_mace`.

## O. Recursos novos do próprio Forgeweave

87. [ ] Modifier Worktable (#1064): tirar um nível de um modifier comum, de um incremental e de um `utility()` sem slot em armadura; a esponja seca; o estado sem nada aplicável; traits nunca são oferecidos; o caso `socketed`. Ordenar nos dois sentidos com os dois wraps, e a recusa numa ferramenta de um modifier só. Salvar e recarregar depois de uma remoção e depois de uma ordenação.
88. [ ] ⚠ Worktable, tela: a arte do painel bate com os slots (ferramenta em (8, 21), reagentes em (8, 45) e (8, 67), resultado em (125, 42), armadura na borda direita e a offhand embaixo). Os botões de modifier desenham o ícone do reagente no sprite certo e acendem no hover e na seleção. A barra de rolagem só aparece depois de dezesseis modifiers. Os dois painéis de info rolam. Com sete tipos de estação adjacentes, a tira de abas passa 16px da borda direita do painel.
89. [ ] ⚠ Worktable, JEI e livro: a categoria desenha os quatro slots e o título por função, o botão [+] enche o primeiro slot de reagente, e a página do Modifier Worktable abre em Modifiers no livro.
90. [ ] ⚠ Preview no armor stand (#1053): arrastar o boneco gira suave, mantém o yaw depois de um resize, e para de girar quando o botão é solto fora da caixa. O anel tem tooltip no hover. Abrir a estação com um baú ao lado: o preview some e dá lugar ao painel lateral. Conferir com uma peça pesada no Tool Forge, que as abas de armadura leve não cobrem. Com uma janela estreita, o JEI não invade o boneco.
91. [ ] ⚠ Seta e slot de saída na posição do 1.20 (#1053): comparar Tool Station e Tool Forge com os screenshots que vieram na issue.
92. [ ] ⚠ Tela do tanque energizado (#1026): o layout, o medidor, a barra e o botão de overdrive. O overdrive é botão na tela, não mais clique com a mão vazia no bloco. Ligado, dobra o custo e a velocidade. O espaçamento e as cores nunca foram vistos rodando.
93. [ ] Tanque energizado, comportamento: amostra de combustível por balde, carga FE por cabo, a smeltery derrete na temperatura da amostra. Buffer vazio não derrete nada. Dois tanques com amostras diferentes: só o mais quente gasta energia. `energizedTank = false` deixa o bloco dormente e devolve amostra e buffer intactos ao religar.
94. [ ] Cast da cabeça do warmace (#1052, #1055): a cabeça é **só vazada**, não sai mais do Part Builder por pattern. O cast é feito vazando ouro sobre um Heavy Core, e **o Heavy Core volta**, no slot de saída. Vale para o cast de ouro e para o clay cast.
95. [ ] Formas de material (#1019): cada metal com lingote tem nugget, bloco, dust, small dust, tiny dust, plate, double plate, rod, gear e wire; brimspar e fulmenite só têm os três dusts. Crafts: 2 lingotes → 1 plate; 2x2 plates → double plate; 2 lingotes na vertical → 2 rods; 4 lingotes em cruz → 1 gear; 3 nuggets → 1 wire. Escada de dusts ida e volta. Dust derrete como o lingote, small dust como um terço, tiny dust como nugget; dust de outro mod com a mesma tag `c:dusts/<id>` também derrete.
96. [ ] ⚠ Sprites 16x16 das formas de material: nenhum roxo de textura faltando, tint coerente com o material. Os oito templates agora são arquivos em `scripts/templates/material_forms/` e os sprites gerados são byte-idênticos aos de antes (#1056).
97. [ ] Modifiers definidos por datapack (#1035): um `forgeweave:modifier_definition` de um pack registra, aplica e aparece no tooltip. `modifierDefinitions = false`: o id do pack resolve para nada e a ferramenta que o carrega continua carregando.
98. [ ] ⚠ Um modifier de pack aparece no JEI e no livro do guia com o nome dele, não com uma chave de lang crua. Cliente dedicado ligado a servidor dedicado, para exercitar o caminho de sync do registro.
99. [ ] Migração de config (#1016): abrir o mundo com um `config/forgeweave-server.toml` plano de uma build antiga, de um pack real e não sintético. Os valores são divididos entre `general-server.toml`, `content-server.toml`, `compat-server.toml` e `worldgen-server.toml` em `config/forgeweave/`, nada que o pack tinha ajustado volta ao padrão, e o arquivo antigo vira `forgeweave-server.toml.migrated`.

## P. Forgeweave sozinho, sem nenhum mod parceiro

100. [ ] Mundo novo, sem nenhum dos mods integrados: nenhuma receita fantasma, nenhuma textura faltando, nenhum ruído no log, nem na entrada do mundo, nem ao abrir a aba criativa, nem ao abrir o JEI.
101. [ ] A exceção conhecida são os três baldes do Just Dire Things do item 60. Qualquer outra coisa que apareça sem o mod dono é defeito.
102. [ ] ⚠ Sem mundo carregado, no menu principal, o JEI ou outro mod que liste receitas não derruba o jogo, e um reload de recursos (F3+T) não dispara `Cannot get config value before config is loaded` (#1024).
103. [ ] Um mundo da `mc1.21.1-v0.5.0-beta.16` carrega inteiro: ferramentas, armadura vestida, níveis do M7, overslime e o inventário das estações, tudo intacto.
104. [ ] Sanity do JEI: as linhas de casting de plating e maille, as de montagem de armadura e as receitas de modifier de armadura aparecem sem mudança de código de JEI; sem o JEI o jogo carrega.
105. [ ] Perfil do spark no servidor dedicado ocioso: armadura vestida não custa nada por tick além do heartbeat da smeltery formada, e as estações ficam em 0.

## Q. Passada com todos os toggles desligados

Um mundo salvo com tudo ligado, carregando um baú com equipamento que carrega socket, affix, módulo do Mekanism, `surgebound`, modifier de ritual do Occultism, augment do Mystical Agriculture, upgrade de fusão e módulo do Draconic. Desligar os vinte toggles de `compat-server.toml` de uma vez e carregar o mundo.

106. [ ] O mod sobe, o mundo carrega, e nenhuma dessas peças perde componente. Inerte, nunca destrutivo (D-M7-3 aplicada ao compat).
107. [ ] Os vinte toggles da seção `compat`, por chave, todos em `false`: `draconicFusion`, `draconicModules`, `overlays`, `kubejsTraits`, `createGoggles`, `apotheosisSockets`, `energizedTank`, `powahModifiers`, `occultismRituals`, `apotheosisAffixes`, `apotheosisEnchanting`, `createRecipes`, `immersiveEngineeringRecipes`, `enderIoRecipes`, `powahHeatSources`, `modifierDefinitions`, `allthemodiumTiers`, `elementariumMaterials`, `mekanismModules`, `mysticalAgricultureAugments`.
108. [ ] Ligar os vinte de volta e recarregar: tudo volta a funcionar, nada foi perdido, e nada precisou de mundo novo.
109. [ ] A mesma passada nas outras seções, uma de cada vez, para confirmar que nenhuma delas leva estado junto: `content-server.toml` (`harvestTools`, `meleeWeapons`, `rangedWeapons`, `armor`, `gadgets`, `smeltery`, `modifiers`, `toolLeveling`), `worldgen-server.toml` (`genCobalt`, `genArdite`, `genTrackBOres`, `generateSlimeIslands`, `generateIslandsInSuperflat`, `slimeIslandsOnlyGenerateInSurfaceWorlds`) e `general-server.toml` (`allowVanillaEnchanting`, `reuseStencils`, `chestsKeepInventory`, `spawnWithBook`, `obsidianAlloy`, `addFlintRecipe`, `matchVanillaSlimeblock`, `enableClayCasts`, `craftCastableMaterials`).

## R. Playthrough de aceitação do M8 (docs/SCOPE.md)

Mundo 1.21.1 novo, servidor dedicado, sem cheats, com Apotheosis, EMI, Mekanism, Draconic Evolution, Jade e KubeJS instalados. Um jogador consegue:

110. [ ] Montar uma picareta no Tool Station e aplicar `socketed`. Custa um slot por nível, a ferramenta mostra essa quantidade de sockets vazios, e a contagem de slots livres do painel cai junto.
111. [ ] Encaixar uma gem do Apotheosis num socket. O bônus aplica e o tooltip mostra, nas mesmas linhas que os modifiers e traits do Forgeweave usam.
112. [ ] Subir o nível dessa picareta pelo M7 e gastar o slot ganho em outro nível de `socketed`. O socket novo aceita uma segunda gem, e um bônus de gem que divide grandeza com um trait ou modifier soma em vez de substituir.
113. [ ] Achar uma ferramenta do Forgeweave com affix num baú de loot. O texto renderiza junto das linhas próprias, o bônus aplica, e a ferramenta ainda repara, aceita modifier e sobe de nível.
114. [ ] Com `allowVanillaEnchanting = true`, encantar na mesa do Apotheosis. O encantamento convive com modifiers, sockets e nível, e não gasta slot. Voltar para `false` e a mesa recusa a ferramenta.
115. [ ] Desinstalar o JEI, instalar só o EMI, e abrir: cada categoria do Forgeweave renderiza e cada transfer handler ainda preenche a estação, tudo pela ponte de plugin JEI do EMI. Nenhum plugin nativo ficou para ser escrito.
116. [ ] Montar um tanque energizado numa smeltery, jogar um balde de molten magma como amostra, e alimentar FE de um gerador do Mekanism. A smeltery derrete na temperatura do molten magma e o buffer drena por melt tick. Deixar secar e a fusão para, em vez de ficar lenta. Apertar o overdrive na tela e a fusão dobra ao dobro do custo. A categoria de combustível de smeltery do JEI mostra o tanque e a fórmula de custo.
117. [ ] Trocar o gerador do Mekanism por um do Powah, ou qualquer outra fonte de Forge Energy, e obter o mesmo comportamento. Um segundo tanque com amostra mais quente: só o mais quente paga.
118. [ ] Desligar todos os toggles de compat e recarregar (bloco Q acima). Receitas ausentes, capabilities não anexadas, plugins inertes, mundo carrega. A ferramenta com socket, a com affix, o equipamento evoluído que hospeda módulos e a ferramenta com upgrade de fusão mantêm os componentes intactos e inertes. Ligar de volta e tudo volta.
119. [ ] Salvar, reiniciar, recarregar: conteúdo dos sockets, bônus das gems, estado de affix, estado de módulo, upgrades de fusão e níveis de ferramenta sobrevivem a todos.
120. [ ] ⚠ O livro do guia abre e cobre os sockets, as regras de affix e encantamento, e o tanque energizado. Sem GuideME (M8-6: o livro fica).

## Decisões pendentes

- Decidido em 2026-09-18: as proporções de craft da família de placas (item 95) ficam como estão, inclusive o wire a 3 nuggets.
- Regra de energia do jetpack do Mekanism (item 25): hoje cobra sempre que o portador está no ar com o módulo ligado, não só ao propulsionar. Confirmar.
- Limitação de tier do Mystical Agriculture (item 13): `ITinkerable` não carrega tier por stack, então toda ferramenta do Forgeweave entra no tier 5 com um slot de augment, e nove formas de ferramenta não são augmentáveis. O mantenedor decidiu em 2026-09-18 que não haverá mixin por enquanto; reabrir se incomodar em jogo.
- O toggle do Elementarium é exceção à D-M8-5 (item 3): a regra diz que preset de material não ganha toggle, e este ganhou porque o gerador lê famílias de tag contra as quais não compila. Confirmar a exceção ou tirar o toggle.
- `#forgeweave:large_tools` deixou de ser só de ferramentas: as peças de armadura pesada entraram na tag. Confirmar o nome ou renomear a tag.
- Templates das formas de material em escala de cinza (#1056): a issue sugeria templates dessaturados, mas o algoritmo de recolor escala a saturação de cada pixel em relação à média do doador, e um doador cinza não tem saturação para escalar (48 de 48 saídas mudaram no teste). Os templates ficaram coloridos. Confirmar.
- Reagente do `socketed`: o Sigil of Socketing é o item certo?
- Tag de fluido por metal (`c:molten_<id>`), sem uma tag-mãe `c:molten_metals`: não havia convenção no ecossistema 1.21.1 quando isso foi escrito.
- Os três baldes do Just Dire Things na aba criativa sem o mod (item 60): lacuna deliberada de #1062. Aceitar como defeito conhecido desta tag ou corrigir antes de sair.
- Occultism, `type` aninhado dentro de um `result` de crushing ou miner: o `00_readme.md` do Occultism omite, o codec de dispatch parece exigir. O gerador sempre escreve, então isso só afeta um autor de pack que siga o exemplo.
- Escada de stats dos presets do Mystical Agriculture: desenho do próprio PR #1036, calibrado contra resonite e nitro_crystal, não contra nada upstream.
- #971 (EMI) continua aberta à espera do item 77 num cliente.
- #1032 (upgrades do Just Dire Things) está bloqueada no mod de origem. Se ele ganhar a tag ou o registro que falta, reabrir em vez de abrir issue nova: a pesquisa e as três respostas já estão na D-M8-22.
- Decidido em 2026-09-18: as oito perguntas de superfície de addon de #1061 seguem as recomendações de `docs/research/addon-surface-audit.md`. A implementação está em #1065, #1066 e #1067 e só entra nesta tag se já estiver no master no corte.
