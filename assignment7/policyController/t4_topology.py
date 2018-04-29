"""Custom topology II

Four hosts connected to three switches:

      host 1       switch 3        host 3
          \       /        \        /
           \     /          \      /
            \   /            \    /
  host 2 --- switch 1 --- switch 2 --- host 4
"""

from mininet.topo import Topo

class CustomTopoII( Topo ):
    "Custom topology II."

    def __init__( self ):
        "Create custom topo."

        # Initialize topology
        Topo.__init__( self )

        # Add hosts and switches
        host1 = self.addHost( 'h1', ip="10.0.1.1/24" )
        host2 = self.addHost( 'h2', ip="10.0.1.2/24" )
        host3 = self.addHost( 'h3', ip="10.0.2.1/24" )
        host4 = self.addHost( 'h4', ip="10.0.2.2/24" )
        switch1 = self.addSwitch( 's1' )
        switch2 = self.addSwitch( 's2' )
        switch3 = self.addSwitch( 's3' )

        # Add links
        self.addLink( host1, switch1 )
        self.addLink( host2, switch1 )
        self.addLink( switch1, switch2 )
        self.addLink( switch1, switch3 )
        self.addLink( switch2, switch3 )
        self.addLink( switch2, host3 )
        self.addLink( switch2, host4 )


topos = { 'custom_topo_II': ( lambda: CustomTopoII() ) }
