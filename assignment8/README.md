# Assignment 8: RDMA

The code is fairly straight forward and is largely a combination of the SendRecvServer/Client and ReadServer/Client example code. See `README.md` in both folders about how to run them.

We have a `RDMAClientProxy` class which is responsible for accepting incoming connections from the browser. It filters out requests not for the www.rdmawebpage.com domain (returning a `HTTP 404 Not Found` response). If the request is for the www.rdmawebpage.com domain, then it passes the request to the `request` method of the `RDMAClient` class. The `request` method carries out a single request for a single resource, for example `index.html` or `network.png`. The `RDMAClientProxy` waits for the `request` method to return within 3 seconds; on timeout of (or exception thrown by) the `request` method, it will be stopped and the proxy returns a `HTTP 504 Gateway Timeout` response directly to the browser.

The `RDMAClient` first establishes a connection with the RDMA server (modelled by the `RDMAServer` class), then does an RDMA `SEND` to pass the request to the server. During initialisation, the `RDMAServer` has preloaded both resources into separate buffers. When the RDMA server receives the request, it returns the normal `HTTP 200 OK` response header, plus the location information of the buffer holding the resource that has been requested (using RDMA `SEND`). The message format is "msg length (4 bytes) + msg body (`msg length` bytes of request/response) + location information (16 bytes, only applicable for server response)".

When the `RDMAClient` receives the response headers and the remote buffer information, it initiates an RDMA `READ`, which will copy the contents of the remote buffer into the local data buffer. The `RDMAClient` then merges the response headers and the resource data into one complete response. Before returning the complete response to the `RDMAClientProxy`, the `RDMAClient` does a final RDMA `SEND` message to the server so that the server knows the client has completed the RDMA `READ` and can therefore close the connection. The `RDMAClient` then returns the complete response to the `RDMAClientProxy`, which then returns the response to the browser.

---
Jack Clark, Zhifei Yang

ETH Zurich
