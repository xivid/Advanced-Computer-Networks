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

All names ("h\*", "s\*") are lowercase rather than the uppercases given in the assignment sheet to save my keystrokes.

## Learning Topology

The hub controller does not learn the topology at all. It just floods all traffic.

## Behaviors

a) On `h1 ping -c 100 h2`:
    TODO
    
    The output log for this command is in `output/h1_ping_h2.log`.

b) On `h1 ping -c 100 h5`:
    TODO

    The output log for this command is in `output/h1_ping_h2.log`.

c) On `pingall`:
    TODO

    The output log for this command is in `output/pingall.log`.

d) On `iperf`:
    We run `iperf h1 h2` to represent the throughput between two hosts connected to the same switch, and `iperf h1 h5` to represent the throughput between hosts connected to different switches.

    TODO

    The output log for this command is in `output/iperf.log`.

