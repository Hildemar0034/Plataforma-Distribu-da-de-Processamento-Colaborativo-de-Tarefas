package orquestrador;

import modelo.EstadoGlobal;
import util.RelogioLamport;

import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorBackup {
    static String MULTICAST_ADDR = "230.0.0.0";
    static int MULTICAST_PORT = 4446;
    static int TCP_PORT = 5000;

    private volatile EstadoGlobal estado = new EstadoGlobal();
    private volatile long lastStateTime = System.currentTimeMillis();
    private RelogioLamport lamport = new RelogioLamport();
    private volatile boolean isPrimary = false;

    public OrquestradorBackup() throws Exception {
        // listener multicast
        new Thread(() -> {
            try {
                MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
                InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
                socket.joinGroup(group);
                byte[] buf = new byte[8192];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    if (msg.startsWith("STATE|")) {
                        String[] p = msg.split("\\|",3);
                        String stateSer = p[1];
                        estado = EstadoGlobal.deserialize(stateSer);
                        lastStateTime = System.currentTimeMillis();
                        // parse lamport part optionally
                        System.out.println("[Backup] Estado recebido via multicast: " + stateSer);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // watcher that promotes to primary if no multicast
        new Thread(() -> {
            while (true) {
                if (!isPrimary && (System.currentTimeMillis() - lastStateTime) > 6000) {
                    System.out.println("[Backup] Não recebeu estado do principal recentemente. Assumindo papel de Orquestrador Principal...");
                    try {
                        becomePrimary();
                    } catch (Exception e) { e.printStackTrace(); }
                }
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            }
        }).start();
    }

    private void becomePrimary() throws Exception {
        isPrimary = true;
        // start a minimal TCP server to accept clients/workers (very similar to principal)
        OrquestradorPrincipalLikeServer srv = new OrquestradorPrincipalLikeServer(estado, lamport);
        srv.start();
    }

    public static void main(String[] args) throws Exception {
        new OrquestradorBackup();
        // keep alive
        Thread.sleep(Long.MAX_VALUE);
    }
}

// minimal server class inside same file for simplicity
class OrquestradorPrincipalLikeServer {
    private int port = OrquestradorBackup.TCP_PORT;
    private EstadoGlobal estado;
    private RelogioLamport lamport;

    public OrquestradorPrincipalLikeServer(EstadoGlobal estado, RelogioLamport lamport) {
        this.estado = estado;
        this.lamport = lamport;
    }

    public void start() throws Exception {
        java.net.ServerSocket server = new java.net.ServerSocket(port);
        System.out.println("[Backup-as-Primary] TCP server started on port " + port);
        while (true) {
            java.net.Socket s = server.accept();
            new Thread(() -> handle(s)).start();
        }
    }

    private void handle(java.net.Socket s) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            String line = in.readLine();
            if (line==null) return;
            if (line.startsWith("WORKER_REGISTER|")) {
                String wid = line.split("\\|",2)[1];
                System.out.println("[Backup-as-Primary] Worker registered: " + wid);
                // worker messages ignored in minimal takeover (simple ACK)
                out.println("REGISTERED|"+wid);
            } else if (line.startsWith("CLIENT_AUTH|")) {
                String[] p = line.split("\\|");
                String user = p[1];
                String token = user + "-token";
                out.println("AUTH_OK|" + token + "|L=" + lamport.tick());
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("SUBMIT|")) {
                        String[] q = line.split("\\|");
                        String tid = q[1];
                        int dur = Integer.parseInt(q[2]);
                        // naive: accept and mark pending (no workers managed here)
                        String key = tid + ",backup," + dur + ",PENDING";
                        estado.status.put(key, "PENDING");
                        out.println("SUBMIT_OK|" + tid + "|L=" + lamport.tick());
                        System.out.println("[Backup-as-Primary] Received task " + key);
                    } else if (line.startsWith("STATUS|")) {
                        String tid = line.split("\\|")[1];
                        String found = null;
                        for (String k : estado.status.keySet()) {
                            if (k.startsWith(tid+",")) { found = k; break; }
                        }
                        String st = (found==null)?"UNKNOWN":estado.status.get(found);
                        out.println("STATUS_OK|" + tid + "|" + st + "|L=" + lamport.tick());
                    }
                }
            } else {
                out.println("ERROR|Unrecognized");
            }
        } catch (IOException e) {}
    }
}
