package org.foo.app;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;

import java.util.HashMap;
import java.util.Map;

@Service
@Command(scope = "onos", name = "mark",
        description = "1 - Core, 2 - Distribution - 3 - Access")


public class CommandMark extends AbstractShellCommand {
    @Argument(index = 0, name = "id", description = "deviceid", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String id;

    @Argument(index = 1, name = "role", description = "role", required = true, multiValued = false)
    Integer role;

    @Override
    protected void doExecute()  {
        String check;
        if (role == 1){
            check = "core";
            Marker.update(DeviceId.deviceId(id),check);
            log.info(Marker.get().toString());
        } else if (role == 2) {
            check = "distribution";
            Marker.update(DeviceId.deviceId(id),check);
            log.info(Marker.get().toString());
            //log.info(Marker.getKeys(Marker.get(),"distribution").toString());
        }
        else {
            check = "access";
            Marker.update(DeviceId.deviceId(id),check);
            log.info(Marker.get().toString());
        }

    }
}
