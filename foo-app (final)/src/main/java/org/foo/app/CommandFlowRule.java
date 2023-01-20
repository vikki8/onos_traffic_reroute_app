package org.foo.app;

import org.apache.karaf.shell.api.action.Argument;
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
    @Argument(index = 0, name = "intent", description = "Intent flow rule", required = true, multiValued = false)
    Boolean intent;
    @Override
    protected void doExecute() {
        FlowRuleService flowRuleService = getService(FlowRuleService.class);
        CoreService coreService = getService(CoreService.class);
        DeviceService deviceService = getService(DeviceService.class);
        ApplicationId FooAppId = coreService.getAppId("org.foo.app");
        ApplicationId IntentAppId = coreService.getAppId("org.onosproject.net.intent");
        if(intent){
            for (FlowEntry flowEntry : flowRuleService.getFlowEntriesById(IntentAppId)) {
                System.out.println(flowEntry.deviceId());
                System.out.println(flowEntry);
                System.out.println();
            }

            for (Device device : deviceService.getAvailableDevices()) {
                int i = 0;
                for (FlowEntry flowEntry : flowRuleService.getFlowEntries(device.id())) {
                    if (IntentAppId.id() == flowEntry.appId()) {
                        i++;
                    }
                }
                System.out.println(device.id());
                System.out.println("Intent has Rule Count: " + i);
                System.out.println();

            }
        }
        else {
            for (FlowEntry flowEntry : flowRuleService.getFlowEntriesById(FooAppId)) {
                System.out.println(flowEntry.deviceId());
                System.out.println(flowEntry.selector());
                System.out.println(flowEntry.treatment());
                System.out.println();
            }

            for (Device device : deviceService.getAvailableDevices()) {
                int i = 0;
                for (FlowEntry flowEntry : flowRuleService.getFlowEntries(device.id())) {
                    if (FooAppId.id() == flowEntry.appId()) {
                        i++;
                    }
                }
                System.out.println(device.id());
                System.out.println("Foo App has Rule Count: " + i);
                System.out.println();

            }
        }

    }
}
