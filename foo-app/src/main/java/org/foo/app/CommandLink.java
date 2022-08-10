package org.foo.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.statistic.Load;
import org.onosproject.net.statistic.StatisticService;

import java.util.*;

@Service
@Command(scope = "onos", name = "linkloadcheck",
        description = "Sample Apache Karaf CLI command")
public class CommandLink extends AbstractShellCommand {
    @Override
    protected void doExecute() {
        LinkService link = getService(LinkService.class);
        DeviceService d = getService(DeviceService.class);
        StatisticService service = getService(StatisticService.class);

        for (Link l : link.getLinks()) {
           System.out.println(l);
        }

        List<ConnectPoint> cp = new ArrayList<>();
        List<DeviceId> div = new ArrayList<>();

        for (Device device : d.getAvailableDevices()) {
            for (int i=0;i<d.getPorts(device.id()).size();i++){
                cp.add(new ConnectPoint(device.id(),d.getPorts(device.id()).get(i).number()));
            }
            div.add(device.id());
        }

        for (ConnectPoint connectPoint : cp) {
            if (link.getLinks(connectPoint).isEmpty()){
                continue;
            }
            Iterator<Link> i = link.getLinks(connectPoint).iterator();

            while(i.hasNext()){ // ALWAYS SET A VARIABLE FOR i.next() Then use its methods
                Link linkload = i.next();
                //System.out.println(link.getLink(linkload.src(),linkload.dst()));

               Load load = service.load(link.getLink(linkload.src(),linkload.dst()));
              // if (load.isValid()) {
                   print("Load on %s - %s -> %s", link.getLink(linkload.src(), linkload.dst()).src(), link.getLink(linkload.src(), linkload.dst()).dst(), load.isValid());
                   print("Load on %s - %s -> %s", link.getLink(linkload.src(), linkload.dst()).src(), link.getLink(linkload.src(), linkload.dst()).dst(), load.rate());
                   print("\n");
               //}
            }
        }





    }
}
