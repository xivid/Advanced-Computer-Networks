# Policy Controller

On Custom Topology II:

For all traffic from H1 to H4 or from H2 to H4, they must traverse switch 3.

For all other traffic (including from H4 to H1 or H2), the default paths will be the shortest paths.

Although installing microflow rules is not required, it is implemented to improve the performance.

## Creating Topology

Following the [openflow-tutorial](https://github.com/mininet/openflow-tutorial/wiki/Router-Exercise) and `mininet/custom/topo-2sw-2host.py`, a custom topology of 3 switches and 4 hosts is created in `t4_topology.py`:

```
      host 1       switch 3        host 3
          \       /        \        /
           \     /          \      /
            \   /            \    /
  host 2 --- switch 1 --- switch 2 --- host 4
```

The custom topology is a subclass of mininet.topo.Topo. Hosts, switches and links are added by calling methods `addHost()`, `addSwitch()` and `addLink()`.

Host __x__ is statically assigned with IP address 10.0.0.__x__/24 to make it possible to express the policies. Switch __y__ is statically assigned with DPID "0000yyyyyyyyyyyy" to make it more recognisable, but the controller implementation *does not* rely on this information at all.

All names ("h\*", "s\*") are lowercase rather than the uppercases given in the assignment sheet, in order to save my keystrokes.

## Learning Topology

The controller learns about the whole topology in the following way.

To learn about switches and the links between them, the controller calls `pox.core.openflow_discovery` and listens for `ConnectionUp` events. `openflow_discovery` will send LLDP (Link Layer Discovery Protocol) packets around the network to discover the topology. Whenever a `ConnectionUp` event is triggered (can be link adding or removing), the controller updates the stored topology graph, which is an object of the `Topology` class. Within only a few seconds, the three switches and three links among them can be found.

To learn about the four hosts, the controller has to wait until hosts send packets. When a switch receives an ARP or IP packet from a host, and redirects it to the controller, the controller will parse the packet header, conclude that there is a host connected to the calling switch, and update the stored topology if the host is a new one. After all hosts have sent a ARP or IP packet, the learning will be complete.

It is worth mentioning that the controller are programmed to reply to ARP requests (if it knows about the requested host) instead of flooding them around. It routes IP packets by installing microflow rules into switches to make them traverse policy-defined paths or shortest paths (if not constraint by policy). **All incoming packets other than ARP and IP packets are dropped.**

## Behaviors

1. On `pingall`:

    We run `pingall` three times: 1) right after the controller startup (to show how the controller learns about all hosts), 2) long enough time after the first call (so that the microflow rules installed on switches have expired), 3) immediately after the second call (to show that the traffics do not traverse the controller). The output of mininet and POX controller is in `output/pingall.log` and `output/controller.log`, respectively.

    In the first call, `h1` fails to ping any other host because the controller does not know any host yet. But from this, the controller can learn about `h1`. So, afterwards, `h2` pings `h1` successfully, while still fails to ping `h3` and `h4`. But the controller now learns about `h2`. So next `h3` pings `h1` and `h2` successfully while fails to ping `h4`. Finally, `h4` succeeds to ping all three hosts because all have been learned by the controller.

    In the second call, all hosts are pingable, since the whole topology has been learned from the first call. However, since the microflow rules have expired, the controller still receives the traffic and installs the rules again.

    In the third call, the microflow rules installed on switches are still valid, so no traffic are sent to the controller.

    Next, we `ping` each pair of hosts and use Wireshark to dump traffic on switches. This will show that the policies are enforced correctly.

2. On `ping`ing between any two hosts after topology has been fully learnt:

    We run `h1 ping -c1 h2`, `h1 ping -c1 h3`, `h1 ping -c1 h4`, `h2 ping -c1 h3`, `h2 ping -c1 h4`, `h3 ping -c1 h4` one by one. Since a `ping`ing involves sending packets in both directions, and we have proved above that all hosts are pingable to each other, these six tests are sufficient to show that the policies are enforced correctly. The `ping` outputs are in `output/ping.log`.

    We run three Wireshark instances to capture the traffic on all three switches. The dump files are `output/switch*_wireshark.pcapng`. It can be concluded from the dump results that only `s1` is involved in `h1 ping -c1 h2`, only `s1` and `s2` are involved in `h1 ping -c1 h3`, `s1`, `s2` and `s3` are involved in `h1 ping -c1 h4`, only `s1` and `s2` are involved in `h2 ping -c1 h3`, `s1`, `s2` and `s3` are involved in `h2 ping -c1 h4`, and only `s2` is involved in `h3 ping -c1 h4`. 

    Everything works exactly as expected. Other than `h1 -> h4` and `h2 -> h4`, all traffics flow on the shortest path. The controller uses a BFS algorithm to find the shortest path and installs a microflow rule about the matching conditions and output port on each switch.

3. On `h1 ping -c 100 h3` vs `h1 ping -c 100 h4`:
 
    We repeat each experiment by three times. The output logs are `output/h1_ping_h3.log` and `output/h1_ping_h4.log`. The latencies of the two with respect to sequence numbers are plotted on the same figure `plots/h1_ping_h3_h4.png`.

    Although `h1 -> h4` goes one more hop than `h1 -> h3`, the ping latencies are almost the same. It may result from the extremely short travelling time of one hop as well as the limited accuracy of Linux `ping`.
