# Forgeweave 0.6.0-beta.6 — checklist de playtest

Minecraft 1.21.1, NeoForge. Esta versão corrige as cores dos materiais e aplica os acabamentos escolhidos à espada, picareta e warmace no conjunto Forged. Outras ferramentas continuam com o tint comum. O pacote Legacy mantém o desenho e o tint anteriores.

Verificação automatizada: o build e o teste de cobertura dos 1.944 sprites passaram. O cliente abriu, criou o atlas e capturou espada, warmace, espada com modifiers e espada quebrada sem textura faltando. O roteiro geral de screenshots foi interrompido numa cena Ponder que deixou de avançar. Ele também registrou falhas nas verificações de disparo do longbow e da crossbow e numa cena da torneira; essas partes não foram alteradas por esta release. Os itens abaixo ainda pedem playtest manual.

## Ferramentas e cores

- [ ] Montar uma espada com lâmina de Vibranium, cabo de madeira e guarda de pedra. Conferir verde na lâmina, fibras finas no cabo e pedra rugosa na guarda.
- [ ] Montar uma picareta de liga Unobtainium–Vibranium. Conferir as duas cores nos veios da cabeça.
- [ ] Montar uma warmace de Draconium Core e conferir o acabamento pulsante.
- [ ] Conferir cristal facetado, metal escovado, orgânico com trama e slime gel nas três ferramentas.
- [ ] Quebrar uma das três ferramentas. A camada quebrada deve continuar visível.
- [ ] Aplicar um modifier com textura. O desenho do modifier deve aparecer sobre o acabamento.
- [ ] Ativar o pacote Legacy. Espada e picareta devem mostrar seus desenhos Legacy e o tint normal; warmace conserva seu desenho próprio.
- [ ] Abrir um mundo da beta.5 com ferramentas salvas. Materiais e modificadores devem permanecer iguais.

## Verificações gerais da release

- [ ] Repetir o teste de JEI com e sem os mods integrados. Sem eles, não deve haver receitas fantasmas.
- [ ] Desligar as opções de compatibilidade e carregar ferramentas e armaduras com estado salvo; religar e conferir o estado.
- [ ] Testar uma ferramenta com nível 12 e outra com nível 42; conferir nome, XP, slot e som.
- [ ] Carregar um mundo da release anterior com armadura e ferramenta niveladas.
- [ ] Conferir as capturas de telas, ferramentas, armaduras e cenas Ponder em `build/screenshots/`.
