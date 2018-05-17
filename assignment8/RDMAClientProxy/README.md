# RDMA Client-side Proxy

Requires `disni-1.0-jar-with-dependencies.jar` and `disni-1.0-tests.jar`.

## How to run

First, run the server: `java -Djava.library.path=/usr/local/lib ch.ethz.acn.SendRecvServer -a <IP>`.

Then run the proxy: `java -Djava.library.path=/usr/local/lib ch.ethz.acn.RDMAClientProxy -a <IP>`. This starts a HTTP proxy at `localhost:3721` (and also a RDMA SendRecvClient).

