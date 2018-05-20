package ch.ethz.acn;

import com.ibm.disni.util.GetOpt;

import java.io.BufferedReader;
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
            "  <p>The requested URL was not found.</p>\n";

    private String ipAddress;

    private RDMAClient rdmaClient;

    public RDMAClientProxy(String ipAddress) { this.ipAddress = ipAddress; }

    public static void main(String args[]) {
        String ipAddress = null;
        String[] _args = args;
        if (args.length < 1) {
            System.exit(0);
        } else if (args[0].equals(RDMAClient.class.getCanonicalName())) {
            _args = new String[args.length - 1];
            for (int i = 0; i < _args.length; i++) {
                _args[i] = args[i + 1];
            }
        }

        GetOpt go = new GetOpt(_args, "a:");
        go.optErr = true;
        int ch = -1;

        while ((ch = go.getopt()) != GetOpt.optEOF) {
            if ((char) ch == 'a') {
                ipAddress = go.optArgGet();
            }
        }

        if (ipAddress == null) {
            System.out.println("Please specify RDMA server IP address using -a option.");
            System.exit(0);
        }

        new RDMAClientProxy(ipAddress).run();
    }

    public void run() {
        System.out.println("Starting RDMA client");
        initRDMAClient();

        System.out.println("Starting HTTP proxy on port 3721");
        initHTTPProxy();

        try {
            rdmaClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initRDMAClient() {
        rdmaClient = new RDMAClient(ipAddress);
        try {
            rdmaClient.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

                        System.out.println(clientSocket + " redirecting to RDMA server");
                        String response = rdmaClient.request(input);
                        System.out.println(clientSocket + " rdma server response: [" + response + "]");
                        clientOut.print(response);
                        clientOut.flush();
                    } else {
                        System.out.println(clientSocket + " handling at proxy");
                        clientOut.print(msg404);
                        clientOut.flush();
                    }
                    clientSocket.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
