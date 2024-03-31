package org.foo.app;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
@Command(scope = "onos", name = "validate",
        description = "Validate Link Utilization Calculation")


public class CommandValidate extends AbstractShellCommand {
    @Override
    protected void doExecute(){
        String url1 = "http://localhost:9090/api/v1/query?query=sflow_ifoutoctets";

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

        Map<ConnectPoint, Double> Outportutilization = new HashMap<>();

        LinkService linkService = getService(LinkService.class);
        FlowRuleService flowRuleService = getService(FlowRuleService.class);
        CoreService coreService = getService(CoreService.class);

        JsonNode json = null;

        try {
            json = mapper.readTree(new URL(url1));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int length = json.get("data").get("result").size();

        for (int i = 0; i < length; i++) {
            JsonNode int_name_out = json.get("data").get("result").get(i).get("metric").get("ifname");
            JsonNode metric_value_out = json.get("data").get("result").get(i).get("value").get(1);

            if (!int_name_out.asText().contains("eth")) {
                continue;
            }

            String [] inf = int_name_out.asText().replace("s","").split("-eth");
            DeviceId deviceId;
            if (Integer.parseInt(inf[0]) <=15 ){
                deviceId = DeviceId.deviceId("of:000000000000000"+Integer.toHexString(Integer.parseInt(inf[0])));
            }
            else{
                deviceId = DeviceId.deviceId("of:00000000000000"+Integer.toHexString(Integer.parseInt(inf[0])));
            }
            PortNumber portNumber = PortNumber.portNumber(inf[1]);

            ConnectPoint connectPoint = new ConnectPoint(deviceId,portNumber);

            double port_Oututilization = metric_value_out.asDouble();

            Outportutilization.put(connectPoint, port_Oututilization);
        }

        for (Link link : linkService.getLinks()) {

            ConnectPoint src = link.src();
            ConnectPoint dst = link.dst();

            double egress_bytes;
            try{
                egress_bytes = Outportutilization.get(src);

            }catch (Exception e){
                break;
            }

            int max_bandwidth;
            try{
                max_bandwidth = Bandwidth.get().get(src);

            }catch (Exception e){
                max_bandwidth = 10000000;
            }

            print("Link %s ---> %s ",src,dst);
            print("Link Maximum Bandwidth: %s",max_bandwidth);
            print("Src Port: %s",src);
            print("Dst port: %s",dst);
            print("Egress Bytes (Src): %s",egress_bytes);

            double link_utilization = (max_bandwidth - (egress_bytes*8)) / 1000000;

            print("Link Available Bandwidth (Formula: (Max_Bandwidth - (Egress Bytes * 8)) / 1000000): %s Mbits",link_utilization);

            print("\n");

            print("Flow Rules in Link");
            print("Src Port: %S",src);
            for (FlowEntry flowEntry : flowRuleService.getFlowEntries(src.deviceId())){
                print(flowEntry.toString());
            }
            print("\n");
            print("Dst port: %s",dst);
            for (FlowEntry flowEntry : flowRuleService.getFlowEntries(dst.deviceId())){
                print(flowEntry.toString());
            }
            print("\n");
        }

    }

    public int linkspeed(String connectpoint) throws IOException {
        String url = "http://10.100.10.8:9090/api/v1/query?query=linkcapacity{connectpoint=~%22"+connectpoint+"%22}";
        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final JsonNode json1 = mapper.readTree(new URL(url));
        JsonNode linkspeed_value = json1.get("data").get("result").get(0).get("value").get(1);
        return linkspeed_value.asInt();
    }
}
