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
Hub POX controller

A simple controller that acts like a Ethernet hub, where all network traffic is flooded
on all ports except the port that it arrived in.

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

class HubController ():
  """
  A Hub POX Controller.
  """

  def __init__ (self, connection):
    # Keep track of the connection to the switch
    self.connection = connection

    # Bind PacketIn event listener, the handler is _handle_PacketIn()
    connection.addListeners(self)


  def _handle_PacketIn (self, event):
    """
    PacketIn event handler
    """
    packet_parsed = event.parsed
    if not packet_parsed.parsed:
      log.warning("%s Ignoring incomplete packet" % self.connection)
      return

    packet_in = event.ofp

    log.debug("%s Got new packet (%s (%s) -> %s), flooding..." % (self.connection, str(packet_parsed.src), str(packet_in.in_port), str(packet_parsed.dst)))

    self.resend_packet(packet_in, of.OFPP_ALL)


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
  HubController(event.connection)


def launch ():
  """
  Listen for connections from switches
  """
  
  log.warn("Hub Controller launched")
  
  # Bind ConnectionUp event listener
  core.openflow.addListenerByName("ConnectionUp", _connection_up)


