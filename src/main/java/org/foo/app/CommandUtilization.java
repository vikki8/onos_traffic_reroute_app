package org.foo.app;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import org.apache.commons.lang.time.StopWatch;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.*;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.utils.ForwardingPort;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.foo.app.OsgiPropertyConstants.*;

@Service
@Command(scope = "onos", name = "linkutilization",
        description = "Sample Apache Karaf CLI Link command")

public class CommandUtilization extends AbstractShellCommand {

    @Argument(index = 0, name = "src", description = "SourceID of Path", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String src;

    @Argument(index = 1, name = "dst", description = "DestinationID of Path", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String dst;

    @Override
    protected void doExecute() throws IOException {
        TopologyService topologyService = getService(TopologyService.class);

        DeviceId srcpath = DeviceId.deviceId(src);
        DeviceId dstpath = DeviceId.deviceId(dst);
        Set<Path> shortest_path = topologyService.getPaths
                (topologyService.currentTopology(), srcpath, dstpath);

        Set<Path> paths = topologyService.getKShortestPaths
                (topologyService.currentTopology(), srcpath, dstpath).limit(15)
                .filter(path -> path.cost() <= shortest_path.iterator().next().cost()+1)
                .sorted((o1, o2) -> {
                    if(o1.cost() == o2.cost()) {
                        if (o1.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)
                                > o2.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)) {
                            return 1;
                        } else {
                            return -1;
                        }
                    }
                    else if (o1.cost() > o2.cost()){
                        return 1;
                    }
                    else {
                        return -1;
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        filterpaths(paths);

        Map<Link, Double> a = linkutilizationmapsFlow(); //sFlow to internal and vice versa
        System.out.println();

        //leastutilizedpathcalculation(paths,a);
        //leastutilizedpathperhop(paths,a);

        perhoppathcalculationwithutilization(paths,a);
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

    public void leastutilizedpathperhop(Set<Path> paths, Map<Link, Double> linkutilizationmap){
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator1 bvc = new ValueComparator1(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min;

        for (Path path : paths) {
            ArrayList<Double> minarr = new ArrayList<>();
            List<Link> pathlinkss = path.links();
            for (Link link : pathlinkss) {
                min = linkutilizationmap.get(link);
                minarr.add(min);
            }
            Object [] newarr = minarr.toArray();
            Arrays.sort(newarr);
            pathmap.put(path, (Double) newarr[0]);
            minarr.clear();
        }
        sorted_map.putAll(pathmap);

        Set<Map.Entry<Path, Double> > entries = sorted_map.entrySet();
        System.out.println("\nBasic Version of the Paths:");
        for (Map.Entry<Path, Double> entry : entries) {
            Path path = (Path) entry.getKey();
            List<Link> pathlinkss = path.links();

            System.out.print(path.src().deviceId() +" ---> ");
            for (Link link : pathlinkss) {
                System.out.print(link.dst().deviceId()+ " ---> ");
            }
            System.out.print(" : Hops : " +path.weight().toString().replace("ScalarWeight{value=","").
                    replace("}","")+ " : Cost : " +entry.getValue());
            System.out.println();
        }
        System.out.println("\nIn Detailed Version of the Paths:");
        for (Map.Entry<Path, Double> entry : entries) {
            Path path = (Path) entry.getKey();
            List<Link> pathlinkss = path.links();

            for (Link link : pathlinkss) {
                System.out.print(link.src() + " ---> "+link.dst()+ " ---> ");
            }
            System.out.print(" : Hops : " +path.weight().toString().replace("ScalarWeight{value=","").
                    replace("}","")+ " : Cost : " +entry.getValue());
            System.out.println();
        }
    }

    public void perhoppathcalculationwithutilization(Set<Path> paths, Map<Link, Double> linkutilizationmap){
        double min;
        for (Path path : paths) {
            ArrayList<Double> minarr = new ArrayList<>();
            List<Link> pathlinkss = path.links();

            System.out.print(path.src().deviceId() +" ---> ");
            for (Link link : pathlinkss) {
                System.out.print(link.dst().deviceId()+ " ---> ");
                min = linkutilizationmap.get(link);
                minarr.add(min);
            }
            Object [] newarr = minarr.toArray();
            Arrays.sort(newarr);
            System.out.print(" : Hops : " +path.weight().toString().replace("ScalarWeight{value=","").
                    replace("}","")+ " : Cost : " + newarr[0]);
            System.out.println();
            minarr.clear();
        }
    }

    public void leastutilizedpathcalculation(Set<Path> paths, Map<Link, Double> linkutilizationmap){
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator bvc = new ValueComparator(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min;

        for (Path path : paths) {
            ArrayList<Double> minarr = new ArrayList<>();
            List<Link> pathlinkss = path.links();
            for (Link link : pathlinkss) {
                min = linkutilizationmap.get(link);
                minarr.add(min);
            }
            Object [] newarr = minarr.toArray();
            Arrays.sort(newarr);
            pathmap.put(path, (Double) newarr[0]);
            minarr.clear();
        }
        sorted_map.putAll(pathmap);

        Set<Map.Entry<Path, Double> > entries = sorted_map.entrySet();
        for (Map.Entry<Path, Double> entry : entries) {
            Path path = (Path) entry.getKey();
            List<Link> pathlinkss = path.links();
            for (Link link : pathlinkss) {
                System.out.print(link.src() + " ---> "+link.dst()+ " ---> ");
            }
            System.out.print(" : Hops : " +path.weight().toString().replace("ScalarWeight{value=","").
                    replace("}","")+ " : Cost : " +entry.getValue());
            System.out.println();
        }

        List<Map.Entry> keyList = new ArrayList<>(sorted_map.entrySet());
        Path lowest = (Path) keyList.get(0).getKey();
        List<Link> pathLinks = lowest.links();
        System.out.println();
        System.out.println("The lowest Utilized Path");
        for (Link link : pathLinks) System.out.println(link.src().toString() + " ---> " + link.dst().toString()+ " ---> ");
        System.out.println(keyList.get(0).getValue());
    }

    public Map<Link, Double> linkutilizationmapsFlow() throws IOException {

        LinkService linkService = getService(LinkService.class);

        String url1 = "http://localhost:9090/api/v1/query?query=sflow_ifoutoctets";

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        Map<ConnectPoint, Double> Outportutilization = new HashMap<>();

        Map<Link, Double> link_utilizationmap = new HashMap<>(); //THE MAIN MAP WITH THE LINK AND ITS VALUE

        final JsonNode json = mapper.readTree(new URL(url1));

        int length = json.get("data").get("result").size();

        for (int i = 0; i < length; i++) {
            JsonNode int_name_out = json.get("data").get("result").get(i).get("metric").get("ifname");
            JsonNode metric_value_out = json.get("data").get("result").get(i).get("value").get(1);

            if (!int_name_out.asText().contains("eth")) {
                continue;
            }

            String[] inf = int_name_out.asText().replace("s", "").split("-eth");
            DeviceId deviceId;
            if (Integer.parseInt(inf[0]) <=15 ){
                deviceId = DeviceId.deviceId("of:000000000000000"+Integer.toHexString(Integer.parseInt(inf[0])));
            }
            else{
                deviceId = DeviceId.deviceId("of:00000000000000"+Integer.toHexString(Integer.parseInt(inf[0])));
            }
            PortNumber portNumber = PortNumber.portNumber(inf[1]);

            ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);

            //double port_Oututilization = 100 -(metric_value_out.asDouble() * 8 / 10000000*100);
            double port_Oututilization;
            try {
                port_Oututilization = (Bandwidth.get().get(connectPoint) - (metric_value_out.asDouble()*8)) / 1000000;

            }catch (Exception e){
                port_Oututilization = (10000000 - (metric_value_out.asDouble()*8)) / 1000000;

            }
            Outportutilization.put(connectPoint, port_Oututilization);
        }

        for (Link link : linkService.getLinks()) {
            ConnectPoint src = link.src();
            ConnectPoint dst = link.dst();

            double link_utilization = Outportutilization.get(src);


            print("Link %s --> %s : %s", src, dst, link_utilization);
            if (!link_utilizationmap.containsKey(link)) {
                link_utilizationmap.put(link, link_utilization);
            }
        }
        return link_utilizationmap;
    }

    public Map<Link, Double> linkutilizationmapInternal(){
        LinkService linkService = getService(LinkService.class);
        DeviceService deviceService = getService(DeviceService.class);

        Map<ConnectPoint, Double> Outportutilization1 = new HashMap<>();
        Map<ConnectPoint, Double> Inportutilization1 = new HashMap<>();
        Map<Link, Double> link_utilizationmap1 = new HashMap<>(); //THE MAIN MAP WITH THE LINK AND ITS VALUE

        for (Link link : linkService.getLinks()) {

            double port_Oututilization1 = 100 - ((double) deviceService.getDeltaStatisticsForPort(link.src().deviceId(), link.src().port()).bytesSent() / 5 * 8 / 10000000 * 100);
            double port_Inutilization1 = 100 - ((double) deviceService.getDeltaStatisticsForPort(link.dst().deviceId(), link.dst().port()).bytesReceived() / 5 * 8 / 10000000 * 100);

            Outportutilization1.put(link.src(), port_Oututilization1);
            Inportutilization1.put(link.dst(), port_Inutilization1);
        }

        for (Link link : linkService.getLinks()) {
            ConnectPoint src = link.src();
            ConnectPoint dst = link.dst();

            Double link_utilization = (Outportutilization1.get(src) + Inportutilization1.get(dst)) / 2;
            print("Link %s --> %s : %s", src, dst,link_utilization);
            if (!link_utilizationmap1.containsKey(link)) {
                link_utilizationmap1.put(link, link_utilization);
            }
        }
        return link_utilizationmap1;
    }



    public Map<ConnectPoint, Integer> linkcapacitymap() throws IOException {
        String url = "http://localhost:9090/api/v1/query?query=linkcapacity";

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final JsonNode json1 = mapper.readTree(new URL(url));

        Map<ConnectPoint, Integer> linkcapacity = new HashMap<>();

        int length = json1.get("data").get("result").size();

        for (int i = 0; i < length; i++) {
            JsonNode connectpoint = json1.get("data").get("result").get(i).get("metric").get("connectpoint");
            JsonNode linkspeed_value = json1.get("data").get("result").get(i).get("value").get(1);


            String [] inf = connectpoint.asText().split("/");

            DeviceId deviceId = DeviceId.deviceId(inf[0]);
            PortNumber portNumber = PortNumber.portNumber(inf[1]);
            int linkspeed = linkspeed_value.asInt();

            linkcapacity.put(new ConnectPoint(deviceId,portNumber),linkspeed);
        }
        return linkcapacity;
    }

    public int linkspeed(String connectpoint) throws IOException {
        String url = "http://10.100.10.8:9090/api/v1/query?query=linkcapacity{connectpoint=~%22"+connectpoint+"%22}";
        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final JsonNode json1 = mapper.readTree(new URL(url));
        JsonNode linkspeed_value = json1.get("data").get("result").get(0).get("value").get(1);
        return linkspeed_value.asInt();
    }

}