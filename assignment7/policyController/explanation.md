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

    The policy is enforced correctly.

    [Wireshark traces on all switches]



//    All hosts are pingable as expected. Microflow rules are installed to all involved switches on all hops.

//    The output log for this command is in `output/pingall.log`. The microflow rules dumped from each switch (from command `sudo ovs-ofctl dump-flows [switch]`) is in `output/s*_dumpflows.log`.

3. On `iperf h1 h2` vs `iperf h1 h3` vs `iperf h1 h4`:
 
    We repeat each experiment by three times. The output log for this command is in `output/iperf.log`. 

//    The results are in `Gbits/sec`, which is by orders of magnitude higher than what we have seen on the previous MAC learning controllers, because once microflow rules are installed, matching traffic will no more need to go through the controller in user space, involving no context switch from kernel space to user space.

