package org.foo.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
@Service
@Command(scope = "onos", name = "intentremove",
        description = "Sample Apache Karaf CLI command")
public class CommandRemoveIntent extends AbstractShellCommand {
    @Override
    protected void doExecute(){
        IntentService intentService = getService(IntentService.class);
        CoreService coreService = getService(CoreService.class);
        ApplicationId FooAppId = coreService.getAppId("org.foo.app");
        for(Intent intent: intentService.getIntentsByAppId(FooAppId)){
            intentService.withdraw(intent);
        }
    }
}