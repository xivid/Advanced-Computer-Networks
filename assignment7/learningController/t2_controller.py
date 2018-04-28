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
Learning POX controller

The learning algorithm consists of the following steps:

a) Look at the incoming port number and MAC source of the incoming packet and store 
it in a dictionary, where the key is the MAC address and the value is the port number. 

If a packet with destination to this MAC arrives later, then the port to
forward the packet to is known.

b) If the dictionary already contains an entry with the MAC destination of the incoming
packet, then the port to forward to is known.

c) Otherwise, the packet is flooded to all ports except for the port from which the packet 
came in.

Reference: OpenFlow POX tutorial (~/pox/pox/misc/of_tutorial.py on mininet 2.2.0 VM)
"""


# Import some POX stuff
from pox.core import core                     # Main POX object
import pox.openflow.libopenflow_01 as of      # OpenFlow 1.0 library
import pox.lib.packet as pkt                  # Packet parsing/construction
from pox.lib.addresses import EthAddr, IPAddr # Address types
import pox.lib.util as poxutil                # Various util functions
import pox.lib.revent as revent               # Event library
import pox.lib.recoco as recoco               # Multitasking library

# Create a logger for this component
log = core.getLogger()

class LearningController ():
  """
  A Learning POX Controller.
  """

  def __init__ (self, connection):
    # Keep track of the connection to the switch
    self.connection = connection

    # Bind PacketIn event listener, the handler is _handle_PacketIn()
    connection.addListeners(self)

    # Dictionary to keep track of the correspondences between MACs and ports
    self.mac_to_port = dict()


  def _handle_PacketIn (self, event):
    """
    PacketIn event handler
    """
    packet_parsed = event.parsed
    if not packet_parsed.parsed:
      log.warning("[_handle_PacketIn] Ignoring incomplete packet")
      return

    packet_in = event.ofp

    log.debug("[_handle_PacketIn] Got new packet (%s (%s) -> %s)" % (str(packet_parsed.src), str(packet_in.in_port), str(packet_parsed.dst)))

    self.learn_and_resend(packet_parsed, packet_in)


  def learn_and_resend(self, packet_parsed, packet_in):
    """
    The learning controller behavior
    """
    src_port = packet_in.in_port
    src_mac = str(packet_parsed.src)
    dst_mac = str(packet_parsed.dst)
    
    log.debug("[learn_and_resend] Processing packet %s (%d) -> %s (?)..." % (src_mac, src_port, dst_mac))

    # Learn the port associated with the source MAC from this packet
    # (whether this is an entry insertion or update does not matter) 
    self.mac_to_port[src_mac] = src_port
    
    # if destination port is unknown, have to flood (send out from all ports but the input port)
    dst_port = of.OFPP_ALL

    if dst_mac in self.mac_to_port:  # if destination port is known, resend to it directly
      dst_port = self.mac_to_port[dst_mac]
      log.debug("[learn_and_resend] Dst port known: %d :)" % dst_port)
    else:
      log.debug("[learn_and_resend] Dst port UNknown, flood :(")

    self.resend_packet(packet_in, dst_port)

     
  def resend_packet(self, packet_in, out_port):
    """
    [Copied from of_tutorial.py]
    Instructs the switch to resend a packet that it had sent to us out from out_port.
    "packet_in" is the ofp_packet_in object the switch had sent to the
    controller due to a table-miss.
    """
    msg = of.ofp_packet_out()
    msg.data = packet_in

    # Add an action to send to the specified port
    action = of.ofp_action_output(port = out_port)
    msg.actions.append(action)

    # Send the message to switch via the kept connection to switch
    self.connection.send(msg)


def _connection_up (event):
  """
  Starts the component
  """
  log.debug("Connected and controlling switch: %s." % event.connection)
  LearningController(event.connection)


def launch ():
  """
  Listen for connections from switches
  """
  
  log.warn("Learning Controller launched")
  
  # Bind ConnectionUp event listener
  core.openflow.addListenerByName("ConnectionUp", _connection_up)


