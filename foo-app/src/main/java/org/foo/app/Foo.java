/*
 * Copyright 2022-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.foo.app;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import  org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.statistic.Load;
import org.onosproject.net.statistic.StatisticService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLOutput;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.foo.app.OsgiPropertyConstants.*;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class Foo {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static HttpURLConnection connection;
    private static Map<ConnectPoint, Integer> linkcapacitymap = new HashMap<>();
    private static Map<String, Double> sflowinoctetsmap = new HashMap<>();
    private static Map<String, Double> sflowoutoctetsmap = new HashMap<>();

    private final TrafficPacketProcessor processor = new TrafficPacketProcessor();
    ApplicationId appId;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StatisticService statisticService;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.foo.app",
                () -> log.info("Periscope down."));
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        withdrawIntercepts();
        log.info("Stopped");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class TrafficPacketProcessor implements PacketProcessor{

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            InboundPacket inpkt = context.inPacket();
            OutboundPacket outpkt = context.outPacket();

            Ethernet ethPkt = inpkt.parsed();

            if (ethPkt == null) {
                return;
            }

            MacAddress srcmacAddress = ethPkt.getSourceMAC();
            MacAddress dstmacAddress = ethPkt.getDestinationMAC();

            if (isControlPacket(ethPkt)) {
                return;
            }
            HostId srcid = HostId.hostId(srcmacAddress);
            HostId dstid = HostId.hostId(dstmacAddress);

            // Do not process LLDP MAC address in any way.
            if (dstid.mac().isLldp()) {
                return;
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                if (dstid.mac().isMulticast()) {
                    return;
                }
            }
            Host src = hostService.getHost(srcid);
            Host dst = hostService.getHost(dstid);
            if (dst == null) {
                flood(context);
                return;
            }
            if (inpkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    installRule(context, dst.location().port());
                }
                return;
            }
            Stream<Path> all_paths = topologyService.getKShortestPaths(topologyService.currentTopology(), inpkt.receivedFrom().deviceId(), dst.location().deviceId());
            List<Path> paths = all_paths.collect(Collectors.toList());

            if (paths.isEmpty()) {
                // If there are no paths, flood and bail.
                flood(context);
                return;
            }

            // Otherwise, pick a path that does not lead back to where we
            // came from; if no such path, flood and bail.


            Path path = determineNextAvailablePath(paths, inpkt.receivedFrom().port(),context);

             if (path == null) {
            log.warn("Don't know where to go from here {} for {} -> {}",
                    inpkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
            flood(context);
            return;
             }

            // Otherwise forward and be done with it.
            installRule(context, path.src().port());


        }
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }


    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }


    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    private void installRule(PacketContext context, PortNumber portNumber) {
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
                .matchEthSrc(inPkt.getSourceMAC())
                .matchEthDst(inPkt.getDestinationMAC());

        if (inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
            IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
            byte ipv4Protocol = ipv4Packet.getProtocol();
            Ip4Prefix matchIp4SrcPrefix =
                    Ip4Prefix.valueOf(ipv4Packet.getSourceAddress(),
                            Ip4Prefix.MAX_MASK_LENGTH);
            Ip4Prefix matchIp4DstPrefix =
                    Ip4Prefix.valueOf(ipv4Packet.getDestinationAddress(),
                            Ip4Prefix.MAX_MASK_LENGTH);
            selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPSrc(matchIp4SrcPrefix)
                    .matchIPDst(matchIp4DstPrefix);

            if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
                TCP tcpPacket = (TCP) ipv4Packet.getPayload();
                selectorBuilder.matchIPProtocol(ipv4Protocol)
                        .matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort()))
                        .matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
            }
            if (ipv4Protocol == IPv4.PROTOCOL_UDP) {
                UDP udpPacket = (UDP) ipv4Packet.getPayload();
                selectorBuilder.matchIPProtocol(ipv4Protocol)
                        .matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort()))
                        .matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
            }
            if (ipv4Protocol == IPv4.PROTOCOL_ICMP) {
                ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
                selectorBuilder.matchIPProtocol(ipv4Protocol)
                        .matchIcmpType(icmpPacket.getIcmpType())
                        .matchIcmpCode(icmpPacket.getIcmpCode());
            }
        }

        TrafficTreatment treatment;
        treatment = context.treatmentBuilder()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(10)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(10)
                .add();
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                forwardingObjective);

        packetOut(context, portNumber);

    }

    private Path determineNextAvailablePath(List<Path> paths, PortNumber notToPort, PacketContext context) {

        for (Path path : paths) {
            boolean exceeded = false;
            List<Link> pathlinks = path.links();
            for (Link link : pathlinks) {
                exceeded = threshold(link);
            }

            if (exceeded){
                continue;
            }
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }

        return null;
    }


    private Link threshold (){
        try{
            for (Link value : linkService.getLinks()) {
                Double utilization = linkutilization().get(value);
                if (utilization >25.0){
                    return value;
                }
            }
            return null;
        }catch (NullPointerException e){
            return null;
        }
    }

    private boolean threshold (Link value) {
        try{
            Double utilization = linkutilization().get(value);
            return utilization > 50;
        }catch (NullPointerException | IndexOutOfBoundsException e){
            return false;
        }
    }


    private Map<Link, Double> linkutilization() {

        try{
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

            for (int i = 0; i < cp.size(); i++) {
                double portinutilization = sflowinoctetsmap.get(inf.get(i).replace("\"", "")) * 8 / 10000000*100;

                onosIn_utilization .put(cp.get(i), portinutilization);
            }

            for (int i = 0; i < cp.size(); i++) {
                double portoututilization = sflowoutoctetsmap.get(inf.get(i).replace("\"", "")) * 8 / 10000000*100;
                onosOut_utilization.put(cp.get(i), portoututilization);
            }

            for (ConnectPoint connectPoint : cp) {
                for (Link link : linkService.getLinks(connectPoint)) {
                    ConnectPoint src = link.src();
                    ConnectPoint dst = link.dst();

                    double link_utilization = (onosOut_utilization.get(src) + onosIn_utilization.get(dst)) / 2;

                    if (!link_utilizationmap.containsKey(link)) {
                        link_utilizationmap.put(link, link_utilization);
                    }
                }
            }
            return link_utilizationmap;
        } catch (NullPointerException | JsonProcessingException | IndexOutOfBoundsException e){
            return null;
        }


    }

    public static StringBuffer api(String web) {

        try {
            BufferedReader reader;
            String line;
            StringBuffer responseContent = new StringBuffer();
            URL url = new URL(web);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int status = connection.getResponseCode();

            if (status == 200) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
            }
            return responseContent;
        }catch (IOException | IllegalStateException e){
            return null;
        }

    }


}



