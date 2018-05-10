package ch.ethz.acn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class RDMAClientProxy {

    public static void main(String args[]) {
        System.out.println("RDMAClientProxy starting on port 3721");
        try (ServerSocket proxyServerSocket = new ServerSocket(3721)) {
            while (true) {
                new ProxyClient(proxyServerSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ProxyClient extends Thread {
        private Socket clientSocket;
        private final String info = "HTTP/1.1 404 Not Found\n" +
                "Content-Type: text/html; charset=UTF-8\n" +
                "\n<!DOCTYPE html>\n" +
                "<html lang=en>\n" +
                "  <meta charset=utf-8>\n" +
                "  <title>Error 404 (Not Found)</title>\n" +
                "  <h1>404 Not Found</h1>\n" +
                "  <p>The requested URL was not found.</p>\n";

        public ProxyClient(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            System.out.println("New client connected: " + clientSocket);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
                // only read the first line
                String input = in.readLine();

                if (input != null) {
                    System.out.println(clientSocket + " says: " + input);
                    String[] tokens = input.split("\\s+");
                    if (tokens.length > 1 && tokens[0].equals("GET") && tokens[1].startsWith("http://www.rdmawebpage.com")) {
                        out.println("HTTP/1.1 200 OK");  // TODO forward to RDMA server
                    } else {
                        out.println(info);
                    }
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Client disconnected: " + clientSocket);
        }
    }
}
