package modelo;

public class Tarefa {
    public String id;
    public String clientId;
    public int durationSeconds; // simula tempo de execução
    public String status; // PENDING, RUNNING, DONE, FAILED

    public Tarefa(String id, String clientId, int durationSeconds) {
        this.id = id;
        this.clientId = clientId;
        this.durationSeconds = durationSeconds;
        this.status = "PENDING";
    }

    public String serialize() {
        return id + "," + clientId + "," + durationSeconds + "," + status;
    }

    public static Tarefa deserialize(String s) {
        String[] p = s.split(",", -1);
        Tarefa t = new Tarefa(p[0], p[1], Integer.parseInt(p[2]));
        t.status = p[3];
        return t;
    }
}
