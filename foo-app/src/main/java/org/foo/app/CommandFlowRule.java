package org.foo.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.PacketContext;

import java.util.Set;

@Service
@Command(scope = "onos", name = "flowrulecheck",
        description = "Sample Apache Karaf CLI command")
public class CommandFlowRule extends AbstractShellCommand {
    @Override
    protected void doExecute() {
        FlowRuleService flowRuleService = getService(FlowRuleService.class);
        CoreService coreService = getService(CoreService.class);
        DeviceService deviceService = getService(DeviceService.class);
        ApplicationId FooAppId = coreService.getAppId("org.foo.app");

        for (Device device : deviceService.getAvailableDevices()){
            int i = 0;
            for (FlowEntry flowEntry : flowRuleService.getFlowEntries(device.id())){
                if (FooAppId.id() == flowEntry.appId()){
                    i++;
                }
            }
            System.out.println(device.id());
            System.out.println("Foo App has Rule Count: "+ i);
            System.out.println();

        }

        for (FlowEntry flowEntry : flowRuleService.getFlowEntriesById(FooAppId)){
            System.out.println(flowEntry.deviceId());
            System.out.println(flowEntry.selector());
            System.out.println(flowEntry.treatment());
            System.out.println();
        }
        System.out.println("-----------------------------");
        for (FlowEntry flowEntry : flowRuleService.getFlowEntriesById(FooAppId)){
            Criterion src_mac = flowEntry.selector().getCriterion(Criterion.Type.valueOf("ETH_SRC"));
            Criterion dst_mac = flowEntry.selector().getCriterion(Criterion.Type.valueOf("ETH_DST"));
            DeviceId flow_src_deviceid = DeviceId.deviceId("of:000000000000000"+src_mac.toString().charAt(src_mac.toString().length()-1));
            DeviceId flow_dst_deviceid = DeviceId.deviceId("of:000000000000000"+dst_mac.toString().charAt(dst_mac.toString().length()-1));
            System.out.println(flow_src_deviceid);
            System.out.println(flow_dst_deviceid);

        }





    }
}
