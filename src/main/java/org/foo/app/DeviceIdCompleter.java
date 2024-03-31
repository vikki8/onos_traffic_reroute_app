package org.foo.app;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;

import java.util.List;
import java.util.SortedSet;
@Service
public class DeviceIdCompleter implements Completer {
    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        // Delegate string completer
        StringsCompleter delegate = new StringsCompleter();
        DeviceService deviceService = AbstractShellCommand.get(DeviceService.class);

        SortedSet<String> strings = delegate.getStrings();
        for (Device device : deviceService.getAvailableDevices()) {
            strings.add(device.id().toString());
        }
        // Now let the completer do the work for figuring out what to offer.
        return delegate.complete(session, commandLine, candidates);
    }
}
