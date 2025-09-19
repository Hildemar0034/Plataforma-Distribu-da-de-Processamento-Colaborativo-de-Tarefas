package modelo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EstadoGlobal {
    // mapa tarefaId -> workerId
    public Map<String, String> checkpoints = new ConcurrentHashMap<>();
    // mapa tarefaId -> status
    public Map<String, String> status = new ConcurrentHashMap<>();

    public String serialize() {
        // formato simples: task1:workerA:status;task2:workerB:status
        StringBuilder sb = new StringBuilder();
        for (String tid : checkpoints.keySet()) {
            if (sb.length()>0) sb.append(";");
            sb.append(tid).append(":").append(checkpoints.get(tid)).append(":").append(status.getOrDefault(tid, "UNKNOWN"));
        }
        return sb.toString();
    }

    public static EstadoGlobal deserialize(String s) {
        EstadoGlobal eg = new EstadoGlobal();
        if (s==null || s.isEmpty()) return eg;
        String[] parts = s.split(";");
        for (String p : parts) {
            String[] q = p.split(":");
            if (q.length>=3) {
                eg.checkpoints.put(q[0], q[1]);
                eg.status.put(q[0], q[2]);
            }
        }
        return eg;
    }
}
