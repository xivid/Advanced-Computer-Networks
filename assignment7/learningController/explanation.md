# Learning Controller

A MAC learning controller that learns output ports corresponding to MAC addresses to avoid flooding on known ports. Still, all traffic arriving at the switches will be directed to this controller.

## Creating Topology

Following the [openflow-tutorial](https://github.com/mininet/openflow-tutorial/wiki/Router-Exercise) and `mininet/custom/topo-2sw-2host.py`, a custom topology of 3 switches and 5 hosts is created in `t2_topology.py`:

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

Whenever a new packet comes into a switch, the controller records the mapping from the packet's source MAC address to the switch port that the packet comes from. Next time, if another packet with that same MAC address as its *destination* MAC address comes into that same switch, the output port is immediately known by looking up the recorded mapping. In this way, unnecessary floodings are avoided.

## Behaviors

1. On `h1 ping -c 100 h2`:

    The minimum/average/maximum/standard deviation of round-trip time is 2.070/26.718/51.489/14.970 ms, respectively. The output log for this command is in `output/h1_ping_h2.log`.

    The latency is close to that with the hub controller. It is because the latency is mainly caused by going through the controller. Although floodings are eliminated, the traffic still needs to pass the controller.
    
    Only host 1, host 2 and switch 1 observe the whole ping traffic, as is shown in the first half of tcpdump results `output/h*_tcpdump.log` (use `sudo tcpdump -ttttnnr [filename]` to read) and controller logs `output/controller.log` (first half). It is because the ping traffic is no longer flooded since the exact output port has been known.

2. On `h1 ping -c 100 h5` and `h1 ping -c 100 h4`:

    The minimum/average/maximum/standard deviation of round-trip time is 5.586/29.518/57.290/14.362 ms to `h5`, and 4.434/27.210/53.970/14.525 ms to `h4`. The output log for this command is in `output/h1_ping_h5.log` and `output/h1_ping_h4.log`.

    The latencies are essentially the same because the two traffics go through the same path, only ending up outputted at different ports of switch 3.

    Hosts 2 and 3 do not observe the ping traffic, as is shown in the second half of tcpdump results `output/h*_tcpdump.log` (use `sudo tcpdump -ttttnnr [filename]` to read) and controller logs `output/controller.log` (second half). The controller no longer send traffic to unrelated ports since it has learned the MAC-port association.

3. On `pingall`:
 
    All hosts are pingable as expected.

    The output log for this command is in `output/pingall.log`.

4. On `iperf`:
 
    We run `iperf h1 h2` to represent the throughput between two hosts connected to the same switch, and `iperf h1 h5` to represent the throughput between hosts connected to different switches. We repeat each experiment by three times. The output log for this command is in `output/iperf.log`. 

    The results are in `Mbits/sec`, which is by orders of magnitude lower than what we will see on the controllers installing MicroFlow rules, because all traffic need to go through the controller, which is in user space (much slower than in kernel space).

    The results also show that the average TCP bandwidth between h1 and h2 (`[24.8 Mbits/sec, 27.0 Mbits/sec]`) is roughly three times that between h1 and h5 (`[7.12 Mbits/sec, 7.98 Mbits/sec]`). This is because the latter's path is two hops longer than the former's path, taking the saturated controller more time to redirect the traffic. The bandwidth between h1 and h2 is notably higher than that with the hub controller, because there is no more flooding, mitigating the bottlenck on the controller.

