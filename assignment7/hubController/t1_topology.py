"""Custom topology I

Five hosts connected to three switches:

  host 1 --- switch 1 --- switch 2 --- switch 3 --- host 5
               |                         |     \
               |                         |      \
               |                         |       \
             host 2                    host 3   host 4
"""

from mininet.topo import Topo

class CustomTopoI( Topo ):
    "Custom topology I."

    def __init__( self ):
        "Create custom topo."

        # Initialize topology
        Topo.__init__( self )

        # Add hosts and switches
        host1 = self.addHost( 'h1' )
        host2 = self.addHost( 'h2' )
        host3 = self.addHost( 'h3' )
        host4 = self.addHost( 'h4' )
        host5 = self.addHost( 'h5' )
        switch1 = self.addSwitch( 's1' )
        switch2 = self.addSwitch( 's2' )
        switch3 = self.addSwitch( 's3' )

        # Add links
        self.addLink( host1, switch1 )
        self.addLink( host2, switch1 )
        self.addLink( switch1, switch2 )
        self.addLink( switch2, switch3 )
        self.addLink( switch3, host3 )
        self.addLink( switch3, host4 )
        self.addLink( switch3, host5 )



topos = { 'custom_topo_I': ( lambda: CustomTopoI() ) }
