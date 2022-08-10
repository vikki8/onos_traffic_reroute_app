package org.foo.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.graph.Edge;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.PrettyJson;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.statistic.FlowStatisticService;
import org.onosproject.net.statistic.Load;
import org.onosproject.net.statistic.StatisticService;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyEdge;

import java.util.ArrayList;
import java.util.List;

import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.net.PortNumber.portNumber;

@Service
@Command(scope = "onos", name = "portloadcheck",
        description = "Sample Apache Karaf CLI Link command")

public class CommandLoad extends AbstractShellCommand {
    @Override
    protected void doExecute(){

        StatisticService service = getService(StatisticService.class);
        FlowStatisticService fservice = getService(FlowStatisticService.class);
        DeviceService d = getService(DeviceService.class);
        List<DeviceId> div = new ArrayList<>();
        List<ConnectPoint> cp = new ArrayList<>();
        for (Device device : d.getAvailableDevices()) {
            String element = String.valueOf(device.id());
            //System.out.println(element);
            for (int i=0;i<d.getPorts(device.id()).size();i++){
                cp.add(new ConnectPoint(device.id(),d.getPorts(device.id()).get(i).number()));
              //  System.out.println(d.getPorts(device.id()).get(i).number());
            }

            div.add(device.id());
        }
        System.out.println("Port Speed is "+ d.getPorts(div.get(0)).get(1).portSpeed());
        long speed = d.getPorts(div.get(0)).get(1).portSpeed();
        System.out.println();

        for (ConnectPoint connectPoint : cp) {
            Load load = service.load(connectPoint);
            if (load.isValid()) {
                print("Load on %s -> %s", connectPoint, load.isValid());
                print("Load on %s -> %s", connectPoint, load.rate());
                print("\n");
            }
        }



    }
}
