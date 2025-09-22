package worker;

import modelo.Tarefa;
import util.RelogioLamport;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class Worker {
    static String ORQ_HOST = "localhost";
    static int ORQ_PORT = 5000;
    private String id;
    private RelogioLamport lamport = new RelogioLamport();

    public Worker(String id) { this.id = id; }

    public void start() throws Exception {
        Socket s = new Socket(ORQ_HOST, ORQ_PORT);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        // register
        out.println("WORKER_REGISTER|" + id);
        // heartbeat thread
        new Thread(() -> {
            int load = 0;
            while (true) {
                try {
                    out.println("HEARTBEAT|" + id + "|" + load);
                    Thread.sleep(2000);
                } catch (Exception e) { break; }
            }
        }).start();

        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("TASK|")) {
                String payload = line.split("\\|",2)[1];
                // payload includes serialized tarefa and optionally lamport
                String tarefaSer = payload.split("\\|L=")[0];
                Tarefa t = Tarefa.deserialize(tarefaSer);
                System.out.println("[Worker " + id + "] Received task: " + tarefaSer);
                // execute (simulate)
                try {
                    t.status = "RUNNING";
                    Thread.sleep(t.durationSeconds * 1000L);
                    t.status = "DONE";
                    out.println("TASK_DONE|" + tarefaSer);
                    System.out.println("[Worker " + id + "] Completed task: " + t.id);
                } catch (InterruptedException e) {
                    t.status = "FAILED";
                    out.println("TASK_DONE|" + tarefaSer);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String wid = (args.length>0)?args[0]:"worker-" + UUID.randomUUID().toString().substring(0,5);
        Worker w = new Worker(wid);
        w.start();
    }
}
