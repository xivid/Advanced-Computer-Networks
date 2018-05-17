package ch.ethz.acn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class RDMAClientProxy {

    private static final String msg404 = "HTTP/1.1 404 Not Found\n" +
            "Content-Type: text/html; charset=UTF-8\n" +
            "\n<!DOCTYPE html>\n" +
            "<html lang=en>\n" +
            "  <meta charset=utf-8>\n" +
            "  <title>Error 404 (Not Found)</title>\n" +
            "  <h1>404 Not Found</h1>\n" +
            "  <p>The requested URL was not found.</p>";
    private static final String png = "\u5089\u474e\u0a0d\u0a1a\u0000\u0d00\u4849\u5244\u0000\u0100\u0000\u0100\u0608\u0000\u1f00\uc415\u0089\u0000\u490a\u4144\u7854\u639c\u0100\u0000\u0005\u0d01\u2d0a\u00b4\u0000\u4900\u4e45\uae44\u6042\u8200";


    public static void main(String args[]) {
        new RDMAClientProxy().run();
    }

    public void run() {
        System.out.println("Starting RDMA client");
        initRDMAClient();

        System.out.println("Starting HTTP proxy on port 3721");
        initHTTPProxy();
    }

    private void initRDMAClient() {

    }

    private void initHTTPProxy() {
        try (ServerSocket proxyServerSocket = new ServerSocket(3721)) {
            while (true) {
                Socket clientSocket = proxyServerSocket.accept();
                BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
                // only read the first line
                String input = clientIn.readLine();

                if (input != null) {
                    System.out.println(clientSocket + " says: " + input);
                    String[] tokens = input.split("\\s+");
                    if (tokens.length > 1 && tokens[0].equals("GET") &&
                            (tokens[1].equals("http://www.rdmawebpage.com") ||  // the root, or resources under the domain
                                    tokens[1].startsWith("http://www.rdmawebpage.com/"))) {
                        if (tokens[1].equals("http://www.rdmawebpage.com") ||
                                tokens[1].equals("http://www.rdmawebpage.com/") ||
                                tokens[1].equals("http://www.rdmawebpage.com/index.html")) {
                            clientOut.println("HTTP/1.1 200 OK\n\n<h1>Welcome to the RDMA world</h1><img src=\"network.png\" alt=\"RDMA Read Image Missing!\">");  // TODO forward to RDMA server
                        }
                        else if (tokens[1].equals("http://www.rdmawebpage.com/network.png")) {
                            clientOut.println("HTTP/1.1 200 OK\nContent-Type: image/png\n\n" + png + "\n");
                        }
                        else {
                            clientOut.println(msg404);
                        }
                    } else {
                        clientOut.println(msg404);
                    }
                    clientSocket.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}