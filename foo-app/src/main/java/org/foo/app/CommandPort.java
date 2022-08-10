package org.foo.app;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkService;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Service
@Command(scope = "onos", name = "linkutilization",
        description = "Sample Apache Karaf CLI Link command")

public class CommandPort extends AbstractShellCommand {
    private static HttpURLConnection connection;
    private static Map<ConnectPoint, Integer> linkcapacitymap = new HashMap<>();
    private static Map<String, Double> sflowinoctetsmap = new HashMap<>();
    private static Map<String, Double> sflowoutoctetsmap = new HashMap<>();
    @Override
    protected void doExecute() throws JsonProcessingException {
        String url1 = "http://localhost:9090/api/v1/query?query=sflow_ifoutoctets";
        String url2 = "http://localhost:9090/api/v1/query?query=sflow_ifinoctets";

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final JsonNode json1 = mapper.readTree(String.valueOf(api(url1)));
        final JsonNode json2 = mapper.readTree(String.valueOf(api(url2)));
        int length = json1.get("data").get("result").size();
        List<String> inf = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            JsonNode int_name_out = json1.get("data").get("result").get(i).get("metric").get("ifname");
            JsonNode metric_value_out = json1.get("data").get("result").get(i).get("value").get(1);

            JsonNode int_name_in = json2.get("data").get("result").get(i).get("metric").get("ifname");
            JsonNode metric_value_in = json2.get("data").get("result").get(i).get("value").get(1);

            if (!int_name_in.asText().contains("eth")) {
                continue;
            }

            inf.add(int_name_out.toString());

            sflowinoctetsmap.put(int_name_in.asText(), metric_value_in.asDouble());
            sflowoutoctetsmap.put(int_name_out.asText(), metric_value_out.asDouble());
        }

        inf.sort(new SortByInterface()); //SORT SFLOW INTERFACES

        DeviceService deviceService = getService(DeviceService.class);
        LinkService linkService = getService(LinkService.class);

        List<DeviceId> div = new ArrayList<>();
        List<ConnectPoint> cp = new ArrayList<>();

        for (Device device : deviceService.getAvailableDevices()) {
            div.add(device.id());
        }
        div.sort(new SortById()); //SORT DEVICEID AND THIS WILL AUTOMATICALLY SORT CONNECTPOINT

        for (DeviceId deviceId : div) {
            for (int j = 1; j < deviceService.getPorts(deviceId).size(); j++) {
                cp.add(new ConnectPoint(deviceId, deviceService.getPorts(deviceId).get(j).number()));
            }
        }

        Map<ConnectPoint, Double> onosOut_utilization = new HashMap<>();
        Map<ConnectPoint, Double> onosIn_utilization  = new HashMap<>();

        Map<Link, Double> link_utilizationmap = new HashMap<>(); //THE MAIN MAP WITH THE LINK AND ITS VALUE

        System.out.println("Port Ingress Utilization");

        for (int i = 0; i < cp.size(); i++) {
            double portinutilization = sflowinoctetsmap.get(inf.get(i).replace("\"", "")) * 8 / 10000000*100;

            onosIn_utilization .put(cp.get(i), portinutilization);

            System.out.println(cp.get(i) + ": " + portinutilization);
        }
        System.out.println();
        System.out.println("Port Egress Utilization");

        for (int i = 0; i < cp.size(); i++) {
            double portoututilization = sflowoutoctetsmap.get(inf.get(i).replace("\"", "")) * 8 / 10000000*100;
            onosOut_utilization.put(cp.get(i), portoututilization);

            System.out.println(cp.get(i) + ": " + portoututilization);
        }
        System.out.println();
        System.out.println("Link Utilization");

        for (ConnectPoint connectPoint : cp) {
            for (Link link : linkService.getLinks(connectPoint)) {
                ConnectPoint src = link.src();
                ConnectPoint dst = link.dst();

                double link_utilization = (onosOut_utilization.get(src) + onosIn_utilization.get(dst)) / 2;

                print("Link %s --> %s : %s", src, dst,link_utilization);

                if (!link_utilizationmap.containsKey(link)) {
                    link_utilizationmap.put(link, link_utilization);
                }
            }
            System.out.println();
        }
    }

    public Map<ConnectPoint, Integer> getLinkcapacitymap(){
        File homedir = new File(System.getProperty("user.home"));
        File fileToRead = new File(homedir, "foo-app/src/main/java/org/foo/app/config.txt");
        try{
            Scanner s = new Scanner(fileToRead);
            LinkService linkService = getService(LinkService.class);
            while(s.hasNext()){

                ConnectPoint connectPoint = ConnectPoint.deviceConnectPoint(s.next());
                int linkcapacity = Integer.parseInt(s.next());

                linkcapacitymap.put(connectPoint,linkcapacity);
            }
        }
        catch (FileNotFoundException e){
            System.out.println("Check File Path");
        }
        return linkcapacitymap;
    }

    public StringBuffer api(String web) {
        BufferedReader reader;
        String line;
        StringBuffer responseContent = new StringBuffer();

        try {
            URL url = new URL(web);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int status = connection.getResponseCode();

            if (status > 299) {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
                reader.close();
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
                reader.close();
            }
        }catch (MalformedURLException e){
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        }
        return responseContent;
    }
}
