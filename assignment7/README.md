# Assignment 7: OpenFlow

The OpenFlow assignment.

## How to Run

1. Launch POX controller.

    First, please copy all controller scripts (./\*Controller/t\*_controller.py) into `~/pox/ext`.
    
    ```
    # Assuming working on the mininet VM, the current directory is ~/pox.
    
    # For 4.1: Learning POX Controller (using the default topology)
    $ ./pox.py log.level --DEBUG t2_controller
    
    # For 4.2.1: Hub Controller
    $ ./pox.py log.level --DEBUG t1_controller
    
    # For 4.2.2: Learning Controller
    $ ./pox.py log.level --DEBUG t2_controller
    
    # For 4.2.3: MicroFlow Controller
    $ ./pox.py log.level --DEBUG t3_controller
    
    # For 4.3: Policy Controller
    $ ./pox.py log.level --DEBUG t4_controller
    ```


    The `log.level` argument can be set to `--INFO` to reduce verboseness.

2. Launch mininet by specifying the topology to use.

    ```
    # Assuming working on the mininet VM, the current directory is ~/mininet/custom.
    
    # For 4.1: Learning POX Controller (using the default topology)
    $ sudo mn --topo single,3 --mac --switch ovsk --controller remote
    
    # For 4.2.1: Hub Controller
    $ sudo mn --custom t1_topology.py --topo custom_topo_I --mac --controller remote
    
    # For 4.2.2: Learning Controller
    $ sudo mn --custom t2_topology.py --topo custom_topo_I --mac --controller remote
    
    # For 4.2.3: MicroFlow Controller
    $ sudo mn --custom t3_topology.py --topo custom_topo_I --mac --controller remote
    
    # For 4.3: Policy Controller
    $ sudo mn --custom t4_topology.py --topo custom_topo_II --mac --controller remote
    ```

## System Modifications

None.

This file is renamed from `README.txt` to `README.md` to enable markdown format displaying.

---
Zhifei Yang (Legi: 17941998)

Computer Science MSc Student

ETH Zürich

