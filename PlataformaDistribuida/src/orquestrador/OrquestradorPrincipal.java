package orquestrador;

import modelo.*;
import util.RelogioLamport;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class OrquestradorPrincipal {
    static int TCP_PORT = 5000;
    static String MULTICAST_ADDR = "230.0.0.0";
    static int MULTICAST_PORT = 4446;

    private ServerSocket server;
    private EstadoGlobal estado = new EstadoGlobal();
    private Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private List<String> workerOrder = Collections.synchronizedList(new ArrayList<>());
    private int rrIndex = 0;
    private RelogioLamport lamport = new RelogioLamport();

    class WorkerInfo {
        String id;
        Socket sock;
        PrintWriter out;
        long lastHeartbeat = System.currentTimeMillis();
        int load = 0;
    }

    public OrquestradorPrincipal() throws Exception {
        server = new ServerSocket(TCP_PORT);
        System.out.println("[OrquestradorPrincipal] TCP server started on port " + TCP_PORT);
        // start multicast broadcaster (state sync)
        new Thread(() -> {
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
                DatagramSocket ds = new DatagramSocket();
                while (true) {
                    String msg = "STATE|" + estado.serialize() + "|L=" + lamport.tick();
                    byte[] buf = msg.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MULTICAST_PORT);
                    ds.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // start worker heartbeat watcher
        new Thread(() -> {
            while (true) {
                long now = System.currentTimeMillis();
                List<String> dead = new ArrayList<>();
                for (String wid : workers.keySet()) {
                    WorkerInfo wi = workers.get(wid);
                    if (now - wi.lastHeartbeat > 6000) {
                        dead.add(wid);
                    }
                }
                for (String d : dead) {
                    System.out.println("[OrquestradorPrincipal] Worker " + d + " parece morto. Reatribuindo suas tarefas.");
                    // reatribuir tarefas associados
                    for (String tid : new ArrayList<>(estado.checkpoints.keySet())) {
                        if (d.equals(estado.checkpoints.get(tid)) && !"DONE".equals(estado.status.get(tid))) {
                            estado.checkpoints.remove(tid);
                            estado.status.put(tid, "PENDING");
                            dispatchTask(Tarefa.deserialize(tid)); // tid encoded in Tarefa.id -> we'll store whole serialized form as key sometimes
                        }
                    }
                    WorkerInfo wi = workers.remove(d);
                    workerOrder.remove(d);
                }
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            }
        }).start();

        // accept connections (clients and workers)
        while (true) {
            Socket s = server.accept();
            new Thread(() -> handleConnection(s)).start();
        }
    }

    private void handleConnection(Socket s) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            String line = in.readLine();
            if (line == null) return;
            if (line.startsWith("WORKER_REGISTER|")) {
                String wid = line.split("\\|",2)[1];
                WorkerInfo wi = new WorkerInfo();
                wi.id = wid;
                wi.sock = s;
                wi.out = out;
                workers.put(wid, wi);
                workerOrder.add(wid);
                System.out.println("[OrquestradorPrincipal] Worker registrado: " + wid);
                // listen to worker messages
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("HEARTBEAT|")) {
                        wi.lastHeartbeat = System.currentTimeMillis();
                        // HEAP: HEARTBEAT|workerId|load
                        try {
                            String[] p = line.split("\\|");
                            wi.load = Integer.parseInt(p[2]);
                        } catch (Exception ex) {}
                    } else if (line.startsWith("TASK_DONE|")) {
                        String payload = line.split("\\|",2)[1];
                        // payload: tarefaSerialized
                        Tarefa t = Tarefa.deserialize(payload);
                        estado.status.put(payload, "DONE");
                        estado.checkpoints.remove(payload);
                        System.out.println("[OrquestradorPrincipal] Tarefa concluída: " + payload + " por worker " + wi.id);
                    }
                }
            } else if (line.startsWith("CLIENT_AUTH|")) {
                // protocolo simples: CLIENT_AUTH|username|password
                String[] p = line.split("\\|");
                String user = p[1], pass = p[2];
                // aceita qualquer usuário para demo, token simples = user:token
                String token = user + "-token";
                out.println("AUTH_OK|" + token + "|L=" + lamport.tick());
                System.out.println("[OrquestradorPrincipal] Cliente autenticado: " + user);
                // agora atendimento de comandos do cliente
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("SUBMIT|")) {
                        // SUBMIT|tarefaId|durationSeconds
                        String[] q = line.split("\\|");
                        String tid = q[1];
                        int dur = Integer.parseInt(q[2]);
                        Tarefa t = new Tarefa(tid, user, dur);
                        String key = t.serialize();
                        estado.status.put(key, "PENDING");
                        System.out.println("[OrquestradorPrincipal] Recebeu tarefa: " + key);
                        dispatchTask(t);
                        out.println("SUBMIT_OK|" + tid + "|L=" + lamport.tick());
                    } else if (line.startsWith("STATUS|")) {
                        String tid = line.split("\\|")[1];
                        // we store by serialized key; here naive lookup
                        String found = null;
                        for (String k : estado.status.keySet()) {
                            if (k.startsWith(tid+",")) { found = k; break; }
                        }
                        String st = (found==null)?"UNKNOWN":estado.status.get(found);
                        out.println("STATUS_OK|" + tid + "|" + st + "|L=" + lamport.tick());
                    }
                }
            } else {
                out.println("ERROR|Unrecognized initial message");
                s.close();
            }
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }

    private synchronized void dispatchTask(Tarefa t) {
        if (workerOrder.size()==0) {
            System.out.println("[OrquestradorPrincipal] Nenhum worker disponível -> tarefa pendente: " + t.serialize());
            return;
        }
        // Round-robin
        String wid = workerOrder.get(rrIndex % workerOrder.size());
        rrIndex++;
        WorkerInfo wi = workers.get(wid);
        if (wi==null || wi.out==null) {
            System.out.println("[OrquestradorPrincipal] Worker escolhido não disponível, repondo.");
            estado.status.put(t.serialize(), "PENDING");
            return;
        }
        try {
            wi.out.println("TASK|" + t.serialize() + "|L=" + lamport.tick());
            estado.checkpoints.put(t.serialize(), wid);
            estado.status.put(t.serialize(), "RUNNING");
            System.out.println("[OrquestradorPrincipal] Enviou tarefa " + t.id + " para worker " + wid);
        } catch (Exception e) {
            System.out.println("[OrquestradorPrincipal] Erro ao enviar tarefa: " + e.getMessage());
            estado.status.put(t.serialize(), "PENDING");
        }
    }

    public static void main(String[] args) throws Exception {
        new OrquestradorPrincipal();
    }
}
