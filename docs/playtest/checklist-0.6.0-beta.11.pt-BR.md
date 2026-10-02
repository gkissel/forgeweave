# Forgeweave 0.6.0-beta.11: checklist de playtest

Esta versão corrige a busca da receita da Tool Forge no JEI. A entrada sem textura passa a corresponder à variante de ferro.

## Tool Forge com Create e JEI

- [ ] Pressionar R sobre a Tool Forge no JEI e conferir que a receita da bancada aparece.
- [ ] Transferir a receita para uma bancada comum e fabricar a Tool Forge com 3 blocos de tijolos carbonizados, 4 blocos de ferro e 1 Tool Station.
- [ ] Repetir com ouro, cobre e cobalto. Conferir a textura do metal no resultado.
- [ ] Conferir que a fabricação funciona sem Create instalado.

## Verificações gerais em servidor dedicado

- [ ] Com JEI, conferir fabricação, montagem, fundição e modificadores das armaduras. Sem JEI, conferir que o jogo inicia.
- [ ] Sem Create e sem os outros mods integrados, conferir que os novos materiais, baldes e receitas não aparecem e que o log não tem erros.
- [ ] Abrir um mundo da beta.10 com ferramentas e armaduras completas. Conferir peças, modificadores, durabilidade, overslime, nível, XP e slots ganhos.
- [ ] Desligar todos os toggles de compatibilidade num mundo com sockets, affixes, módulos, fusion, surgebound, rituais e augments. Reativar e conferir os estados salvos.
- [ ] Executar o roteiro de aceitação do M8 num servidor dedicado novo, com os mods integrados e sem cheats.
- [ ] Obter um perfil Spark do servidor ocioso com armaduras vestidas. Conferir que as estações ociosas não acrescentam trabalho por tick.
- [ ] Subir um nível de ferramenta e conferir texto, cor, som e slot. Conferir os níveis 12 e 42.
- [ ] Abrir o mundo com toolLeveling desligado e conferir que níveis, slots e modificadores permanecem salvos.
- [ ] Revisar as capturas das estações, armaduras e cenas Ponder.

Os itens acima são verificações manuais; os resultados automatizados ficam na descrição do PR.
