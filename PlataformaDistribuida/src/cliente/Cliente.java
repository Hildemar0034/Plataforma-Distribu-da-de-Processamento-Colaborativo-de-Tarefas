package cliente;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.UUID;

public class Cliente {
    static String ORQ_HOST = "localhost";
    static int ORQ_PORT = 5000;

    public static void main(String[] args) throws Exception {
        Socket s = new Socket(ORQ_HOST, ORQ_PORT);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        Scanner sc = new Scanner(System.in);

        System.out.println("-- Cliente simples --");
        System.out.print("Usuário: ");
        String user = sc.nextLine();
        System.out.print("Senha: ");
        String pass = sc.nextLine();

        out.println("CLIENT_AUTH|" + user + "|" + pass);
        String resp = in.readLine();
        System.out.println("Resposta do servidor: " + resp);

        while (true) {
            System.out.println("Comandos: submit, status, exit");
            System.out.print("> ");
            String cmd = sc.nextLine();
            if (cmd.equalsIgnoreCase("submit")) {
                System.out.print("ID da tarefa (ex: t1): ");
                String tid = sc.nextLine();
                System.out.print("Duração em segundos: ");
                String dur = sc.nextLine();
                out.println("SUBMIT|" + tid + "|" + dur);
                String r = in.readLine();
                System.out.println("Resposta: " + r);
            } else if (cmd.equalsIgnoreCase("status")) {
                System.out.print("ID da tarefa: ");
                String tid = sc.nextLine();
                out.println("STATUS|" + tid);
                String r = in.readLine();
                System.out.println("Resposta: " + r);
            } else if (cmd.equalsIgnoreCase("exit")) {
                s.close();
                break;
            }
        }
    }
}
