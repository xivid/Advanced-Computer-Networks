/*
 * DiSNI: Direct Storage and Networking Interface
 *
 * Author: Patrick Stuedi <stu@zurich.ibm.com>
 *
 * Copyright (C) 2016, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ch.ethz.acn;

import com.ibm.disni.rdma.RdmaActiveEndpoint;
import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.RdmaEndpointFactory;
import com.ibm.disni.rdma.RdmaServerEndpoint;
import com.ibm.disni.rdma.verbs.*;
import com.ibm.disni.util.GetOpt;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

public class RDMAServer implements RdmaEndpointFactory<RDMAServer.CustomServerEndpoint> {
	private String ipAddress;
	RdmaActiveEndpointGroup<RDMAServer.CustomServerEndpoint> endpointGroup;
	
	public RDMAServer.CustomServerEndpoint createEndpoint(RdmaCmId idPriv, boolean serverSide) throws IOException {
		return new RDMAServer.CustomServerEndpoint(endpointGroup, idPriv, serverSide);
	}	

	private RdmaServerEndpoint<RDMAServer.CustomServerEndpoint> serverEndpoint;

	private void packMsg(ByteBuffer buf, String msg) {
		buf.putInt(msg.length()).put(msg.getBytes(StandardCharsets.US_ASCII));
		buf.clear();
	}

	private String unpackMsg(ByteBuffer buf) {
		buf.clear();
		int len = buf.getInt();
		buf.limit(len + 4);
		return StandardCharsets.US_ASCII.decode(buf).toString(); //.substring(0, len);
	}

	public void run() throws Exception {
		//create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and delivers CQ event to the endpoint.dispatchCqEvent() method.
		endpointGroup = new RdmaActiveEndpointGroup<RDMAServer.CustomServerEndpoint>(1000, false, 128, 4, 128);
		endpointGroup.init(this);
		//create a server endpoint
		serverEndpoint = endpointGroup.createServerEndpoint();

		//we can call bind on a server endpoint, just like we do with sockets
		URI uri = URI.create("rdma://" + ipAddress + ":" + 1919);
		serverEndpoint.bind(uri);
		System.out.println("[RDMAServer] servers bound to address " + uri.toString());

		while (true) {
			//we can accept new connections
			RDMAServer.CustomServerEndpoint clientEndpoint = serverEndpoint.accept();
			//we have previously passed our own endpoint factory to the group, therefore new endpoints will be of type CustomServerEndpoint
			System.out.println("[RDMAServer] client connection accepted");

			//in our custom endpoints we make sure CQ events get stored in a queue, we now query that queue for new CQ events.
			//in this case a new CQ event means we have received data, i.e., a message from the client.
			clientEndpoint.getWcEvents().take();
			System.out.println("[RDMAServer] message received");

			String req = unpackMsg(clientEndpoint.getRecvBuf());
			System.out.println("Message from the client: [" + req + "], length " + req.length());
			String[] tokens = req.split("\\s+");
			// for (String token : tokens) System.out.println(token);

			//in our custom endpoints we have prepared (memory registration and work request creation) some memory buffers beforehand.
			String response = "unknown";
			if (tokens.length > 1 && tokens[0].equals("GET") && tokens[1].startsWith("http://www.rdmawebpage.com")) {
					response = "HTTP/1.1 200 OK\n\n";
			}
			ByteBuffer sendBuf = clientEndpoint.getSendBuf();
			IbvMr dataMr = clientEndpoint.getDataMr();
			sendBuf.putInt(response.length()).put(response.getBytes(StandardCharsets.US_ASCII));
			sendBuf.putLong(dataMr.getAddr());
			sendBuf.putInt(dataMr.getLength());
			sendBuf.putInt(dataMr.getLkey());
			sendBuf.clear();

			System.out.println("Addr: " + dataMr.getAddr() + "Len: " + dataMr.getLength() + "Lkey: " + dataMr.getLkey());

			//let's respond with a message
			clientEndpoint.postSend(clientEndpoint.getWrList_send()).execute().free();
			//when receiving the CQ event we know the message has been sent
			clientEndpoint.getWcEvents().take();
			System.out.println("[RDMAServer] message sent");

			clientEndpoint.close();
			System.out.println("client endpoint closed");
		}
	}

	public void close() throws Exception {
		//close everything
		serverEndpoint.close();
		System.out.println("server endpoint closed");
		endpointGroup.close();
		System.out.println("group closed");
//		System.exit(0);
	}
	
	public void launch(String[] args) throws Exception {
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
		
		this.run();
	}
	
	public static void main(String[] args) throws Exception { 
		RDMAServer rdmaServer = new RDMAServer();
		rdmaServer.launch(args);
	}	
	
	public static class CustomServerEndpoint extends RdmaActiveEndpoint {
		private ByteBuffer buffers[];
		private IbvMr mrlist[];
		private int buffercount = 3;
		private int buffersize = 100;
		
		private ByteBuffer dataBuf;
		private IbvMr dataMr;
		private ByteBuffer sendBuf;
		private IbvMr sendMr;
		private ByteBuffer recvBuf;
		private IbvMr recvMr;	
		
		private LinkedList<IbvSendWR> wrList_send;
		private IbvSge sgeSend;
		private LinkedList<IbvSge> sgeList;
		private IbvSendWR sendWR;
		
		private LinkedList<IbvRecvWR> wrList_recv;
		private IbvSge sgeRecv;
		private LinkedList<IbvSge> sgeListRecv;
		private IbvRecvWR recvWR;
		
		private ArrayBlockingQueue<IbvWC> wcEvents;

		public CustomServerEndpoint(RdmaActiveEndpointGroup<CustomServerEndpoint> endpointGroup, RdmaCmId idPriv, boolean serverSide) throws IOException {	
			super(endpointGroup, idPriv, serverSide);
			this.buffercount = 3;
			this.buffersize = 100;
			buffers = new ByteBuffer[buffercount];
			this.mrlist = new IbvMr[buffercount];
			
			for (int i = 0; i < buffercount; i++){
				buffers[i] = ByteBuffer.allocateDirect(buffersize);
			}
			
			this.wrList_send = new LinkedList<IbvSendWR>();	
			this.sgeSend = new IbvSge();
			this.sgeList = new LinkedList<IbvSge>();
			this.sendWR = new IbvSendWR();
			
			this.wrList_recv = new LinkedList<IbvRecvWR>();	
			this.sgeRecv = new IbvSge();
			this.sgeListRecv = new LinkedList<IbvSge>();
			this.recvWR = new IbvRecvWR();		
			
			this.wcEvents = new ArrayBlockingQueue<IbvWC>(10);
		}
		
		//important: we override the init method to prepare some buffers (memory registration, post recv, etc). 
		//This guarantees that at least one recv operation will be posted at the moment this endpoint is connected. 	
		public void init() throws IOException{
			super.init();
			
			for (int i = 0; i < buffercount; i++){
				mrlist[i] = registerMemory(buffers[i]).execute().free().getMr();
			}
			
			this.dataBuf = buffers[0];
			this.dataMr = mrlist[0];
			this.sendBuf = buffers[1];
			this.sendMr = mrlist[1];
			this.recvBuf = buffers[2];
			this.recvMr = mrlist[2];
			
			sgeSend.setAddr(sendMr.getAddr());
			sgeSend.setLength(sendMr.getLength());
			sgeSend.setLkey(sendMr.getLkey());
			sgeList.add(sgeSend);
			sendWR.setWr_id(2000);
			sendWR.setSg_list(sgeList);
			sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
			sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
			wrList_send.add(sendWR);
			
			sgeRecv.setAddr(recvMr.getAddr());
			sgeRecv.setLength(recvMr.getLength());
			int lkey = recvMr.getLkey();
			sgeRecv.setLkey(lkey);
			sgeListRecv.add(sgeRecv);	
			recvWR.setSg_list(sgeListRecv);
			recvWR.setWr_id(2001);
			wrList_recv.add(recvWR);
			
			System.out.println("[RDMAServer] initiated recv");
			this.postRecv(wrList_recv).execute().free();		
		}

		// Added by Zhifei Yang: deregister memory regions when closing the endpoint
		@Override
		public void close() throws IOException, InterruptedException {
			super.close();
			for (int i = 0; i < buffercount; i++){
				deregisterMemory(mrlist[i]);
			}
		}

		public void dispatchCqEvent(IbvWC wc) throws IOException {
			wcEvents.add(wc);
		}
		
		public ArrayBlockingQueue<IbvWC> getWcEvents() {
			return wcEvents;
		}		

		public LinkedList<IbvSendWR> getWrList_send() {
			return wrList_send;
		}

		public LinkedList<IbvRecvWR> getWrList_recv() {
			return wrList_recv;
		}	
		
		public ByteBuffer getDataBuf() {
			return dataBuf;
		}

		public ByteBuffer getSendBuf() {
			return sendBuf;
		}

		public ByteBuffer getRecvBuf() {
			return recvBuf;
		}

		public IbvSendWR getSendWR() {
			return sendWR;
		}

		public IbvRecvWR getRecvWR() {
			return recvWR;
		}

		public IbvMr getDataMr() {
			return dataMr;
		}

		public IbvMr getSendMr() {
			return sendMr;
		}

		public IbvMr getRecvMr() {
			return recvMr;
		}		
	}
	
}

