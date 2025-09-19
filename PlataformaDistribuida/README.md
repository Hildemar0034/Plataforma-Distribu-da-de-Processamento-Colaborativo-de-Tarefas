Plataforma Distribuída de Processamento Colaborativo de Tarefas (versão simplificada)
===================================================================================

O projeto é uma implementação em Java (linha de comando) que simula:
- Orquestrador Principal (porta TCP 5000)
- Orquestrador Backup (escuta multicast; assume papel de principal se o principal sumir)
- Workers (conectam-se ao orquestrador e executam tarefas simuladas)
- Clientes (autenticação simples e submissão de tarefas)

Como compilar
-------------
Abra um terminal na pasta `src` dentro do projeto e rode:
  javac -d ../bin $(find . -name "*.java")

Como rodar (exemplo com múltiplos terminais)
--------------------------------------------
1) Rodar orquestrador principal:
   (Terminal A)
   cd PlataformaDistribuida
   java -cp bin orquestrador.OrquestradorPrincipal

2) Rodar orquestrador backup:
   (Terminal B)
   java -cp bin orquestrador.OrquestradorBackup

3) Rodar 3 workers (cada um em seu terminal):
   (Terminal C)
   java -cp bin worker.Worker worker1
   java -cp bin worker.Worker worker2
   java -cp bin worker.Worker worker3

4) Rodar um cliente e submeter tarefas:
   (Terminal D)
   java -cp bin cliente.Cliente

Observações importantes
-----------------------
- Comunicação Cliente↔Orquestrador: TCP (porta 5000).
- Comunicação Orquestrador↔Backup: UDP Multicast (230.0.0.0:4446).
- Balanceamento: Round-Robin (implementado).
- Lamport clock: há uma classe simples RelogioLamport e ela é usada ao enviar/receber eventos.
- Logs são impressos no terminal de cada processo (padrão).
- Para simular falha de worker: mate o processo do worker (CTRL+C). O orquestrador detecta ausência via heartbeat e reatribui a tarefa.
- Para simular falha do orquestrador principal: mate o processo do orquestrador principal. O backup detecta ausência do multicast e assume como principal (bind no porto TCP 5000). Note que se o principal ainda estiver usando a porta, o takeover pode falhar; nesse ambiente local de múltiplos terminais funciona como simulação.

Cenários de falha e comportamento esperado
------------------------------------------
1) Orquestrador Principal cai
   O backup assume automaticamente como principal e passa a escutar na porta TCP 5000.
   O cliente precisa sair (Exit) e reconectar com login e senha → depois disso pode continuar submetendo tarefas.
   Limitação: pode haver perda da última atualização de estado se o principal cair antes de enviar o snapshot ao backup.

2) Backup cai (mas o principal continua)
   O sistema continua funcionando normalmente (o backup não é essencial enquanto o principal estiver ativo).
   Se o principal cair depois, não haverá quem assuma → o sistema para totalmente.

3) Tanto o principal quanto o backup caem
Não há nenhum orquestrador ativo.
Workers e clientes ficam sem coordenação.
Não é possível puxar status nem enviar novas tarefas até que ao menos um orquestrador volte a rodar.

4) Worker cai
O principal detecta ausência do heartbeat.
A tarefa atribuída ao worker é redistribuída para outro worker ativo.
Quando o worker morto voltar a rodar, ele se registra de novo normalmente.

5) Cliente cai
Nenhum impacto no sistema global.
As tarefas submetidas permanecem registradas no orquestrador.
Ao reconectar, o cliente pode usar Status para ver as tarefas pendentes/concluídas (se ainda estiverem no estado global).


Estrutura do projeto
--------------------
/PlataformaDistribuida
  /src
    /cliente (Cliente.java)
    /orquestrador (OrquestradorPrincipal.java, OrquestradorBackup.java)
    /worker (Worker.java)
    /modelo (Tarefa.java, Usuario.java, EstadoGlobal.java)
    /util (RelogioLamport.java)
  /bin  (gerado após compilação)
  README.md


