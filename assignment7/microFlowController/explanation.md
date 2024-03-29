# MicroFlow Controller

The MAC learning controller in previous section is modified to install microflow rules in the OpenFlow switch reactively to incoming traffic. Thus, the controller will only receive packets for which there are no matching microflow rules in the switch.

## Creating Topology

Following the [openflow-tutorial](https://github.com/mininet/openflow-tutorial/wiki/Router-Exercise) and `mininet/custom/topo-2sw-2host.py`, a custom topology of 3 switches and 5 hosts is created in `t3_topology.py`:

```
  host 1 --- switch 1 --- switch 2 --- switch 3 --- host 5
               |                         |     \
               |                         |      \
               |                         |       \
             host 2                    host 3   host 4
```

The custom topology is a subclass of mininet.topo.Topo. Hosts, switches and links are added by calling methods `addHost()`, `addSwitch()` and `addLink()`.

All names ("h\*", "s\*") are lowercase rather than the uppercases given in the assignment sheet, in order to save my keystrokes.

## Learning Topology

The MAC learning controller only learns the switch ports associated with the source MAC addresses of all packets. It does not really learn the whole topology.

Whenever a new packet comes into a switch, the controller records the mapping from the packet's source MAC address to the switch port that the packet comes from. Next time, if another packet with that same MAC address as its *destination* MAC address comes into that same switch, the output port is immediately known by looking up the recorded mapping. Meanwhile, a flow table entry matching the packet header and specifying the output port will be installed on the switch. Thus, a switch will only send packets to the controller when there are no matching rules installed on that switch.

In this way, both unnecessary floodings and traversing the controller are avoided.

## Behaviors

1. On `h1 ping -c 100 h2`:

    By setting the hard timeout of microflow rules to be 20 seconds, and with a ping interval of 1 second, the ping latencies follows a pattern with a period of 20 as expected, where the first latency is subject to the same distribution with that in the learning switch and the hub (i.e. between a few milliseconds and tens of milliseconds), while all the rest 19 latencies are orders of magnitude lower. The output log for this command is in `output/h1_ping_h2.log`. The periodic pattern is clearly shown in the plot `plots/h1_ping_h2.png`.

    The latency is much lower than that with the learning switch and hub controller. It is because the major part of the latency, which was caused by going through the controller, has been eliminated. The ping traffic only need to travel via switches.
    
2. On `pingall`:
 
    All hosts are pingable as expected. Microflow rules are installed to all involved switches on all hops.

    The output log for this command is in `output/pingall.log`. The microflow rules dumped from each switch (from command `sudo ovs-ofctl dump-flows [switch]`) is in `output/s*_dumpflows.log`.

3. On `iperf h1 h2`:
 
    We repeat each experiment by three times. The output log for this command is in `output/iperf.log`. 

    The results are in `Gbits/sec`, which is by orders of magnitude higher than what we have seen on the previous MAC learning controllers, because once microflow rules are installed, matching traffic will no more need to go through the controller in user space, involving no context switch from kernel space to user space.

