/*
 * Copyright 2022-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.foo.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.statistic.StatisticService;
import org.onosproject.net.topology.TopologyService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.net.PortNumber.portNumber;


/**
 * Sample Apache Karaf CLI command.
 */
@Service
@Command(scope = "onos", name = "pathcheck",
         description = "Sample Apache Karaf CLI command")
public class CommandPath extends AbstractShellCommand{

    @Override
    protected void doExecute(){
        DeviceService d = getService(DeviceService.class);
        TopologyService t = getService(TopologyService.class);
        StatisticService s = getService(StatisticService.class);

        List<DeviceId> div = new ArrayList<>();;
        for (Device device : d.getAvailableDevices()) {
            div.add(device.id());
        }
        div.sort(new SortById());


        Stream<Path> l = t.getKShortestPaths(t.currentTopology(), div.get(0), div.get(1));
        List<Path> result = l.collect(Collectors.toList());

        System.out.println("Get K-Shortest Path");

        for (Path path : result) {
            List<Link> pathlink = path.links();
            print("Cost %s ", path.cost()); //Can use path.weight() too for Weight Object
            //System.out.print(pathlink.get(0).src() + " ---> ");
            for (Link link : pathlink) {
                System.out.print(link.src()+ " ---> " + link.dst() + " ---> ");
            }
            System.out.println();
        }

        System.out.println("\nGet Current Path");
        for (Path check : t.getPaths(t.currentTopology(), div.get(0), div.get(1))) {
            List<Link> plink = check.links();
            for (Link link : plink) {
                long bandwidth = s.load(link).rate() * 8;
                System.out.print(link.src() + " ---> " + link.dst() + " ---> ");
                System.out.println("The load on "+ link.src()+" ---> "+link.dst()+" is: "+ bandwidth);
            }
            System.out.println();

        }
        System.out.println("------------------");
        System.out.println("Get Current Path source");
        for (Path check : t.getPaths(t.currentTopology(), div.get(0), div.get(1))) {
            System.out.println(check.src());
        }

        System.out.println("------------------");
        System.out.println("Number of devices in a path");
        List<DeviceId> divi = new ArrayList<>();;
        Path spath = t.getPaths(t.currentTopology(), div.get(0), div.get(1)).stream().iterator().next();
            List<Link> plink = spath.links();
            for (Link link : plink) {
                if (!divi.contains(link.src().deviceId())){
                    divi.add(link.src().deviceId());
                }
                if (!divi.contains(link.dst().deviceId())){
                    divi.add(link.dst().deviceId());
                }
            }
        System.out.println(divi);
        System.out.println();
        System.out.println("------------------");
        System.out.println("Non clashing path");
        Stream<Path> all_paths1 = t.getKShortestPaths(t.currentTopology(), div.get(0), div.get(3));
        List<Path> paths1 = all_paths1.collect(Collectors.toList());
        Stream<Path> all_paths2 = t.getKShortestPaths(t.currentTopology(), div.get(1), div.get(3));
        List<Path> paths2 = all_paths2.collect(Collectors.toList());
        boolean check = false;
        for (Path path1 : paths1) {
            List<Link> pathlink1 = path1.links();
            for (Link link : pathlink1) {
                System.out.print(link.src() + " ---> " + link.dst() + " ---> ");
            }
            System.out.println();
            for (Path path2 : paths2){
                List<Link> pathlink2 = path2.links();
                for (Link link : pathlink1) {
                    if (pathlink2.contains(link)){
                        check = true;
                    }
                }
                if(!check){
                    for (Link link : pathlink2) {
                        System.out.print(link.src() + " ---> " + link.dst() + " ---> ");
                    }
                    System.out.println();
                }
                check = false;
            }
            System.out.println();
            check=false;
        }



        }

        }



class SortById implements Comparator<DeviceId> {
    // Used for sorting in ascending order of ID
    public int compare(DeviceId a, DeviceId b) {
        if (a == b) {
            return 0;
        } else if (String.valueOf(a).compareTo(String.valueOf(b)) > 0) {
            return 1;
        } else
            return -1;
    }
}





