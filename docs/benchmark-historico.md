# Histórico de desempenho

Registro das rodadas completas, sempre com o mesmo roteiro, para comparar versões ao longo do tempo e pegar regressões. O método, os perfis com JFR e a explicação de cada otimização ficam em [benchmark.md](benchmark.md); aqui ficam só os números de cada rodada, a mais nova primeiro.

## Como rodar e registrar

```bash
# no HEAD e numa worktree da versão de comparação (a última rodada registrada ou o commit suspeito)
git worktree add --detach ../grox-base <commit>
for d in . ../grox-base; do (cd $d && ./gradlew :bench:installDist :bench:livros:installDist :sample-plain:installDist :sample-app:installDist); done
./gradlew -p bench/livros-grails bootJar
# com a máquina ociosa (load average < 1), cerca de 30 min
BASE=../grox-base bench/rodada.sh > rodada.log
bench/resumo.py rodada.log      # tabelas em Markdown para colar abaixo
```

- **O que a rodada mede:** a app de livros (`bench/livros`) no Grails 8, no Grox compat e no Grox puro (`sample-plain`), em texto, GSP com layout e JSON. São duas ordens intercaladas (ida e volta), com 64 conexões (20 s de aquecimento, 10 s de medição) e depois com 1 conexão. Depois, a `/usuario/index` do sample-app com login (4 consultas, tags, layout), com 1 e 64 conexões e o tempo de CPU por requisição. Com `BASE`, a versão de comparação entra intercalada com o HEAD em todas as medições.
- **Sempre em A/B:** a máquina e o JDK mudam entre rodadas, e os números absolutos de datas diferentes não se comparam. Por isso cada rodada mede também a anterior (ou o commit suspeito) nas mesmas condições.
- **O que conta como regressão:** o HEAD pior que a base nas duas ordens. Na prática, mais de 10% no p50 com 1 conexão (a métrica mais estável) ou mais de 15% em req/s com 64 conexões, que oscila mais. Se aparecer, abra um perfil com JFR (ver [benchmark.md](benchmark.md#o-que-o-profiling-mostrou-jfr)) antes de concluir.
- **Aquecimento:** o `run-logado.sh` aquece a página com 64 conexões (`JIT=60` s) antes de medir. Até 2026-11-27, ele media com 1 conexão logo depois de 30 s de aquecimento com 1 conexão, e o JIT ainda estava compilando. Nessa condição, o p50 da `/usuario/index` dava de 5 a 6 ms; aquecida, a mesma página fica em ~2 ms.
- **Quando rodar:** antes de fechar uma série de otimizações, depois de mudanças no caminho de toda requisição (Netty, segurança, despacho, sessão, GSP, tags, consultas) e ao atualizar Groovy, Netty, Hibernate ou o JDK.

## Linha do tempo

Números do HEAD de cada rodada. Compare só a tendência; a diferença entre rodadas vem também da máquina.

| Data | Commit | Texto compat (req/s, 64) | GSP compat (req/s, 64) | GSP compat (p50, 1) | `/usuario/index` (req/s, 1) |
|---|---|---:|---:|---:|---:|
| 2026-11-27 | `ab23e32` | 29.840 a 31.990 | 4.207 a 6.763 | 0,72 a 0,74 ms | 154 a 159 |
| 2026-11-27 (base) | `c7ccb7d` | 20.780 a 21.402 | 1.977 a 2.745 | 0,82 a 0,83 ms | 91 a 92 |
| 2026-10-10 | (ver [benchmark.md](benchmark.md#grails-x-grox-compat-x-grox-puro-2026-10-10)) | 17.893 a 18.474 | 1.676 a 2.193 | 0,88 a 1,09 ms | |

## 2026-11-27: `ab23e32` x `c7ccb7d`

- **Comparação:** HEAD `ab23e32` (herança de domain classes) contra a base `c7ccb7d`, o último commit antes da série de otimizações de 2026-11-26 e 27: tags em `@CompileStatic`, menos religação de call sites, HQL gerado em cache, construtor das domains e o caminho dos controllers em `@CompileStatic`.
- **Ambiente:** JDK 27, 8 CPUs, 21 GB, Linux 7.3. Máquina ociosa no início (load 0,97). Durante a medição, o load ficou em 10 a 14, que é o próprio gerador com 64 conexões mais o servidor. Grails 8.0.0, com o jar do `bootJar`. Sem erros e sem respostas fora de 2xx.

| req/s com 64 conexões (ida · volta) | Grails 8 | compat base | compat HEAD | puro base | puro HEAD |
|---|---:|---:|---:|---:|---:|
| Texto (`/ola/mundo`) | 10.082 · 8.344 | 21.402 · 20.780 | 31.990 · 29.840 | 23.716 · 24.383 | 30.666 · 28.729 |
| GSP com layout (`/livros`) | 1.582 · 1.476 | 1.977 · 2.745 | 6.763 · 4.207 | 3.682 · 2.106 | 6.364 · 4.184 |
| JSON (`/api/livros/7`) | 10.416 · 10.377 | 14.352 · 12.766 | 12.737 · 14.173 | 14.539 · 13.328 | 14.679 · 14.817 |

| p50 com 1 conexão (ms) | Grails 8 | compat base | compat HEAD | puro base | puro HEAD |
|---|---:|---:|---:|---:|---:|
| Texto | 0,22 · 0,21 | 0,33 · 0,32 | 0,31 · 0,31 | 0,31 · 0,30 | 0,30 · 0,30 |
| GSP com layout | 1,52 · 2,08 | 0,83 · 0,82 | 0,72 · 0,74 | 0,79 · 0,81 | 0,72 · 0,67 |
| JSON | 0,38 · 0,39 | 0,56 · 0,44 | 0,48 · 0,45 | 0,36 · 0,36 | 0,36 · 0,35 |

| | Grails 8 | compat base | compat HEAD | puro base | puro HEAD |
|---|---:|---:|---:|---:|---:|
| Até responder, com a JVM (s) | 16,7 · 18,8 | 10,6 · 8,4 | 8,2 · 9,1 | 8,7 · 8,0 | 8,5 · 7,9 |
| RSS no fim (MB) | 1.464 · 1.436 | 1.329 · 1.347 | 2.081 · 1.619 | 1.567 · 1.488 | 2.001 · 1.625 |

`/usuario/index` do sample-app com login (base, HEAD, HEAD, base). Atenção: as linhas com 1 conexão foram medidas antes do aquecimento com 64 conexões (ver acima) e incluem o JIT compilando; servem só para comparar base e HEAD da mesma rodada:

| | Conexões | req/s | p50 (ms) | CPU ms/req |
|---|---:|---:|---:|---:|
| base `c7ccb7d` | 1 | 91 · 92 | 11,05 · 10,83 | 18,71 · 20,55 |
| HEAD `ab23e32` | 1 | 159 · 154 | 6,19 · 6,32 | 11,74 · 13,66 |
| base `c7ccb7d` | 64 | 1.976 · 1.693 | 23,15 · 26,03 | 3,49 · 3,78 |
| HEAD `ab23e32` | 64 | 3.213 · 3.416 | 13,98 · 14,47 | 1,97 · 1,93 |

**O que a rodada mostrou:**

- **`/usuario/index`, a página mais próxima de uma tela real:** de 91 para 157 req/s com 1 conexão (+70%) e de 10,9 para 6,3 ms no p50. Com 64 conexões, de 1.835 para 3.315 req/s (+80%) e de 3,6 para 1,95 ms de CPU por requisição. É o efeito somado das tags, do HQL em cache, da religação e do caminho dos controllers. Com 1 conexão, a CPU por requisição inclui o JIT e o GC de fundo, divididos por poucas requisições; com 64 conexões ela é mais próxima do custo real.
- **Texto no compat:** +45% com 64 conexões. O caminho dos controllers em `@CompileStatic` deixou o compat no nível do puro, e a camada Grails não custa mais vazão em texto.
- **GSP com 64 conexões:** de 2 a 2,7 mil para 4,2 a 6,8 mil req/s, no compat e no puro. A oscilação entre as ordens é grande. No p50 com 1 conexão, que é mais estável, o ganho é de 10% a 15%.
- **JSON:** sem diferença fora do ruído, porque essa rota não passava pelas partes otimizadas.
- **Contra o Grails 8:** o compat faz cerca de 3,4× em texto, de 2,7× a 4,6× na GSP e 1,3× em JSON, e sobe na metade do tempo. Com 1 conexão, o Grails segue mais rápido em texto (0,21 contra 0,31 ms, por causa dos saltos de thread do Grox) e empata com o puro em JSON.
- **RSS:** o HEAD termina com mais memória residente na primeira ordem (2,0 a 2,1 GB, contra 1,3 a 1,6 GB). O heap padrão é 1/4 da RAM, e o HEAD atende o dobro de requisições na mesma janela, então o heap cresce mais antes de um GC completo. Não é uma medida de consumo. Para memória, ver a seção de memória em [benchmark.md](benchmark.md#memória-grails-x-grox-2026-10-31) (`bench/run-mem.sh`).
