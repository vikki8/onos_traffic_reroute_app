package org.foo.app;

import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public final class Bandwidth {
    static DeviceService deviceService = AbstractShellCommand.get(DeviceService.class);
    static LinkService linkService = AbstractShellCommand.get(LinkService.class);

    private static Map<ConnectPoint, Integer> map = new HashMap<>();
    static {
        for (Device device :deviceService.getAvailableDevices()){
            for (Port port: deviceService.getPorts(device.id())){
                if(port.number().toString().equals("LOCAL")){
                    continue;
                }
                map.put(new ConnectPoint(device.id(),port.number()),10*1000000);

            }

        }
    }

    private Bandwidth() {

    }
    public static void update(String role, Integer value) {
        Set<DeviceId> switch_role = Marker.getKeys(Marker.get(), role);
        for(DeviceId deviceId: switch_role){
            for(Link link:linkService.getDeviceLinks(deviceId)){
                ConnectPoint src = link.src();
                ConnectPoint dst = link.dst();
                if(switch_role.contains(src.deviceId()) && switch_role.contains(dst.deviceId())){
                    map.replace(src,value*1000000);
                    map.replace(dst,value*1000000);
                }
            }
        }
        if (role.equals("distribution")){
            Set<DeviceId> switch_role_core = Marker.getKeys(Marker.get(), "core");
            for(DeviceId deviceId: switch_role){
                for(Link link:linkService.getDeviceLinks(deviceId)){
                    ConnectPoint src = link.src();
                    ConnectPoint dst = link.dst();
                    if(switch_role_core.contains(src.deviceId()) && switch_role.contains(dst.deviceId())){
                        map.replace(src,value*1000000);
                        map.replace(dst,value*1000000);
                    }
                }
            }
        }
        if (role.equals("access")){
            Set<DeviceId> switch_role_distribution = Marker.getKeys(Marker.get(), "distribution");
            for(DeviceId deviceId: switch_role){
                for(Link link:linkService.getDeviceLinks(deviceId)){
                    ConnectPoint src = link.src();
                    ConnectPoint dst = link.dst();
                    if(switch_role_distribution.contains(src.deviceId()) && switch_role.contains(dst.deviceId())){
                        map.replace(src,value*1000000);
                        map.replace(dst,value*1000000);
                    }
                }
            }
        }

    }

    public static Map<ConnectPoint, Integer>  get() {
        return map;
    }
}
