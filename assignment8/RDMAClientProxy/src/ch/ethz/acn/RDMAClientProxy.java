package ch.ethz.acn;

import com.ibm.disni.util.GetOpt;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class RDMAClientProxy {

    private static final String msg404 = "HTTP/1.1 404 Not Found\n" +
            "Content-Type: text/html; charset=UTF-8\n" +
            "\n<!DOCTYPE html>\n" +
            "<html lang=en>\n" +
            "  <meta charset=utf-8>\n" +
            "  <title>Error 404 (Not Found)</title>\n" +
            "  <h1>404 Not Found</h1>\n" +
            "  <p>The requested URL was not found.</p>\n";

    private static final String msg504 = "HTTP/1.1 504 Gateway Timeout\n" +
            "Content-Type: text/html; charset=UTF-8\n" +
            "\n<!DOCTYPE html>\n" +
            "<html lang=en>\n" +
            "  <meta charset=utf-8>\n" +
            "  <title>Error 504 (Gateway Timeout)</title>\n" +
            "  <h1>504 Gateway Timeout</h1>\n" +
            "  <p>The proxy did not receive a timely response from server.</p>\n";

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
        ExecutorService executor = Executors.newCachedThreadPool();

        try (ServerSocket proxyServerSocket = new ServerSocket(3721)) {
            while (true) {
                Socket clientSocket = proxyServerSocket.accept();
                BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
                DataOutputStream clientOs = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                // only read the first line
                String input = clientIn.readLine();

                if (input != null) {
                    System.out.println(clientSocket + " says: " + input);
                    String[] tokens = input.split("\\s+");
                    if (tokens.length > 1 && tokens[0].equals("GET") &&
                            (tokens[1].equals("http://www.rdmawebpage.com") ||  // the root, or resources under the domain
                                    tokens[1].startsWith("http://www.rdmawebpage.com/"))) {

                        System.out.println(clientSocket + " redirecting to RDMA server");
                        Future<ByteBuffer> future = executor.submit(new Callable<ByteBuffer>() {
                           public ByteBuffer call() throws Exception {
                               return rdmaClient.request(input);
                           }
                        });
                        try {
                            ByteBuffer response = future.get(3, TimeUnit.SECONDS);
                            System.out.println(clientSocket + " rdma full response length " + response.limit());
                            clientOs.write(response.array());
                            clientOs.flush();
                        } catch (Exception e) {
                            System.out.println(clientSocket + " communication failed between proxy and server, replying 504");
                            clientOut.print(msg504);
                            clientOut.flush();
                        }
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
