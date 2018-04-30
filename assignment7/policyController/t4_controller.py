# Copyright 2018 Zhifei Yang
# Legi: 17941998
# D-INFK, ETH Zurich
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Policy POX controller

[Topology]
Custom Topology II

[Policy]
For all traffic from H1 to H4 or from H2 to H4, they must traverse switch 3.
For all other traffic (including from H4 to H1 or H2), the default paths will be the shortest paths.

Reference: [pox/pox/forwarding/l2_multi.py] and [pox/pox/forwarding/l3_learning.py]
"""


# Import some POX stuff
from pox.core import core                     # Main POX object
import pox.openflow.libopenflow_01 as of      # OpenFlow 1.0 library
import pox.lib.packet as pkt                  # Packet parsing/construction
from pox.lib.packet.ethernet import ethernet, ETHER_BROADCAST
from pox.lib.packet.ipv4 import ipv4
from pox.lib.packet.icmp import icmp
from pox.lib.packet.arp import arp
from pox.lib.addresses import EthAddr, IPAddr # Address types
import pox.lib.util as poxutil                # Various util functions
import pox.lib.revent as revent               # Event library
import pox.lib.recoco as recoco               # Multitasking library
from pox.openflow.discovery import Discovery
import pox.openflow.discovery as discovery
from pox.lib.revent import *
from collections import defaultdict
from pox.lib.util import dpid_to_str
from Queue import Queue

# Create a logger for this component
log = core.getLogger()


class Topology:
  """
    Keeps track of the learned topology.
  """
  def __init__ (self):
    self.switch_links = defaultdict(lambda:defaultdict(lambda:None))  # switch adjacency graph
    self.host_to_switch = defaultdict(lambda:None)  # host ip to directly connected (switch, port)
    self.arp_table = defaultdict(lambda:None)  # host ip to host mac
    self.switches = set()
    self.non_top_switches = set()
    self.top_switch = None

  def add_link (self, link):
    sw1 = link.dpid1
    sw2 = link.dpid2
    pt1 = link.port1
    pt2 = link.port2
    self.switch_links[sw1][sw2] = pt1
    self.switches.add(sw1)
    self.switches.add(sw2)
    log.debug("[Topology] adding link {dpid1 %s, port1 %d} -> {dpid2 %s, port2 %d}" % (dpid_to_str(sw1), pt1, dpid_to_str(sw2), pt2))


  def remove_link (self, link):
    sw1 = link.dpid1
    sw2 = link.dpid2
    pt1 = link.port1
    pt2 = link.port2

    if sw2 in self.switch_links[sw1]:
      del self.switch_links[sw1][sw2]
      if len(self.switch_links[sw1]) == 0:
        del self.switch_links[sw1]
        self.switches.remove(sw1)
        for host, (sw, port) in self.host_to_switch.items():
          if sw == sw1:
            del self.host_to_switch[host]

    log.debug("[Topology] removing link {dpid1 %s, port1 %d} -> {dpid2 %s, port2 %d}" % (dpid_to_str(sw1), pt1, dpid_to_str(sw2), pt2))


  def add_new_host (self, host_ip, switch_dpid, in_port, host_mac):
    if host_ip in self.host_to_switch:
      return False
    
    self.host_to_switch[host_ip] = (switch_dpid, in_port)
    self.arp_table[host_ip] = host_mac
    log.debug("[Topology] adding host (ip %s, mac %s) attached to switch %s port %d" % (str(host_ip), str(host_mac), dpid_to_str(switch_dpid), in_port))

    self.non_top_switches.add(switch_dpid)  # some host is directly connected to it => not a top switch
    if self.top_switch == None and len(self.switches - self.non_top_switches) == 1:
      # the only one switch that has no directly connected hosts has been found
      self.top_switch = list(self.switches - self.non_top_switches)[0]
      log.debug("[Topology] Top switch found: %s" % dpid_to_str(self.top_switch, True))

    return True


  def get_host_mac (self, host_ip):
    return self.arp_table[host_ip]


  def print_graph(self):
    print "Switches:"
    for sw1 in self.switch_links:
      print dpid_to_str(sw1, True) + " ->",
      for sw2 in self.switch_links[sw1]:
        print "%s (%d)" % (dpid_to_str(sw2, True), self.switch_links[sw1][sw2]),
      print
    if len(self.switch_links) == 0:
      print "None"

    print "Hosts:"
    for host_ip, (dpid, port)  in self.host_to_switch.iteritems():
      print "%s (%s) -> %s (%d)" % (host_ip, self.arp_table[host_ip], dpid_to_str(dpid), port)
    if len(self.host_to_switch) == 0:
      print "None"


  def get_next_hop (self, current_switch, src_host, dst_host):
    """
    Returns the out port to next hop in the shortest path from this switch to the destination host.
    """

    # if destination host is not learned yet
    if dst_host not in self.host_to_switch:
      return None

    end_switch, end_port = self.host_to_switch[dst_host]
    # if destination host is directly connected to this switch
    if end_switch == current_switch:
      return end_port

    # Policy: h1 --> h4 and h2 --> h4 must traverse switch 3 
    # (we recognise the switch with no directly-connected hosts as "switch 3")
    if dst_host == "10.0.0.4" and (src_host == "10.0.0.1" or src_host == "10.0.0.2"):
      assert src_host in self.host_to_switch
      # if current_switch the source host's directly connected switch (switch 1)
      # and the top switch (switch 3) has been recognised
      if self.host_to_switch[src_host][0] == current_switch and self.top_switch is not None: 
        if self.top_switch in self.switch_links[current_switch]:  # by checking this, if link between s1 and s3 is down, the normal shortest path can still hold the traffic
          out_port = self.switch_links[current_switch][self.top_switch]
          log.info("[get_next_hop] Implementing special policy (%s -SW3-> %s) on %s (%d)" % (src_host, dst_host, dpid_to_str(current_switch, True), out_port))
          return out_port

    # run BFS to find shortest path from current_switch to end_switch
    q = Queue()
    path_to = defaultdict(lambda:None)
    path_to[current_switch] = current_switch
    q.put(current_switch)
    while not q.empty():
      u = q.get()
      for v in self.switch_links[u]:
        if path_to[v] is None:
          path_to[v] = u
          q.put(v)

    # if connected to end_switch, get switch next to current_switch on the shortest path
    if path_to[end_switch]:
      next_hop = end_switch
      while path_to[next_hop] != current_switch:
        next_hop = path_to[next_hop]
      return self.switch_links[current_switch][next_hop]

    # finally not found
    return None


class Switch:

  def __init__ (self, connection, topology):
    self.connection = connection
    self.topology = topology
    self.dpid = connection.dpid
    self.mac_to_port = dict()
    connection.addListeners(self)
    log.info("[Switch %s] New switch up" % dpid_to_str(self.dpid, True)) 

  
  def _handle_PacketIn (self, event):
    """
    PacketIn event handler
    """
    packet = event.parsed
    if not packet.parsed:
      log.warning("[Switch %s] Ignoring incomplete packet" % dpid_to_str(self.dpid, True))
      return
    packet_in = event.ofp

    src_port = packet_in.in_port
    src_mac = str(packet.src)
    dst_mac = str(packet.dst)
    
    # Learn the port associated with the source MAC from this packet
    self.mac_to_port[src_mac] = src_port

    log.debug("[Switch %s] Got new packet (%s (%d) -> %s), type: %s" % (dpid_to_str(self.dpid, True), src_mac, src_port, dst_mac, "ARP" if packet.type == packet.ARP_TYPE else "IP" if packet.type == packet.IP_TYPE else "LLDP" if packet.type == packet.LLDP_TYPE else "others"))

    # if ARP
    if packet.type == packet.ARP_TYPE and packet.payload.opcode == arp.REQUEST:
      self.handle_arp (packet, packet_in)

    # elif IP
    elif packet.type == packet.IP_TYPE:
      self.handle_ip (packet, packet_in)

    # drop all other types of packets
    else:
      return
  
       
  def handle_arp (self, packet, packet_in):
    """
    Proxy the ARP replies by the controller rather than flooding.
    """
    host_ip = packet.payload.protosrc
    query_ip = packet.payload.protodst
    host_mac = packet.src
    log.debug("[Switch %s] ARP REQUEST: I am %s (%s), who has %s?" % (dpid_to_str(self.dpid, True), str(host_ip), str(host_mac), str(query_ip)))

    # add host (if it is a new host) to the topology
    if self.topology.add_new_host(host_ip, self.dpid, packet_in.in_port, host_mac):  
      log.info("[Switch %s] Added new host directly linked to me: %s (%s) through port %d" % (dpid_to_str(self.dpid, True), str(host_ip), str(host_mac), packet_in.in_port))
      self.topology.print_graph()

    # if we know the answer, construct and send an arp reply
    query_mac = self.topology.get_host_mac(query_ip)
    if query_mac:  # not None
      arp_reply = arp()
      arp_reply.hwsrc = query_mac
      arp_reply.hwdst = host_mac
      arp_reply.opcode = arp.REPLY
      arp_reply.protosrc = query_ip
      arp_reply.protodst = host_ip
      ether = ethernet()
      ether.type = ethernet.ARP_TYPE
      ether.dst = host_mac
      ether.src = query_mac
      ether.payload = arp_reply
      log.debug("[Switch %s] Answering ARP from %s: %s is at %s" % (dpid_to_str(self.dpid, True), str(host_mac), str(query_ip), str(query_mac)))
      msg = of.ofp_packet_out()
      msg.data = ether.pack()
      msg.actions.append(of.ofp_action_output(port = of.OFPP_IN_PORT))
      msg.in_port = packet_in.in_port
      self.connection.send(msg)


  def handle_ip (self, packet, packet_in):
    in_port = packet_in.in_port
    src_mac = packet.src
    dst_mac = packet.dst

    payload = packet.payload
    dst_ip = payload.dstip
    src_ip = payload.srcip
    protocol = payload.protocol

    # add host (if it is a new host) to the topology
    if self.topology.add_new_host(src_ip, self.dpid, in_port, src_mac):  
      log.info("[Switch %s] Added new host directly linked to me: %s (%s) through port %d" % (dpid_to_str(self.dpid, True), str(src_ip), str(src_mac), in_port))
      self.topology.print_graph()

    # get the port to next hop (if unreachable, gets None)
    next_hop = self.topology.get_next_hop(self.dpid, src_ip, dst_ip)
    log.info("[Switch %s] IP packet from %s to %s, protocol: %s, next hop port: %s" % (dpid_to_str(self.dpid, True), src_ip, dst_ip, "ICMP" if protocol == payload.ICMP_PROTOCOL else "TCP" if protocol == payload.TCP_PROTOCOL else "UDP" if protocol == payload.UDP_PROTOCOL else "UNKNOWN", next_hop))

    # send to next hop (only if there exists a path from to dst)
    if next_hop:
      log.debug("[Switch %s] Redirecting IP packet to next hop out of port %d" % (dpid_to_str(self.dpid, True), next_hop))
      # self.resend_packet(packet_in, next_hop)

      log.info("[Switch %s] Installing flow" % (dpid_to_str(self.dpid, True)))
      msg = of.ofp_flow_mod()

      # Match the src/dst mac&ip of received packet and the source port
      msg.match = of.ofp_match(dl_type = pkt.ethernet.IP_TYPE, dl_src = src_mac, dl_dst = dst_mac, nw_src = src_ip, nw_dst = dst_ip, in_port = in_port)

      # Set idle_timeout (timeout for expiry with no traffic)
      # and hard_timeout (force expiry)
      msg.idle_timeout = 15
      msg.hard_timeout = 20 

      # Set buffer id to that of packet_in
      msg.buffer_id = packet_in.buffer_id
 
      # Add an output action, and send
      action = of.ofp_action_output(port = next_hop)
      msg.actions.append(action)

      self.connection.send(msg)

        
  def resend_packet(self, packet_in, out_port):
    msg = of.ofp_packet_out()
    msg.data = packet_in
    action = of.ofp_action_output(port = out_port)
    msg.actions.append(action)
    self.connection.send(msg)


class PolicyController (EventMixin):
 
  def __init__ (self):
    self.topology = Topology() 
    # Listen to dependencies
    def startup ():
      core.openflow.addListeners(self, priority=0)
      core.openflow_discovery.addListeners(self)
    core.call_when_ready(startup, ('openflow','openflow_discovery'))


  def _handle_LinkEvent(self, event):
    if event.added:
        self.topology.add_link(event.link)
    elif event.removed:
      self.topology.remove_link(event.link)
    else:
      return

    log.debug("[_handle_LinkEvent] Updated topology")
    self.topology.print_graph()


  def _handle_ConnectionUp (self, event):
    log.debug("[_handle_ConnectionUp] New connection up: %s, dpid %s." % (event.connection, dpid_to_str(event.dpid, True)))
    Switch(event.connection, self.topology)


def launch ():
  core.registerNew(PolicyController) 
  discovery.launch()


