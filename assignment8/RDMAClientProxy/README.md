# RDMA Client-side Proxy

Depends on `disni-1.0-jar-with-dependencies.jar` and `disni-1.0-tests.jar`.

## Files

- `RDMAClientProxy.java`: the client-side proxy which communicates with the browser through socket and with the RDMA server through an RDMA client.
- `RDMAClient.java`: the RDMA client used by `RDMAClientProxy.java`. Executes RDMA SEND/RECV/READ operations with the RDMA server.
- `ReadClient.java`: only for reference.

## How to run

Run `ant` to build the project, then navigate to `out/production/RDMAClientProxy/`.

(After you have the server running,) run the proxy: `java -Djava.library.path=/usr/local/lib ch.ethz.acn.RDMAClientProxy -a <IP>`. This starts a HTTP proxy at `localhost:3721` (and also a RDMA SendRecvClient).

