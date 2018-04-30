# Hub Controller

A simple Ethernet hub where all network traffic is flooded on all ports except the port that it arrived in.

## Creating Topology

Following the [openflow-tutorial](https://github.com/mininet/openflow-tutorial/wiki/Router-Exercise) and `mininet/custom/topo-2sw-2host.py`, a custom topology of 3 switches and 5 hosts is created in `t1_topology.py`:

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

The hub controller does not learn the topology at all. It just floods all traffic.

## Behaviors

1. On `h1 ping -c 100 h2`:

    The minimum/average/maximum/standard deviation of round-trip time is 2.257/27.419/52.710/14.666 ms, respectively. The output log for this command is in `output/h1_ping_h2.log`.

    Although the standard deviation is a bit high and the latency is not very stable, it should be explained as normal fluctuations of flooding.

    All hosts and all switches observe the ping traffic because of flooding, as is shown in the first half of tcpdump results `output/h*_tcpdump.log` (use `sudo tcpdump -ttttnnr [filename]` to read) and controller logs `output/controller.log` (first half).

2. On `h1 ping -c 100 h5`:

    The minimum/average/maximum/standard deviation of round-trip time is 3.990/27.644/51.938/14.612 ms, respectively. The output log for this command is in `output/h1_ping_h5.log`.

    The latency is almost the same as the previous result. This is because the latency is dominated by communication with controller. The tiny impact on latency by a few more hops is therefore hidden.

    All hosts and all switches observe the ping traffic because of flooding, as is shown in the second half of tcpdump results `output/h*_tcpdump.log` (use `sudo tcpdump -ttttnnr [filename]` to read) and controller logs `output/controller.log` (second half).

3. On `pingall`:
 
    All hosts are pingable as expected.

    The output log for this command is in `output/pingall.log`.

4. On `iperf`:
 
    We run `iperf h1 h2` to represent the throughput between two hosts connected to the same switch, and `iperf h1 h5` to represent the throughput between hosts connected to different switches. We repeat each experiment by three times. The output log for this command is in `output/iperf.log`. 

    The results are in `Mbits`, which is by orders of magnitues lower than what we will see on the controllers installing MicroFlow rules, because all traffic need to go through the controller, which is in user space (much slower than in kernel space).

    The results also show that the TCP bandwidth between h1 and h2 is roughly two times that between h1 and h5. This is because the latter's path is two hops longer than the former's path. Apparently, traversing additional switches cost additional time.

