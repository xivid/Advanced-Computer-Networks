# RDMA Server

Depends on `disni-1.0-jar-with-dependencies.jar` and `disni-1.0-tests.jar`.

## Files

- `RDMAServer.java`: the RDMA server. Accepts and replys to requests from RDMA clients by RDMA SEND/RECV, and returns HTML and PNG contents by RDMA READ.

## How to run

Run `ant` to build the project, then navigate to `out/production/RDMAServer/`.

Run the server: `java -Djava.library.path=/usr/local/lib ch.ethz.acn.RDMAServer -a <IP>`.

Now you can run the RDMAProxyClient in another folder.

