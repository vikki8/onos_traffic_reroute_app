package org.foo.app;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.MacAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.topology.TopologyService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Command(scope = "onos", name = "roundrobin",
        description = "Sample Apache Karaf CLI Link command")
public class CommandRoundRobin extends AbstractShellCommand {
    @Argument(index = 0, name = "srcid", description = "SourceID of Path", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String srcid;

    @Argument(index = 1, name = "dstid", description = "DestinationID of Path", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String dstid;

    @Argument(index = 2, name = "srcmac", description = "Source Mac", required = true, multiValued = false)
    @Completion(MacAddressCompleter.class)
    String srcmac;

    @Argument(index = 3, name = "dstmac", description = "Destination Mac", required = true, multiValued = false)
    @Completion(MacAddressCompleter.class)
    String dstmac;

    @Argument(index = 4, name = "clear", description = "Clear counter", required = true, multiValued = false)
    Boolean clear;

    @Override
    protected void doExecute() {
        if(clear){
            Counter.clear();
        }
        else {
            TopologyService topologyService = getService(TopologyService.class);

            DeviceId srcpath = DeviceId.deviceId(srcid);
            DeviceId dstpath = DeviceId.deviceId(dstid);
            Set<Path> shortest_path = topologyService.getPaths
                    (topologyService.currentTopology(), srcpath, dstpath);

            Set<Path> paths = topologyService.getKShortestPaths
                            (topologyService.currentTopology(), srcpath, dstpath).limit(15)
                    .filter(path -> path.cost() <= shortest_path.iterator().next().cost() + 1)
                    .sorted((o1, o2) -> {
                        if (o1.cost() == o2.cost()) {
                            if (o1.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)
                                    > o2.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)) {
                                return 1;
                            } else {
                                return -1;
                            }
                        } else if (o1.cost() > o2.cost()) {
                            return 1;
                        } else {
                            return -1;
                        }
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            filterpaths(paths);
            System.out.println("Paths Available:");
            perhoppathcalculation(paths);
            System.out.println();
            System.out.println("Round Robin Path:");
            roundrobin(paths, shortest_path.size(), Counter.COUNT);
            Counter.increment();
            System.out.println();
        }

    }

    public void filterpaths(Set<Path> paths){
        List<Path> aList
                = new ArrayList<>(paths);
        for(int i=0; i<aList.size();i++) {
            for (Link link : aList.get(i).links()) {
                if(link.dst() == aList.get(i).dst()){
                    break;
                }
                if(Marker.get().get(link.dst().deviceId()).equals("access")){
                    paths.remove(aList.get(i));
                }
            }
        }

    }

    public void perhoppathcalculation(Set<Path> paths){
        for (Path path : paths) {
            System.out.print(path.src().deviceId() +" ---> ");
            for (Link link : path.links()) {
                System.out.print(link.dst().deviceId()+ " ---> ");
            }
            System.out.println();
        }
    }

    public void roundrobin(Set<Path> paths,  int length, int counter){
        int i = 0;
        for (Path path : paths) {
            if (i == length){
                i = 0;
            }
            if(counter%length == i){
                System.out.print(path.src().deviceId() +" ---> ");
                for (Link link : path.links()) {
                    System.out.print(link.dst().deviceId()+ " ---> ");
                }
                break;
            }
            i++;
        }
    }

}
