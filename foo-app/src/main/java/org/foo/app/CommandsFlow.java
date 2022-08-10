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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Sample Apache Karaf CLI command.
 */
@Service
@Command(scope = "onos", name = "backup",
        description = "The Link Utilization of EVERY Links")
public class CommandsFlow extends AbstractShellCommand{
    private static HttpURLConnection connection;
    static Map<String, Double >map1 = new HashMap<>();
    static Map<String, Double >map2 = new HashMap<>();

    @Override
    protected void doExecute() throws JsonProcessingException {
        String url1 = "http://10.100.10.8:9090/api/v1/query?query=sflow_ifoututilization";
        String url2 = "http://10.100.10.8:9090/api/v1/query?query=sflow_ifinutilization";

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

            map1.put(int_name_out.asText(), metric_value_out.asDouble() * 1000);
            map2.put(int_name_in.asText(), metric_value_in.asDouble() * 1000);

        }
        inf.sort(new SortByInterface()); //SORT SFLOW INTERFACES

        //System.out.println("Out_Utilization");
        //sortbykey1();
        //System.out.println();
        //System.out.println("In_Utilization");
        //sortbykey2();

        System.out.println();

        DeviceService d = getService(DeviceService.class);

        LinkService l = getService(LinkService.class);
        List<DeviceId> div = new ArrayList<>();
        ;
        List<ConnectPoint> cp = new ArrayList<>();

        for (Device device : d.getAvailableDevices()) {
            div.add(device.id());
        }
        div.sort(new SortById()); //SORT DEVICEID AND THIS WILL AUTOMATICALLY SORT CONNECTPOINT

        for (DeviceId deviceId : div) {
            for (int j = 1; j < d.getPorts(deviceId).size(); j++) {
                cp.add(new ConnectPoint(deviceId, d.getPorts(deviceId).get(j).number()));
            }
        }

        Map<ConnectPoint, Double> sFlowOut_connect = new HashMap<>();
        Map<ConnectPoint, Double> sFlowIn_connect = new HashMap<>();

        Map<Link, Double> link_utilization = new HashMap<>(); //THE MAIN MAP WITH THE LINK AND ITS VALUE

        for (int i = 0; i < cp.size(); i++) {
            sFlowOut_connect.put(cp.get(i), map1.get(inf.get(i).replace("\"", "")));

            System.out.println(cp.get(i) + ": " + map1.get(inf.get(i).replace("\"", "")));
        }
        System.out.println();
        for (int i = 0; i < cp.size(); i++) {
            sFlowIn_connect.put(cp.get(i), map2.get(inf.get(i).replace("\"", "")));

            System.out.println(cp.get(i) + ": " + map2.get(inf.get(i).replace("\"", "")));
        }
        System.out.println();
        double src_utilization_total;
        double dst_utilization_total;
        double link_utilization_val;

        for (ConnectPoint connectPoint : cp) {
            for (Link link : l.getLinks(connectPoint)) {
                ConnectPoint src = link.src();
                ConnectPoint dst = link.dst();

                src_utilization_total = sFlowOut_connect.get(src) + sFlowIn_connect.get(src);
                dst_utilization_total = sFlowOut_connect.get(dst) + sFlowIn_connect.get(dst);

                link_utilization_val = (src_utilization_total + dst_utilization_total) / 2;

                print("Link %s --> %s : %s", src, dst, link_utilization_val);
                if (!link_utilization.containsKey(link)) {
                    link_utilization.put(link, link_utilization_val);
                }

            }
            System.out.println();
        }
    }

    public static StringBuffer api(String web) {
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

    public static void sortbykey1()
    {
        // TreeMap to store values of HashMap
        TreeMap<String, Double> sorted = new TreeMap<>();

        // Copy all data from hashMap into TreeMap
        sorted.putAll(map1);

        // Display the TreeMap which is naturally sorted
        for (Map.Entry<String, Double> entry : sorted.entrySet())
            System.out.println(entry.getKey() +
                    ": "+ entry.getValue());
    }

    public static void sortbykey2()
    {
        // TreeMap to store values of HashMap
        TreeMap<String, Double> sorted = new TreeMap<>();

        // Copy all data from hashMap into TreeMap
        sorted.putAll(map2);

        // Display the TreeMap which is naturally sorted
        for (Map.Entry<String, Double> entry : sorted.entrySet())
            System.out.println(entry.getKey() +
                    ": "+ entry.getValue());
    }

}

class SortByInterface implements Comparator<String> {
    // Used for sorting in ascending order of ID
    public int compare(String a, String b) {
        if (Objects.equals(a, b)) {
            return 0;
        } else if (String.valueOf(a).compareTo(String.valueOf(b)) > 0) {
            return 1;
        } else
            return -1;
    }
}


