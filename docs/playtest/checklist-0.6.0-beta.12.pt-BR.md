# Checklist 0.6.0-beta.12, 2026-10-07

Esta versão completa receitas de fusão dos materiais existentes e adiciona os três alloys de
crafting do Mekanism e o Hellforged Metal do Neo Vitae. A auditoria está em
[compat-material-audit-2026-10-07.md](../research/compat-material-audit-2026-10-07.md).

## Verificação automatizada

- Build e testes unitários: registrados no PR desta versão.
- GameTests sem os mods opcionais: registrados no PR desta versão.
- Datagen em diretório isolado, sem os mods de `run/mods`.
- Capturas reais das telas pelo screenshot harness, antes da publicação.
- Inicialização de servidor dedicado com Mekanism, Neo Vitae e os mods disponíveis no ambiente.

## Jogabilidade para conferir no servidor dedicado

- [ ] Mekanism: fundir osmium, tin, lead e uranium em ore, raw e raw block nos quatro cores.
- [ ] Mekanism: dust, dirty dust, clump, shard e crystal rendem 144 mB em qualquer core.
- [ ] Mekanism: bronze usa a proporção 3:1; blocos e nuggets podem ser fundidos e moldados.
- [ ] Part Builder: construir peças com Infused, Reinforced e Atomic Alloy; conferir capacidade
  de energia, carga e efeitos de armadura.
- [ ] Immersive Engineering: fundir aluminum, silver, nickel e Hop Graphite; moldar o ingot de
  Hop Graphite. Invar e Constantan melhoram as estatísticas dos ingredientes.
- [ ] Neo Vitae: dungeon ore e raw demonite recebem o bônus; ingot, dust, fragment e gravel não.
  Moldar Hellforged e conferir Vitae Siphon e Magic Protection II.
- [ ] JEI: todas as novas linhas aparecem nas categorias existentes, sem resultados ausentes.
- [ ] Forgeweave sozinho: não aparecem materiais condicionados, receitas quebradas ou buckets
  de materiais cujos provedores estão ausentes.
- [ ] Mundo da versão anterior: peças, modificadores, durabilidade, energia e níveis continuam
  carregando; conferir um conjunto completo de armadura.
- [ ] Todos os toggles `compat` desligados: socket, affix, módulos, fusão, ritual e augment ficam
  inativos e voltam a funcionar quando os toggles são religados.
- [ ] Nivelar uma ferramenta e conferir mensagem, som, cor, níveis 12 e 42 e slot concedido.
- [ ] `toolLeveling = false`: níveis e slots existentes permanecem salvos.
- [ ] Spark: armadura equipada não acrescenta trabalho por tick no servidor ocioso.
- [ ] Fazer o percurso de aceitação de M8 em um mundo novo com os mods integrados.

As linhas manuais ficam abertas até uma sessão de jogo confirmar cada comportamento.
