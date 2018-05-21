The code is fairly straight forward and is largely a combination of the SendRecvServer/Client and ReadServer/Client example code.

We have a ClientProxy class which is responsible for accepting incoming connections from the browser. It filters out requests not for the rdmawebpage.com domain (returning a 404). If the request is for the rdmawebpage.com domain, then it passes the request to the request method of the RDMAClient class. The request method carries out a single request for a single resource, for example index.html or network.png. 

The RDMAClient first establishes a connection with the RDMAServer (modelled by the RDMAServer class), then does an RDMA SEND to pass the request to the server. During initialisation, the RDMAServer has preloaded both resources into separate buffers. When the server receives the request, it returns the normal 200 OK response header, plus the location information of the buffer holding the resource that has been requested (using RDMA SEND).

When the RDMAClient receives the response headers and the remote buffer information, it initiates an RDMA READ, which will copy the contents of the remote buffer into the local data buffer. The RDMAClient then merges the response headers and the resource data into one complete response. Before returning the complete response to the RDMAClientProxy, the RDMAClient does a final RDMA SEND message to the server so that the server knows the client has completed the RDMA READ and can therefore close the connection. The RDMAClient then returns the complete response to the RDMAClientProxy, which then returns the response to the browser.


