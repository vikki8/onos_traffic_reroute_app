package org.foo.app;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;

import java.util.Set;

@Service
@Command(scope = "onos", name = "bandwidth",
        description = "Sample Apache Karaf CLI Link command")

public class CommandBandwidth extends AbstractShellCommand {
    @Argument(index = 0, name = "role", description = "role", required = true, multiValued = false)
    String role;

    @Argument(index = 1, name = "value", description = "value", required = true, multiValued = false)
    Integer value;

    @Override
    protected void doExecute() {
        Bandwidth.update(role,value);
        log.info(Bandwidth.get().toString());
    }
}
