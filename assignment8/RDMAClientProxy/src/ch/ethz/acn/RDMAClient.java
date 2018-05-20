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
import com.ibm.disni.rdma.verbs.*;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;


public class RDMAClient implements RdmaEndpointFactory<RDMAClient.CustomClientEndpoint> {
	private String ipAddress;
	RdmaActiveEndpointGroup<RDMAClient.CustomClientEndpoint> endpointGroup;


	public RDMAClient(String ipAddress) { this.ipAddress = ipAddress; }

	public RDMAClient.CustomClientEndpoint createEndpoint(RdmaCmId idPriv, boolean serverSide) throws IOException {
		return new CustomClientEndpoint(endpointGroup, idPriv, serverSide);
	}

	public void run() throws Exception {
		//create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and delivers CQ event to the endpoint.dispatchCqEvent() method.
		endpointGroup = new RdmaActiveEndpointGroup<RDMAClient.CustomClientEndpoint>(1000, false, 128, 4, 128);
		endpointGroup.init(this);
	}

	private void packMsg(ByteBuffer buf, String msg) {
		buf.putInt(msg.length()).put(msg.getBytes(StandardCharsets.US_ASCII));
		buf.clear();
	}

	private String unpackMsg(ByteBuffer buf) {
		buf.clear();
		int len = buf.getInt();
		buf.limit(len + 4);
		return StandardCharsets.US_ASCII.decode(buf).toString();
	}

	private String unpackData(ByteBuffer buf, int len) {
		buf.clear();
		buf.limit(len);
		return StandardCharsets.US_ASCII.decode(buf).toString();
	}


	public String request(String request) throws Exception {
		// TODO: on communication failure return HTTP 504 (Gateway Time-out)

		//we have passed our own endpoint factory to the group, therefore new endpoints will be of type CustomClientEndpoint
		//let's create a new client endpoint
		RDMAClient.CustomClientEndpoint endpoint = endpointGroup.createEndpoint();

		//connect to the server
		endpoint.connect(URI.create("rdma://" + ipAddress + ":" + 1919));
		System.out.println("[RDMAClient] client channel set up ");

		//in our custom endpoints we have prepared (memory registration and work request creation) some memory
		//buffers beforehand.
		//let's send one of those buffers out using a send operation
		System.out.println("[RDMAClient] message to be sent: [" + request + "], length " + request.length());

		packMsg(endpoint.getSendBuf(), request);

		SVCPostSend postSend = endpoint.postSend(endpoint.getWrList_send());
		postSend.getWrMod(0).setWr_id(4444);
		postSend.execute().free();
		//in our custom endpoints we make sure CQ events get stored in a queue, we now query that queue for new CQ events.
		//in this case a new CQ event means we have sent data, i.e., the message has been sent to the server
		IbvWC wc = endpoint.getWcEvents().take();
		System.out.println("[RDMAClient] message sent, wr_id " + wc.getWr_id());
		//in this case a new CQ event means we have received data
		endpoint.getWcEvents().take();
		System.out.println("[RDMAClient] message received");

		//the response should be received in this buffer, let's print it
		String response = unpackMsg(endpoint.getRecvBuf());
		System.out.println("[RDMAClient] message from the server: [" + response + "], length " + response.length());

		// get static content info
		ByteBuffer recvBuf = endpoint.getRecvBuf();
		ByteBuffer sendBuf = endpoint.getSendBuf();
		recvBuf.limit(recvBuf.limit()+16);
		long addr = recvBuf.getLong();
		int len = recvBuf.getInt();
		int lkey = recvBuf.getInt();
		recvBuf.clear();
		sendBuf.clear();
		System.out.println("Addr: " + addr + "Len: " + len + "Lkey: " + lkey);

		// Use RDMA READ to get HTML content directly from server buffer
		IbvSendWR sendWR = endpoint.getSendWR();
		sendWR.setWr_id(4445);
		sendWR.setOpcode(IbvSendWR.IBV_WR_RDMA_READ);
		sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
		sendWR.getRdma().setRemote_addr(addr);
		sendWR.getRdma().setRkey(lkey);
		postSend = endpoint.postSend(endpoint.getWrList_send());

		// READ to dataMr
		/*IbvMr dataMr = endpoint.getDataMr();
		SVCPostSend.SgeMod sgeMod = postSend.getWrMod(0).getSgeMod(0);
		assert dataMr.getLength() >= len;
		sgeMod.setAddr(dataMr.getAddr());
		sgeMod.setLength(dataMr.getLength());
		sgeMod.setLkey(dataMr.getLkey());*/

		postSend.execute().free();
		System.out.println("[RDMAClient] RDMA Read");
		endpoint.getWcEvents().take();

		ByteBuffer dataBuf = endpoint.getDataBuf();

		System.out.print("data: [");
		for (int i = 0; i < len; ++i) {
			System.out.print((char) sendBuf.get());
		}
		sendBuf.flip();
		System.out.println("]");

		response += unpackData(sendBuf, len); // dataBuf.asCharBuffer().toString();
		System.out.println("[RDMAClient] Full Response: [" + response + "]");

		// Send a final message to terminate connection
		//sendWR.setWr_id(4446);
		//sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
		//sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
		//sendWR.getRdma().setRemote_addr(addr);
		//sendWR.getRdma().setRkey(lkey);

		//endpoint.postSend(endpoint.getWrList_send()).execute().free();
		//System.out.println("[RDMAClient] Termination Message Sent");
		
		// close the customClientEndpoint
		endpoint.close();

		return response;
	}

	public void close() throws Exception {
		//close everything
		endpointGroup.close();
		System.out.println("group closed");
	}

	
	public static class CustomClientEndpoint extends RdmaActiveEndpoint {
		private ByteBuffer buffers[];
		private IbvMr mrlist[];
		private int buffercount;
		private int buffersize;
		
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

		public CustomClientEndpoint(RdmaActiveEndpointGroup<CustomClientEndpoint> endpointGroup, RdmaCmId idPriv, boolean serverSide) throws IOException {	
			super(endpointGroup, idPriv, serverSide);
			this.buffercount = 3;
			this.buffersize = 206;
			buffers = new ByteBuffer[buffercount];
			this.mrlist = new IbvMr[buffercount];

			buffers[0] = ByteBuffer.allocateDirect(3000);  // allocate large enough datamr to hold png
			for (int i = 1; i < buffercount; i++){
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
			
			dataBuf.clear();

			sgeSend.setAddr(sendMr.getAddr());
			sgeSend.setLength(sendMr.getLength());
			sgeSend.setLkey(sendMr.getLkey());
			sgeList.add(sgeSend);
			sendWR.setWr_id(2000);  // why do we set it to 2000 here and reset it to 4444 in RDMAClient::request()?
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
			
			System.out.println("[RDMAClient] initiated recv");
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

		public IbvMr getDataMr() { return dataMr; }
	}
	
}

