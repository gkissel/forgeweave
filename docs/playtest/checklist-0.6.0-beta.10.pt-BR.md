# Forgeweave 0.6.0-beta.10: checklist de playtest

Esta versão adiciona zinco, latão, liga de andesito e quartzo rosa do Create como materiais de ferramentas e armaduras. Os valores de cada material estão em `docs/SCOPE.md`, na seção Create materials.

## Create 6.0.10 e Minecraft 1.21.1

- [ ] No Part Builder, fabricar cabeças, cabos, placas de armadura e malha com liga de andesito e quartzo rosa.
- [ ] Derreter lingotes, pepitas e blocos de zinco e latão com lava. Fundir peças de ferramentas, placas de armadura, malha e os lingotes do próprio Create.
- [ ] Montar e reparar ferramentas e armaduras dos quatro materiais usando o item correspondente do Create.
- [ ] Conferir os acabamentos de metal, pedra e cristal nas peças e ferramentas, incluindo arco e besta.
- [ ] Conferir Precision acima e abaixo de 75% de durabilidade, Industrial em pedra e terra, e Focused contra um alvo com armadura.
- [ ] Vestir quatro peças de cada material. Conferir 20% de economia de durabilidade com zinco, 12% de velocidade com latão, 40% de resistência a repulsão com liga de andesito e 16% de proteção mágica com quartzo rosa.
- [ ] Quebrar uma peça vestida e conferir que seu bônus deixa de funcionar.
- [ ] Aplicar o modificador Goggles a um capacete de cada material e conferir as informações do Create.

## Verificações gerais em servidor dedicado

- [ ] Com JEI, conferir fabricação, montagem, fundição e modificadores das armaduras. Sem JEI, conferir que o jogo inicia.
- [ ] Sem Create e sem os outros mods integrados, conferir que os novos materiais, baldes e receitas não aparecem e que o log não tem erros.
- [ ] Abrir um mundo da beta.9 com ferramentas e armaduras completas. Conferir peças, modificadores, durabilidade, overslime, nível, XP e slots ganhos.
- [ ] Desligar todos os toggles de compatibilidade num mundo com sockets, affixes, módulos, fusion, surgebound, rituais e augments. Reativar e conferir os estados salvos.
- [ ] Executar o roteiro de aceitação do M8 num servidor dedicado novo, com os mods integrados e sem cheats.
- [ ] Obter um perfil Spark do servidor ocioso com armaduras vestidas. Conferir que as estações ociosas não acrescentam trabalho por tick.
- [ ] Subir um nível de ferramenta e conferir texto, cor, som e slot. Conferir os níveis 12 e 42.
- [ ] Abrir o mundo com toolLeveling desligado e conferir que níveis, slots e modificadores permanecem salvos.
- [ ] Revisar as capturas das estações, armaduras e cenas Ponder.

Os itens acima são verificações manuais; os resultados automatizados ficam na descrição do PR.
