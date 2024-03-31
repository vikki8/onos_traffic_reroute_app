package org.foo.app;

import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class Marker {
    static DeviceService deviceService = AbstractShellCommand.get(DeviceService.class);

    private static Map<DeviceId, String> map = new HashMap<>();
    static {
        for (Device device :deviceService.getAvailableDevices()){
            map.put(device.id(),"access");
        }
    }

    private Marker() {

    }
    public static void update(DeviceId d, String s) {
        map.replace(d, s);
    }

    public static Map<DeviceId, String>  get() {
        return map;
    }

    public static Set<DeviceId> getKeys(
            Map<DeviceId, String> map, String value) {

        return map
                .entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), value))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

    }
}
