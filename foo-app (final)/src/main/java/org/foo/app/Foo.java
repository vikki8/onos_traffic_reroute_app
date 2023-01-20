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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.time.StopWatch;
import org.onlab.packet.*;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.intent.*;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.URL;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;


import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.foo.app.OsgiPropertyConstants.*;

import static org.onosproject.net.flow.DefaultTrafficSelector.builder;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, service = Foo.class, property = {
        PACKET_OUT_ONLY + ":Boolean=" + PACKET_OUT_ONLY_DEFAULT,
        PACKET_OUT_OFPP_TABLE + ":Boolean=" + PACKET_OUT_OFPP_TABLE_DEFAULT,
        FLOW_TIMEOUT + ":Integer=" + FLOW_TIMEOUT_DEFAULT,
        FLOW_PRIORITY  + ":Integer=" + FLOW_PRIORITY_DEFAULT,
        IPV6_FORWARDING + ":Boolean=" + IPV6_FORWARDING_DEFAULT,
        MATCH_DST_MAC_ONLY + ":Boolean=" + MATCH_DST_MAC_ONLY_DEFAULT,
        MATCH_VLAN_ID + ":Boolean=" + MATCH_VLAN_ID_DEFAULT,
        MATCH_IPV4_ADDRESS + ":Boolean=" + MATCH_IPV4_ADDRESS_DEFAULT,
        MATCH_IPV4_DSCP + ":Boolean=" + MATCH_IPV4_DSCP_DEFAULT,
        MATCH_IPV6_ADDRESS + ":Boolean=" + MATCH_IPV6_ADDRESS_DEFAULT,
        MATCH_IPV6_FLOW_LABEL + ":Boolean=" + MATCH_IPV6_FLOW_LABEL_DEFAULT,
        MATCH_TCP_UDP_PORTS + ":Boolean=" + MATCH_TCP_UDP_PORTS_DEFAULT,
        MATCH_ICMP_FIELDS + ":Boolean=" + MATCH_ICMP_FIELDS_DEFAULT,
        IGNORE_IPV4_MCAST_PACKETS + ":Boolean=" + IGNORE_IPV4_MCAST_PACKETS_DEFAULT,
        RECORD_METRICS + ":Boolean=" + RECORD_METRICS_DEFAULT,
        INHERIT_FLOW_TREATMENT + ":Boolean=" + INHERIT_FLOW_TREATMENT_DEFAULT,
        LIMIT + ":Integer=" + LIMIT_DEFAULT,
        INTENT + ":Boolean=" + INTENT_DEFAULT,
        FORWARDING + ":Integer=" + FORWARDING_DEFAULT,
        CAMPUS + ":Boolean=" + CAMPUS_DEFAULT
})
public class Foo {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Log log1 = new Log();
    private static int logi=0;

    private TrafficPacketProcessor processor = new TrafficPacketProcessor();
    private LinkListener linkListener = new InternalLinkListener();
    private TopologyListener topologyListener = new InternalTopologyListener();

    private final ProviderId PID = new ProviderId("core", "org.onosproject.core");
    protected static final LinkWeigher DEFAULT_WEIGHER = new HopCountLinkWeigher();
    ApplicationId appId;

    private static final int DROP_RULE_TIMEOUT = 300;
    private static final EnumSet<IntentState> WITHDRAWN_STATES = EnumSet.of(IntentState.WITHDRAWN,
            IntentState.WITHDRAWING,
            IntentState.WITHDRAW_REQ);

    /** Enable packet-out only forwarding; default is false. */
    private boolean packetOutOnly = PACKET_OUT_ONLY_DEFAULT;

    /** Enable first packet forwarding using OFPP_TABLE port instead of PacketOut with actual port; default is false. */
    private boolean packetOutOfppTable = PACKET_OUT_OFPP_TABLE_DEFAULT;

    /** Configure Flow Timeout for installed flow rules; default is 10 sec. */
    private int flowTimeout = FLOW_TIMEOUT_DEFAULT;

    /** Configure Flow Priority for installed flow rules; default is 10. */
    private int flowPriority = FLOW_PRIORITY_DEFAULT;

    /** Enable IPv6 forwarding; default is false. */
    private boolean ipv6Forwarding = IPV6_FORWARDING_DEFAULT;

    /** Enable matching Dst Mac Only; default is false. */
    private boolean matchDstMacOnly = MATCH_DST_MAC_ONLY_DEFAULT;

    /** Enable matching Vlan ID; default is false. */
    private boolean matchVlanId = MATCH_VLAN_ID_DEFAULT;

    /** Enable matching IPv4 Addresses; default is false. */
    private boolean matchIpv4Address = MATCH_IPV4_ADDRESS_DEFAULT;

    /** Enable matching IPv4 DSCP and ECN; default is false. */
    private boolean matchIpv4Dscp = MATCH_IPV4_DSCP_DEFAULT;

    /** Enable matching IPv6 Addresses; default is false. */
    private boolean matchIpv6Address = MATCH_IPV6_ADDRESS_DEFAULT;

    /** Enable matching IPv6 FlowLabel; default is false. */
    private boolean matchIpv6FlowLabel = MATCH_IPV6_FLOW_LABEL_DEFAULT;

    /** Enable matching TCP/UDP ports; default is false. */
    private boolean matchTcpUdpPorts = MATCH_TCP_UDP_PORTS_DEFAULT;

    /** Enable matching ICMPv4 and ICMPv6 fields; default is false. */
    private boolean matchIcmpFields = MATCH_ICMP_FIELDS_DEFAULT;

    /** Ignore (do not forward) IPv4 multicast packets; default is false. */
    private boolean ignoreIPv4Multicast = IGNORE_IPV4_MCAST_PACKETS_DEFAULT;

    /** Enable record metrics for reactive forwarding. */
    private boolean recordMetrics = RECORD_METRICS_DEFAULT;

    /** Enable use of builder from packet context to define flow treatment; default is false. */
    private boolean inheritFlowTreatment = INHERIT_FLOW_TREATMENT_DEFAULT;

    /** Configure Maximum path computation; default is 15. */
    private int limit = LIMIT_DEFAULT;

    /** Enable Path installation via Intent; default is false. */
    private boolean intent = INTENT_DEFAULT;

    /** Configure traffic forwarding method (1 - Shortest , 2 - Link Utilization , 3 - Round Robin); default is 1. */
    private int forwarding = FORWARDING_DEFAULT;

    /** Enable Path Filtering for Campus LAN Topology; default is true. */
    private boolean campus = CAMPUS_DEFAULT;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

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
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PathService pathService;

    private ExecutorService blackHoleExecutor;
    private MetricCollectorScheduler schedule = new MetricCollectorScheduler();
    Map<Link, Double> link_utilizationmap = new HashMap<>();
    Map<Link, Double> link_utilizationpercentagemap = new HashMap<>();
    Map<Key, Path> intentpathtrackermap = new HashMap<>();

    @Activate
    protected void activate(ComponentContext context) {
        blackHoleExecutor = newSingleThreadExecutor(groupedThreads("home/tein/foo-app",
                "black-hole-fixer",
                log));
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("org.foo.app",
                () -> log.info("Periscope down."));
        packetService.addProcessor(processor, PacketProcessor.director(2));
        linkService.addListener(linkListener);
        topologyService.addListener(topologyListener);
        readComponentConfiguration(context);
        requestIntercepts();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        linkService.removeListener(linkListener);
        topologyService.removeListener(topologyListener);
        blackHoleExecutor.shutdown();
        blackHoleExecutor = null;
        processor = null;
        schedule.timer.cancel();
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        readComponentConfiguration(context);
        requestIntercepts();
    }

    /**
     * Request packet in via packet service.
     */

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        selector.matchEthType(Ethernet.TYPE_IPV6);
        if (ipv6Forwarding) {
            packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        } else {
            packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        }
    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_IPV6);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Extracts properties from the component configuration context.
     *
     * @param context the component context
     */
    private void readComponentConfiguration(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();

        Boolean packetOutOnlyEnabled =
                Tools.isPropertyEnabled(properties, PACKET_OUT_ONLY);
        if (packetOutOnlyEnabled == null) {
            log.info("Packet-out is not configured, " +
                    "using current value of {}", packetOutOnly);
        } else {
            packetOutOnly = packetOutOnlyEnabled;
            log.info("Configured. Packet-out only forwarding is {}",
                    packetOutOnly ? "enabled" : "disabled");
        }

        Boolean packetOutOfppTableEnabled =
                Tools.isPropertyEnabled(properties, PACKET_OUT_OFPP_TABLE);
        if (packetOutOfppTableEnabled == null) {
            log.info("OFPP_TABLE port is not configured, " +
                    "using current value of {}", packetOutOfppTable);
        } else {
            packetOutOfppTable = packetOutOfppTableEnabled;
            log.info("Configured. Forwarding using OFPP_TABLE port is {}",
                    packetOutOfppTable ? "enabled" : "disabled");
        }

        Boolean ipv6ForwardingEnabled =
                Tools.isPropertyEnabled(properties, IPV6_FORWARDING);
        if (ipv6ForwardingEnabled == null) {
            log.info("IPv6 forwarding is not configured, " +
                    "using current value of {}", ipv6Forwarding);
        } else {
            ipv6Forwarding = ipv6ForwardingEnabled;
            log.info("Configured. IPv6 forwarding is {}",
                    ipv6Forwarding ? "enabled" : "disabled");
        }

        Boolean matchDstMacOnlyEnabled =
                Tools.isPropertyEnabled(properties, MATCH_DST_MAC_ONLY);
        if (matchDstMacOnlyEnabled == null) {
            log.info("Match Dst MAC is not configured, " +
                    "using current value of {}", matchDstMacOnly);
        } else {
            matchDstMacOnly = matchDstMacOnlyEnabled;
            log.info("Configured. Match Dst MAC Only is {}",
                    matchDstMacOnly ? "enabled" : "disabled");
        }

        Boolean matchVlanIdEnabled =
                Tools.isPropertyEnabled(properties, MATCH_VLAN_ID);
        if (matchVlanIdEnabled == null) {
            log.info("Matching Vlan ID is not configured, " +
                    "using current value of {}", matchVlanId);
        } else {
            matchVlanId = matchVlanIdEnabled;
            log.info("Configured. Matching Vlan ID is {}",
                    matchVlanId ? "enabled" : "disabled");
        }

        Boolean matchIpv4AddressEnabled =
                Tools.isPropertyEnabled(properties, MATCH_IPV4_ADDRESS);
        if (matchIpv4AddressEnabled == null) {
            log.info("Matching IPv4 Address is not configured, " +
                    "using current value of {}", matchIpv4Address);
        } else {
            matchIpv4Address = matchIpv4AddressEnabled;
            log.info("Configured. Matching IPv4 Addresses is {}",
                    matchIpv4Address ? "enabled" : "disabled");
        }

        Boolean matchIpv4DscpEnabled =
                Tools.isPropertyEnabled(properties, MATCH_IPV4_DSCP);
        if (matchIpv4DscpEnabled == null) {
            log.info("Matching IPv4 DSCP and ECN is not configured, " +
                    "using current value of {}", matchIpv4Dscp);
        } else {
            matchIpv4Dscp = matchIpv4DscpEnabled;
            log.info("Configured. Matching IPv4 DSCP and ECN is {}",
                    matchIpv4Dscp ? "enabled" : "disabled");
        }

        Boolean matchIpv6AddressEnabled =
                Tools.isPropertyEnabled(properties, MATCH_IPV6_ADDRESS);
        if (matchIpv6AddressEnabled == null) {
            log.info("Matching IPv6 Address is not configured, " +
                    "using current value of {}", matchIpv6Address);
        } else {
            matchIpv6Address = matchIpv6AddressEnabled;
            log.info("Configured. Matching IPv6 Addresses is {}",
                    matchIpv6Address ? "enabled" : "disabled");
        }

        Boolean matchIpv6FlowLabelEnabled =
                Tools.isPropertyEnabled(properties, MATCH_IPV6_FLOW_LABEL);
        if (matchIpv6FlowLabelEnabled == null) {
            log.info("Matching IPv6 FlowLabel is not configured, " +
                    "using current value of {}", matchIpv6FlowLabel);
        } else {
            matchIpv6FlowLabel = matchIpv6FlowLabelEnabled;
            log.info("Configured. Matching IPv6 FlowLabel is {}",
                    matchIpv6FlowLabel ? "enabled" : "disabled");
        }

        Boolean matchTcpUdpPortsEnabled =
                Tools.isPropertyEnabled(properties, MATCH_TCP_UDP_PORTS);
        if (matchTcpUdpPortsEnabled == null) {
            log.info("Matching TCP/UDP fields is not configured, " +
                    "using current value of {}", matchTcpUdpPorts);
        } else {
            matchTcpUdpPorts = matchTcpUdpPortsEnabled;
            log.info("Configured. Matching TCP/UDP fields is {}",
                    matchTcpUdpPorts ? "enabled" : "disabled");
        }

        Boolean matchIcmpFieldsEnabled =
                Tools.isPropertyEnabled(properties, MATCH_ICMP_FIELDS);
        if (matchIcmpFieldsEnabled == null) {
            log.info("Matching ICMP (v4 and v6) fields is not configured, " +
                    "using current value of {}", matchIcmpFields);
        } else {
            matchIcmpFields = matchIcmpFieldsEnabled;
            log.info("Configured. Matching ICMP (v4 and v6) fields is {}",
                    matchIcmpFields ? "enabled" : "disabled");
        }

        Boolean ignoreIpv4McastPacketsEnabled =
                Tools.isPropertyEnabled(properties, IGNORE_IPV4_MCAST_PACKETS);
        if (ignoreIpv4McastPacketsEnabled == null) {
            log.info("Ignore IPv4 multi-cast packet is not configured, " +
                    "using current value of {}", ignoreIPv4Multicast);
        } else {
            ignoreIPv4Multicast = ignoreIpv4McastPacketsEnabled;
            log.info("Configured. Ignore IPv4 multicast packets is {}",
                    ignoreIPv4Multicast ? "enabled" : "disabled");
        }
        Boolean recordMetricsEnabled =
                Tools.isPropertyEnabled(properties, RECORD_METRICS);
        if (recordMetricsEnabled == null) {
            log.info("IConfigured. Ignore record metrics  is {} ," +
                    "using current value of {}", recordMetrics);
        } else {
            recordMetrics = recordMetricsEnabled;
            log.info("Configured. record metrics  is {}",
                    recordMetrics ? "enabled" : "disabled");
        }

        flowTimeout = Tools.getIntegerProperty(properties, FLOW_TIMEOUT, FLOW_TIMEOUT_DEFAULT);
        log.info("Configured. Flow Timeout is configured to {} seconds", flowTimeout);

        flowPriority = Tools.getIntegerProperty(properties, FLOW_PRIORITY, FLOW_PRIORITY_DEFAULT);
        log.info("Configured. Flow Priority is configured to {}", flowPriority);

        Boolean inheritFlowTreatmentEnabled =
                Tools.isPropertyEnabled(properties, INHERIT_FLOW_TREATMENT);
        if (inheritFlowTreatmentEnabled == null) {
            log.info("Inherit flow treatment is not configured, " +
                    "using current value of {}", inheritFlowTreatment);
        } else {
            inheritFlowTreatment = inheritFlowTreatmentEnabled;
            log.info("Configured. Inherit flow treatment is {}",
                    inheritFlowTreatment ? "enabled" : "disabled");
        }

        limit = Tools.getIntegerProperty(properties,LIMIT,LIMIT_DEFAULT);
        log.info("Configured. Limit is configured to {}", limit);

        Boolean intentEnabled =
                Tools.isPropertyEnabled(properties, INTENT);
        if (intentEnabled == null) {
            log.info("Intent is not configured, " +
                    "using current value of {}", intent);
        } else {
            intent = intentEnabled;
            log.info("Configured. Intent is {}",
                    intent ? "enabled" : "disabled");
        }

        forwarding = Tools.getIntegerProperty(properties,FORWARDING,FORWARDING_DEFAULT);
        log.info("Configured. Forwarding Method is configured to {}", forwarding);

        Boolean campusEnabled =
                Tools.isPropertyEnabled(properties, CAMPUS);
        if (campusEnabled == null) {
            log.info("Campus LAN Topology Path Filtering is not configured, " +
                    "using current value of {}", campus);
        } else {
            campus = campusEnabled;
            log.info("Configured. Campus LAN Topology Path Filtering is {}",
                    campus ? "enabled" : "disabled");
        }
    }
    /**
     * Output certain log to log file for better visibility
     * @param stringtoWrite
     * @throws IOException
     */
    public static void usingBufferedWriter(String stringtoWrite) throws IOException
    {
        FileWriter fileWriter = new FileWriter("/home/tein/bot-python/log.txt", true); //Set true for append mode
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println(stringtoWrite);  //New line
        printWriter.close();
    }

    /**
     * Packet processor responsible for forwarding packets along their paths.
     */

    private class TrafficPacketProcessor implements PacketProcessor {

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
            // Skip IPv6 multicast packet when IPv6 forward is disabled.
            if (!ipv6Forwarding && isIpv6Multicast(ethPkt)) {
                return;
            }

            HostId srcid = HostId.hostId(srcmacAddress, VlanId.vlanId(ethPkt.getVlanID()));
            HostId dstid = HostId.hostId(dstmacAddress, VlanId.vlanId(ethPkt.getVlanID()));


            // Do not process LLDP MAC address in any way.
            if (dstid.mac().isLldp()) {
                return;
            }
            // Do not process IPv4 multicast packets, let mfwd handle them
            if (ignoreIPv4Multicast && ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
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
            if(!intent) {

                if (inpkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                    if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                        installRule(context, dst.location().port());
                    }
                    return;
                }

                Instant start1 = Instant.now();
                Instant start = Instant.now();
                //Calculate shortest and shortest+1 paths for computation
                Set<Path> shortest_path = topologyService.getPaths
                        (topologyService.currentTopology(), inpkt.receivedFrom().deviceId(), dst.location().deviceId());
                Set<Path> paths = topologyService.getKShortestPaths
                                (topologyService.currentTopology(), inpkt.receivedFrom().deviceId(), dst.location().deviceId()).
                        limit(limit)
                        .filter(path -> path.cost() <= shortest_path.iterator().next().cost() + 1)
                        .sorted((o1, o2) -> {
                            if (o1.cost() == o2.cost()) {
                                if (o1.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)
                                        > o2.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            } else if (o1.cost() > o2.cost()) {
                                return 1;
                            } else {
                                return -1;
                            }
                        })
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Instant end = Instant.now();

                //Filter calculated paths if topology is an Enterprise Campus LAN Topology
                if (campus) {
                    filterpaths(paths);
                }

                if (paths.isEmpty()) {
                    // If there are no paths, flood and bail.
                    flood(context);
                    return;
                }

                // Otherwise, pick a path that does not lead back to where we came from
                log1.setThreshold(false);
                Path path = null;

                //FallBack Method - If Database or Telemetry Collector fails
                if (forwarding == 0) {
                    path = pickForwardPathIfPossible(paths, inpkt.receivedFrom().port());

                    //If no such path, Try to forward it to the shortest path. Otherwise, flood and bail
                    if (path == null) {
                        log.warn("Don't know where to go from here {} for {} -> {}", inpkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
                        flood(context);
                        return;
                    }
                }

                //Non Load Balancing
                if (forwarding == 1) {
                    path = pickForwardPathIfPossible3(paths, inpkt.receivedFrom().port());

                    //If no such path, Try to forward it to the shortest path. Otherwise, flood and bail
                    if (path == null) {
                        log.warn("Don't know where to go from here {} for {} -> {}", inpkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
                        flood(context);
                        return;
                    }
                }
                //Lowest Utilization Load Balancing
                if (forwarding == 2) {
                    path = pickForwardPathIfPossible2(paths, inpkt.receivedFrom().port());

                    //If no such path, Try to forward it to the shortest path. Otherwise, flood and bail
                    if (path == null) {
                        log.warn("Don't know where to go from here {} for {} -> {}", inpkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
                        flood(context);
                        return;
                    }
                }
                //Round-Robin Load Balancing
                if (forwarding == 3) {
                    path = pickForwardPathIfPossible4(paths, inpkt.receivedFrom().port(), shortest_path.size(), Counter.COUNT);

                    //If no such path, Try to forward it to the shortest path. Otherwise, flood and bail
                    if (path == null) {
                        log.warn("Don't know where to go from here {} for {} -> {}", inpkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
                        flood(context);
                        return;
                    }

                    if (Marker.get().get(path.src().deviceId()).equals("access")) {
                        Counter.increment();
                    }
                }
                //Path Tracking and Logging
                Instant end1 = Instant.now();
                Duration timeElapsed = Duration.between(start, end);
                Duration timeElapsed1 = Duration.between(start1, end1);

                String graphlog = logi + " " + timeElapsed.toString().replace("PT","").
                        replace("S","") + " " + timeElapsed1.toString().replace("PT","").
                        replace("S","");
                try {
                    usingBufferedWriter(graphlog); logi++;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (forwarding == 1 || forwarding == 2 || forwarding == 3) {
                    if (Marker.get().get(path.src().deviceId()).equals("access")) {
                        log.info(srcid.mac().toString() + " ---> " + dstid.mac().toString());
                        reroutelog();
                        if (forwarding == 3) {
                            log.info("Counter Value: " + Counter.COUNT);
                        }
                        log.info("Time taken to calculate the path: " + timeElapsed1);
                    }
                }
                // Otherwise forward and be done with it.
                installRule(context, path.src().port());
            }
            else{
                log1.setThreshold(false);

                log.info(srcid.mac().toString() + " ---> " + dstid.mac().toString());

                //withdrawIntentBelowThreshold(srcid,dstid);
                setUpConnectivity(context, srcid, dstid);

                forwardPacketToDst(context, dst);
            }

        }
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    // Indicated whether this is an IPv6 multicast packet.
    private boolean isIpv6Multicast(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
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

    // Install a rule forwarding the packet to the specified port.
    private void installRule(PacketContext context, PortNumber portNumber) {
        //
        // We don't support (yet) buffer IDs in the Flow Service so
        // packet out first.
        //
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        // If matchDstMacOnly
        //    Create flows matching dstMac only
        // Else
        //    Create flows with default matching and include configured fields
        //
        if (matchDstMacOnly) {
            selectorBuilder.matchEthDst(inPkt.getDestinationMAC());
        } else {
            selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
                    .matchEthSrc(inPkt.getSourceMAC())
                    .matchEthDst(inPkt.getDestinationMAC());

            // If configured Match Vlan ID
            if (matchVlanId && inPkt.getVlanID() != Ethernet.VLAN_UNTAGGED) {
                selectorBuilder.matchVlanId(VlanId.vlanId(inPkt.getVlanID()));
            }

            //
            // If configured and EtherType is IPv4 - Match IPv4 and
            // TCP/UDP/ICMP fields
            //
            if (matchIpv4Address && inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
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

                if (matchIpv4Dscp) {
                    byte dscp = ipv4Packet.getDscp();
                    byte ecn = ipv4Packet.getEcn();
                    selectorBuilder.matchIPDscp(dscp).matchIPEcn(ecn);
                }

                if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_TCP) {
                    TCP tcpPacket = (TCP) ipv4Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv4Protocol)
                            .matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort()))
                            .matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
                }
                if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv4Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv4Protocol)
                            .matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort()))
                            .matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
                }
                if (matchIcmpFields && ipv4Protocol == IPv4.PROTOCOL_ICMP) {
                    ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv4Protocol)
                            .matchIcmpType(icmpPacket.getIcmpType())
                            .matchIcmpCode(icmpPacket.getIcmpCode());
                }
            }

            //
            // If configured and EtherType is IPv6 - Match IPv6 and
            // TCP/UDP/ICMP fields
            //
            if (matchIpv6Address && inPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Packet = (IPv6) inPkt.getPayload();
                byte ipv6NextHeader = ipv6Packet.getNextHeader();
                Ip6Prefix matchIp6SrcPrefix =
                        Ip6Prefix.valueOf(ipv6Packet.getSourceAddress(),
                                Ip6Prefix.MAX_MASK_LENGTH);
                Ip6Prefix matchIp6DstPrefix =
                        Ip6Prefix.valueOf(ipv6Packet.getDestinationAddress(),
                                Ip6Prefix.MAX_MASK_LENGTH);
                selectorBuilder.matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Src(matchIp6SrcPrefix)
                        .matchIPv6Dst(matchIp6DstPrefix);

                if (matchIpv6FlowLabel) {
                    selectorBuilder.matchIPv6FlowLabel(ipv6Packet.getFlowLabel());
                }

                if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_TCP) {
                    TCP tcpPacket = (TCP) ipv6Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv6NextHeader)
                            .matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort()))
                            .matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
                }
                if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv6Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv6NextHeader)
                            .matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort()))
                            .matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
                }
                if (matchIcmpFields && ipv6NextHeader == IPv6.PROTOCOL_ICMP6) {
                    ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv6NextHeader)
                            .matchIcmpv6Type(icmp6Packet.getIcmpType())
                            .matchIcmpv6Code(icmp6Packet.getIcmpCode());
                }
            }
        }
        TrafficTreatment treatment;
        if (inheritFlowTreatment) {
            treatment = context.treatmentBuilder()
                    .setOutput(portNumber)
                    .build();
        } else {
            treatment = DefaultTrafficTreatment.builder()
                    .setOutput(portNumber)
                    .build();
        }

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                forwardingObjective);

        //
        // If packetOutOfppTable
        //  Send packet back to the OpenFlow pipeline to match installed flow
        // Else
        //  Send packet direction on the appropriate port
        //
        if (packetOutOfppTable) {
            packetOut(context, PortNumber.TABLE);
        } else {
            packetOut(context, portNumber);
        }
    }

    private void forwardPacketToDst(PacketContext context, Host dst) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(dst.location().port()).build();
        OutboundPacket packet = new DefaultOutboundPacket(dst.location().deviceId(),
                treatment, context.inPacket().unparsed());
        packetService.emit(packet);
        //log.info("sending packet: {}", packet);
    }

    // Install a 2-way P2P Intent to the specified path.
    private void setUpConnectivity(PacketContext context, HostId srcId, HostId dstId) {
        //Selectors match on the MAC addresses
        TrafficSelector selectorSrcDst = builder()
                .matchEthSrc(srcId.mac())
                .matchEthDst(dstId.mac())
                .build();
        TrafficSelector selectorDstSrc = builder()
                .matchEthSrc(dstId.mac())
                .matchEthDst(srcId.mac())
                .build();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

        // We need to define 2 keys since we use two PointToPoint intents
        Key keySrcDst, keyDstSrc;

        keySrcDst = Key.of(srcId.toString() + dstId.toString(), appId);
        keyDstSrc = Key.of(dstId.toString() + srcId.toString(), appId);

        ConnectivityIntent intentSrcDst =
                (ConnectivityIntent) intentService.getIntent(keySrcDst);
        ConnectivityIntent intentDstSrc =
                (ConnectivityIntent) intentService.getIntent(keyDstSrc);

        // Calculate the connect point each host is connected to
        FilteredConnectPoint filteredIngressCP = getFilteredConnectPoint(srcId);
        FilteredConnectPoint filteredEgressCP = getFilteredConnectPoint(dstId);

        submitIntent(selectorSrcDst, treatment, keySrcDst, intentSrcDst,
                filteredIngressCP, filteredEgressCP);
        submitIntent(selectorDstSrc, treatment, keyDstSrc, intentDstSrc,
                filteredEgressCP, filteredIngressCP);
    }

    private void submitIntent(TrafficSelector selector, TrafficTreatment treatment,
                              Key key, ConnectivityIntent intent,
                              FilteredConnectPoint filteredIngressCP,
                              FilteredConnectPoint filteredEgressCP) {
        if (intent == null || WITHDRAWN_STATES.contains(intentService.getIntentState(key))
                || intentService.getIntentState(key) == IntentState.FAILED) {

            //The intent is in the withdrawn state, we need to re-add it
            if(filteredIngressCP.connectPoint().elementId().equals(filteredEgressCP.connectPoint().elementId())) {
                PointToPointIntent ptpIntent = PointToPointIntent.builder()
                        .appId(appId)
                        .key(key)
                        .filteredIngressPoint(filteredIngressCP)
                        .filteredEgressPoint(filteredEgressCP)
                        .selector(selector)
                        .treatment(treatment)
                        .build();
                intentService.submit(ptpIntent);
            }else {
                Instant start1 = Instant.now();
                Instant start = Instant.now();
                Set<Path> shortest_path = topologyService.getPaths
                        (topologyService.currentTopology(),filteredIngressCP.connectPoint().deviceId(),filteredEgressCP.connectPoint().deviceId());
                Set<Path> paths = topologyService.getKShortestPaths
                                (topologyService.currentTopology(),filteredIngressCP.connectPoint().deviceId(),filteredEgressCP.connectPoint().deviceId()).
                        limit(limit)
                        .filter(path -> path.cost() <= shortest_path.iterator().next().cost()+1)
                        .sorted((o1, o2) -> {
                            if(o1.cost() == o2.cost()) {
                                if (o1.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)
                                        > o2.links().get(0).dst().deviceId().toString().charAt(o1.links().get(0).dst().deviceId().toString().length() - 1)) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                            else if (o1.cost() > o2.cost()){
                                return 1;
                            }
                            else {
                                return -1;
                            }
                        })
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                 Instant end = Instant.now();

                //Filter calculated paths if topology is an Enterprise Campus LAN Topology
                if (campus){
                    filterpaths(paths);
                }

                //Path Tracking & Logging
                Path path = null;

                if (forwarding == 1) {
                    path = pickForwardPathIfPossibleIntent1(paths);
                }
                if (forwarding == 3) {
                    path = pickForwardPathIfPossibleIntent2(paths, shortest_path.size(), Counter.COUNT);
                }

                Instant end1 = Instant.now();
                Duration timeElapsed = Duration.between(start, end);
                Duration timeElapsed1 = Duration.between(start1, end1);
              
                String graphlog = logi + " " + timeElapsed.toString().replace("PT","").
                        replace("S","")+ " " + timeElapsed1.toString().replace("PT","").
                        replace("S","");
                try {
                    usingBufferedWriter(graphlog); logi++;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                log.info("Time taken to calculate the path: "+ timeElapsed1);

                intentpathtrackermap.put(key,path);

                reroutelog();
                if (forwarding == 3) {
                    Counter.increment();
                }

                PointToPointIntent ptpIntent = PointToPointIntent.builder()
                        .appId(appId)
                        .key(key)
                        .filteredIngressPoint(filteredIngressCP)
                        .filteredEgressPoint(filteredEgressCP)
                        .selector(selector)
                        .treatment(treatment)
                        .suggestedPath(path.links())
                        .build();
                intentService.submit(ptpIntent);
            }

        }
        //TODO check master for IntentState.FAILED state handling with flowObjective
    }

    private void withdrawIntentBelowThreshold(HostId srcId, HostId dstId){
        ArrayList<Double> minarr = new ArrayList<>();
        double min;
        Key keySrcDst, keyDstSrc;
        keySrcDst = Key.of(srcId.toString() + dstId.toString(), appId);
        keyDstSrc = Key.of(dstId.toString() + srcId.toString(), appId);
        Path path_srcdst = intentpathtrackermap.getOrDefault(keySrcDst,null);
        Path path_dstsrc = intentpathtrackermap.getOrDefault(keyDstSrc ,null);
        if (path_srcdst != null) {
            for(Link link: path_srcdst.links()){
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }

            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if((Double)newarr[0] <= 6){
                intentService.withdraw(intentService.getIntent(keySrcDst));
                log.info(srcId.toString() + " ---> " + dstId.toString() + " : " + " INTENT WITHDRAWN");
            }
            minarr.clear();
        }
        if (path_dstsrc != null) {
            for(Link link: path_dstsrc.links()){
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }

            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if((Double)newarr[0] <= 6){
                intentService.withdraw(intentService.getIntent(keyDstSrc));
                log.info(dstId.toString() + " ---> " + srcId.toString() + " : " + " INTENT WITHDRAWN");
            }
            minarr.clear();
        }
    }

    private FilteredConnectPoint getFilteredConnectPoint(HostId hostId) {
        Host h = hostService.getHost(hostId);
        return new FilteredConnectPoint(new ConnectPoint(h.location().deviceId(),h.location().port()));
    }

    public class MetricCollectorScheduler {
        Toolkit toolkit;
        Timer timer;

        public MetricCollectorScheduler() {
            toolkit = Toolkit.getDefaultToolkit();
            timer = new Timer();
            timer.schedule(new RemindTask(),
                    1*2000,        // initial delay
                    1 * 10000);   // subsequent rate
        }

        class RemindTask extends TimerTask {
            int numWarningBeeps = 1;

            public void run() {
                if (numWarningBeeps != 0) {
                    //toolkit.beep();
                    System.out.println("Running background process as usual !");
                    System.out.println("Executing Database data request !");
                    // numWarningBeeps--;
                    String url1 = "http://localhost:9090/api/v1/query?query=sflow_ifoutoctets";

                    final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

                    Map<ConnectPoint, Double> Outportutilization_percentage = new HashMap<>();
                    Map<ConnectPoint, Double> Outportutilization = new HashMap<>();

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

                        double port_Oututilization_percentage;
                        double port_Oututilization;

                        try {
                            port_Oututilization = (Bandwidth.get().get(connectPoint) - (metric_value_out.asDouble()*8)) / 1000000;
                            port_Oututilization_percentage = 100 -(metric_value_out.asDouble() * 8 / Bandwidth.get().get(connectPoint)*100);

                        }catch (Exception e){
                            port_Oututilization = (10000000 - (metric_value_out.asDouble()*8)) / 1000000;
                            port_Oututilization_percentage = 100 -(metric_value_out.asDouble() * 8 / 10000000*100);
                        }

                        Outportutilization_percentage.put(connectPoint,port_Oututilization_percentage);
                        Outportutilization.put(connectPoint, port_Oututilization);
                    }
                    String check = "";

                    for (Link link : linkService.getLinks()) {
                        ConnectPoint src = link.src();
                        ConnectPoint dst = link.dst();

                        double link_utilization;
                        double link_utilization_percentage;
                        try{
                            link_utilization = Outportutilization.get(src);
                            link_utilization_percentage = Outportutilization_percentage.get(src);

                        }catch (NullPointerException e){
                            break;
                        }

                        if (!link_utilizationmap.containsKey(link)) {
                            link_utilizationmap.put(link, link_utilization);
                            link_utilizationpercentagemap.put(link,link_utilization_percentage);
                        }
                        else{
                            link_utilizationmap.replace(link,link_utilization);
                            link_utilizationpercentagemap.replace(link,link_utilization_percentage);
                        }
                        check += link.src() + " ---> " + link.dst() + " : " + link_utilizationmap.get(link) + " Mbits\n";
                    }
                    log.info(check);


                } else {
                    toolkit.beep();
                    System.out.println("Caught Exception, Meter stats collection stopped");
                    //timer.cancel(); //Not necessary because we call System.exit at Deactivate()
                    System.exit(0);   //Stops the AWT thread (and everything else)
                }
            }
        }
    }

    private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
        for (Path path : paths) {
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return null;
    }

    private Path pickForwardPathIfPossible1(Set<Path> paths, PortNumber notToPort) { //shortest path & no rerouting
        ArrayList<Double> minarr = new ArrayList<>();
        double min;
        for (Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }
            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if (!path.src().port().equals(notToPort)) {
                log1.setOriginal(path);
                log1.setValueOriginal((Double)newarr[0]);
                return path;
            }
        }
        return null;
    }

    private Path pickForwardPathIfPossible2(Set<Path> paths, PortNumber notToPort) {
        //Least utilized path per hop initialization & rerouting
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator1 bvc = new ValueComparator1(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min1;
        ArrayList<Double> minarr1 = new ArrayList<>();

        for (Path path1 : paths) {

            for (Link link : path1.links()) {
                min1 = link_utilizationmap.get(link);
                minarr1.add(min1);
            }

            Object[] newarr = minarr1.toArray();
            Arrays.sort(newarr);
            pathmap.put(path1, (Double) newarr[0]);
            minarr1.clear();
        }
        sorted_map.putAll(pathmap);
        List<Map.Entry> keyList = new ArrayList<>(sorted_map.entrySet());

        for (int i=0;i<keyList.size();i++) {
            Path path = (Path) keyList.get(i).getKey();
            double util = (Double) keyList.get(i).getValue();
            /*
            if(util <= 6){
                log1.setThreshold(true);
                log1.setOriginal(path);
                log1.setValueOriginal(util);
                log1.setReroute((Path) keyList.get(i+1).getKey());
                log1.setValueReroute((Double) keyList.get(i+1).getValue());
                return (Path) keyList.get(i+1).getKey();

            }
             */

            if ((!path.src().port().equals(notToPort))) {
                log1.setOriginal(path);
                log1.setValueOriginal(util);
                return path;
            }
        }

        return null;
    }

    private Path pickForwardPathIfPossible3(Set<Path> paths, PortNumber notToPort) {
        //ONOS static path initialization & least utilized path per hop rerouting
        ArrayList<Double> minarr = new ArrayList<>();
        double min;

        for (Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }

            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if((Double)newarr[0] <= 6){
                log1.setThreshold(true);
                log1.setOriginal(path);
                log1.setValueOriginal((Double)newarr[0]);
                break;
            }

            if ((!path.src().port().equals(notToPort))) {
                log1.setOriginal(path);
                log1.setValueOriginal((Double)newarr[0]);
                return path;
            }
            minarr.clear();
        }
        //-----------------------------------------------------------------------------------------------
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator1 bvc = new ValueComparator1(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min1;
        ArrayList<Double> minarr1 = new ArrayList<>();

        for (Path path1 : paths) {

            for (Link link : path1.links()) {
                min1 = link_utilizationmap.get(link);
                minarr1.add(min1);
            }

            Object[] newarr = minarr1.toArray();
            Arrays.sort(newarr);
            pathmap.put(path1, (Double) newarr[0]);
            minarr1.clear();
        }
        sorted_map.putAll(pathmap);
        List<Map.Entry> keyList = new ArrayList<>(sorted_map.entrySet());

        for (int i=0;i<keyList.size();i++) {
            Path path = (Path) keyList.get(i).getKey();
            double util = (Double) keyList.get(i).getValue();
            if(util <= 6){
                continue;
            }

            if ((!path.src().port().equals(notToPort))) {
                log1.setReroute(path);
                log1.setValueReroute(util);
                return path;
            }
        }

        //If network is fully congested
        for(Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }
            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if(!path.src().port().equals(notToPort)) {
                log1.setReroute(path);
                log1.setValueReroute((Double) newarr[0]);
                return path;
            }
        }

        return null;

    }

    private Path pickForwardPathIfPossible4(Set<Path> paths, PortNumber notToPort , int length, int counter) {
        //Round Robin path initialization & least utilized path per hop rerouting
        ArrayList<Double> minarr = new ArrayList<>();
        double min;
        int j = 0; //J and first Counter value must match

        for (Path path : paths) {
            if (j == length){
                j = 0;
            }
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }

            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if(counter%length == j){
                if((Double)newarr[0] <= 6){
                    log1.setThreshold(true);
                    log1.setOriginal(path);
                    log1.setValueOriginal((Double)newarr[0]);
                    break;
                }

                if ((!path.src().port().equals(notToPort))) {
                    log1.setOriginal(path);
                    log1.setValueOriginal((Double)newarr[0]);
                    return path;
                }
            }
            j++;
            minarr.clear();
        }
        //-----------------------------------------------------------------------------------------------
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator1 bvc = new ValueComparator1(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min1;
        ArrayList<Double> minarr1 = new ArrayList<>();

        for (Path path1 : paths) {

            for (Link link : path1.links()) {
                min1 = link_utilizationmap.get(link);
                minarr1.add(min1);
            }

            Object[] newarr = minarr1.toArray();
            Arrays.sort(newarr);
            pathmap.put(path1, (Double) newarr[0]);
            minarr1.clear();
        }
        sorted_map.putAll(pathmap);
        List<Map.Entry> keyList = new ArrayList<>(sorted_map.entrySet());

        for (int i=0;i<keyList.size();i++) {
            Path path = (Path) keyList.get(i).getKey();
            double util = (Double) keyList.get(i).getValue();
            if(util <= 6){
                continue;
            }

            if ((!path.src().port().equals(notToPort))) {
                log1.setReroute(path);
                log1.setValueReroute(util);
                return path;
            }
        }

        //If network is fully congested
        for(Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }
            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if(!path.src().port().equals(notToPort)) {
                log1.setReroute(path);
                log1.setValueReroute((Double) newarr[0]);
                return path;
            }
        }

        return null;

    }

    private Path pickForwardPathIfPossibleIntent1(Set<Path> paths) {
        //ONOS static path initialization & least utilized path per hop rerouting : FOR INTENT
        ArrayList<Double> minarr = new ArrayList<>();
        double min;

        for (Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }

            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if((Double)newarr[0] <= 6){
                log1.setThreshold(true);
                log1.setOriginal(path);
                log1.setValueOriginal((Double)newarr[0]);
                break;
            }

            log1.setOriginal(path);
            log1.setValueOriginal((Double)newarr[0]);
            minarr.clear();
            return path;
        }
        //-----------------------------------------------------------------------------------------------
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator1 bvc = new ValueComparator1(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min1;
        ArrayList<Double> minarr1 = new ArrayList<>();

        for (Path path1 : paths) {

            for (Link link : path1.links()) {
                min1 = link_utilizationmap.get(link);
                minarr1.add(min1);
            }

            Object[] newarr = minarr1.toArray();
            Arrays.sort(newarr);
            pathmap.put(path1, (Double) newarr[0]);
            minarr1.clear();
        }
        sorted_map.putAll(pathmap);
        List<Map.Entry> keyList = new ArrayList<>(sorted_map.entrySet());

        for (int i=0;i<keyList.size();i++) {
            Path path = (Path) keyList.get(i).getKey();
            double util = (Double) keyList.get(i).getValue();
            if(util <= 6){
                continue;
            }

            log1.setReroute(path);
            log1.setValueReroute(util);
            return path;

        }

        //If network is fully congested
        for(Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }
            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            log1.setReroute(path);
            log1.setValueReroute((Double) newarr[0]);
            return path;

        }

        return null;

    }

    private Path pickForwardPathIfPossibleIntent2(Set<Path> paths, int length, int counter) {
        //Round Robin path initialization & least utilized path per hop rerouting
        ArrayList<Double> minarr = new ArrayList<>();
        double min;
        int j = 0; //J and first Counter value must match

        for (Path path : paths) {
            if (j == length){
                j = 0;
            }
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }

            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            if(counter%length == j){
                if((Double)newarr[0] <= 6){
                    log1.setThreshold(true);
                    log1.setOriginal(path);
                    log1.setValueOriginal((Double)newarr[0]);
                    break;
                }
                log1.setOriginal(path);
                log1.setValueOriginal((Double)newarr[0]);
                return path;
            }
            j++;
            minarr.clear();
        }
        //-----------------------------------------------------------------------------------------------
        Map<Path, Double> pathmap = new HashMap<>();
        ValueComparator1 bvc = new ValueComparator1(pathmap);
        TreeMap<Path, Double> sorted_map = new TreeMap<>(bvc);
        double min1;
        ArrayList<Double> minarr1 = new ArrayList<>();

        for (Path path1 : paths) {

            for (Link link : path1.links()) {
                min1 = link_utilizationmap.get(link);
                minarr1.add(min1);
            }

            Object[] newarr = minarr1.toArray();
            Arrays.sort(newarr);
            pathmap.put(path1, (Double) newarr[0]);
            minarr1.clear();
        }
        sorted_map.putAll(pathmap);
        List<Map.Entry> keyList = new ArrayList<>(sorted_map.entrySet());

        for (int i=0;i<keyList.size();i++) {
            Path path = (Path) keyList.get(i).getKey();
            double util = (Double) keyList.get(i).getValue();
            if(util <= 6){
                continue;
            }
            log1.setReroute(path);
            log1.setValueReroute(util);
            return path;
        }

        //If network is fully congested
        for(Path path : paths) {
            for (Link link : path.links()) {
                min = link_utilizationmap.get(link);
                minarr.add(min);
            }
            Object[] newarr = minarr.toArray();
            Arrays.sort(newarr);

            log1.setReroute(path);
            log1.setValueReroute((Double) newarr[0]);
            return path;
        }

        return null;

    }

    public void filterpaths(Set<Path> paths){
        List<Path> aList = new ArrayList<>(paths);
        for(int i=0; i<aList.size();i++) {
            for (Link link : aList.get(i).links()) {
                if(link.dst() == aList.get(i).dst()){
                    break;
                }
                if(Marker.get().get(link.dst().deviceId()).equals("access")){
                    paths.remove(aList.get(i));
                }
            }
        }
    }

    private void reroutelog() {
        StringBuilder original = new StringBuilder();
        StringBuilder reroute = new StringBuilder();
        if (log1.getThreshold()) {
            log.info("Threshold Exceeded: " + true);
            List<Link> originalpathlinks = log1.getOriginal().links();
            original.append("Original Path: ");
            for (Link link : originalpathlinks) {
                original.append(link.src()).append(" ---> ").append(link.dst()).append(" ---> ");
            }
            log.info(String.valueOf(original));
            log.info("Original Path Utilization: " + log1.getValueOriginal());
            List<Link> reroutepathlinks = log1.getReroute().links();
            reroute.append("Rerouted Path: ");
            for (Link link : reroutepathlinks) {
                reroute.append(link.src()).append(" ---> ").append(link.dst()).append(" ---> ");
            }
            log.info(String.valueOf(reroute));
            log.info("Reroute Path Utilization: " + log1.getValueReroute());
        }
        else{
            StringBuilder logpath= new StringBuilder();
            logpath.append("Main Path: ");
            logpath.append(log1.getOriginal().src().deviceId().toString()).append(" ---> ");
            for (Link link : log1.getOriginal().links()) {
                logpath.append(link.dst().deviceId().toString()).append(" ---> ");
            }
            log.info(String.valueOf(logpath));
            log.info("Main Path Utilization: " + log1.getValueOriginal());
        }
    }

    private Map<Link, Double> linkutilizationmapsFlow() throws IOException {
        String url1 = "http://localhost:9090/api/v1/query?query=sflow_ifoutoctets";
        String url2 = "http://localhost:9090/api/v1/query?query=sflow_ifinoctets";

        Map<ConnectPoint, Integer> linkcapacity = linkcapacitymap();

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

        Map<ConnectPoint, Double> Outportutilization = new HashMap<>();
        Map<ConnectPoint, Double> Inportutilization = new HashMap<>();

        Map<Link, Double> link_utilizationmap = new HashMap<>(); //THE MAIN MAP WITH THE LINK AND ITS VALUE

        final JsonNode json1 = mapper.readTree(new URL(url1));
        final JsonNode json2 = mapper.readTree(new URL(url2));

        int length = json1.get("data").get("result").size();

        for (int i = 0; i < length; i++) {
            JsonNode int_name_out = json1.get("data").get("result").get(i).get("metric").get("ifname");
            JsonNode metric_value_out = json1.get("data").get("result").get(i).get("value").get(1);

            JsonNode int_name_in = json2.get("data").get("result").get(i).get("metric").get("ifname");
            JsonNode metric_value_in = json2.get("data").get("result").get(i).get("value").get(1);

            if (!int_name_in.asText().contains("eth")) {
                continue;
            }

            String[] inf = int_name_in.asText().replace("s", "").split("-eth");
            DeviceId deviceId;
            if (Integer.parseInt(inf[0]) < 10) {
                deviceId = DeviceId.deviceId("of:000000000000000" + inf[0]);
            } else {
                deviceId = DeviceId.deviceId("of:00000000000000" + inf[0]);
            }
            PortNumber portNumber = PortNumber.portNumber(inf[1]);

            ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);

            //double port_Oututilization = metric_value_out.asDouble() * 8 / linkcapacity.get(connectPoint)*100;
            //double port_Inutilization = metric_value_in.asDouble() * 8 / linkcapacity.get(connectPoint)*100;

            //double port_Oututilization = 100 - (metric_value_out.asDouble() * 8 / 10000000 * 100);
            //double port_Inutilization = 100 - (metric_value_in.asDouble() * 8 / 10000000 * 100);

            double port_Oututilization = linkspeed(connectPoint.toString()) - (metric_value_out.asDouble() * 8);
            double port_Inutilization = linkspeed(connectPoint.toString()) - (metric_value_in.asDouble() * 8);

            Outportutilization.put(connectPoint, port_Oututilization);
            Inportutilization.put(connectPoint, port_Inutilization);
        }


        for (Link link : linkService.getLinks()) {
            ConnectPoint src = link.src();
            ConnectPoint dst = link.dst();

            double link_utilization = (Outportutilization.get(src) + Inportutilization.get(dst)) / 2;

            if (!link_utilizationmap.containsKey(link)) {
                link_utilizationmap.put(link, link_utilization);
            }
        }

        return link_utilizationmap;

    }

    private Map<Link, Double> linkutilizationmapInternal() {
        Map<ConnectPoint, Double> Outportutilization1 = new HashMap<>();
        Map<ConnectPoint, Double> Inportutilization1 = new HashMap<>();
        Map<Link, Double> link_utilizationmap = new HashMap<>(); //THE MAIN MAP WITH THE LINK AND ITS VALUE

        for (Link link : linkService.getLinks()) {

            double port_Oututilization1 = 100 - ((double) deviceService.getDeltaStatisticsForPort(link.src().deviceId(), link.src().port()).bytesSent() / 5 * 8 / 10000000 * 100);
            double port_Inutilization1 = 100 - ((double) deviceService.getDeltaStatisticsForPort(link.dst().deviceId(), link.dst().port()).bytesReceived() / 5 * 8 / 10000000 * 100);

            Outportutilization1.put(link.src(), port_Oututilization1);
            Inportutilization1.put(link.dst(), port_Inutilization1);
        }

        for (Link link : linkService.getLinks()) {
            ConnectPoint src = link.src();
            ConnectPoint dst = link.dst();

            Double link_utilization = (Outportutilization1.get(src) + Inportutilization1.get(dst)) / 2;
            if (!link_utilizationmap.containsKey(link)) {
                link_utilizationmap.put(link, link_utilization);
            }

        }
        return link_utilizationmap;
    }

    public int linkspeed(String connectpoint) throws IOException {
        String url = "http://10.100.10.8:9090/api/v1/query?query=linkcapacity{connectpoint=~%22"+connectpoint+"%22}";
        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final JsonNode json1 = mapper.readTree(new URL(url));
        JsonNode linkspeed_value = json1.get("data").get("result").get(0).get("value").get(1);
        return linkspeed_value.asInt();
    }

    public Map<ConnectPoint, Integer> linkcapacitymap() throws IOException {
        String url = "http://localhost:9090/api/v1/query?query=linkcapacity";

        final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        final JsonNode json1 = mapper.readTree(new URL(url));

        Map<ConnectPoint, Integer> linkcapacity = new HashMap<>();

        int length = json1.get("data").get("result").size();

        for (int i = 0; i < length; i++) {
            JsonNode connectpoint = json1.get("data").get("result").get(i).get("metric").get("connectpoint");
            JsonNode linkspeed_value = json1.get("data").get("result").get(i).get("value").get(1);


            String[] inf = connectpoint.asText().split("/");

            DeviceId deviceId = DeviceId.deviceId(inf[0]);
            PortNumber portNumber = PortNumber.portNumber(inf[1]);
            int linkspeed = linkspeed_value.asInt();

            linkcapacity.put(new ConnectPoint(deviceId, portNumber), linkspeed);
        }
        return linkcapacity;
    }

    private LinkEvent threshold(Map<Link, Double> linkutilizationmap) {
        for (Link link : linkService.getLinks()) {
            if (linkutilizationmap.get(link) < 99) {
                return new LinkEvent(LinkEvent.Type.LINK_UPDATED, link);
            }
        }

        return null;
    }

    private void fixBlackhole(ConnectPoint egress) {
        Set<FlowEntry> rules = getFlowRulesFrom(egress);
        Set<SrcDstPair> pairs = findSrcDstPairs(rules);

        Map<DeviceId, Set<Path>> srcPaths = new HashMap<>();

        for (SrcDstPair sd : pairs) {
            // get the edge deviceID for the src host
            Host srcHost = hostService.getHost(HostId.hostId(sd.src));
            Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
            if (srcHost != null && dstHost != null) {
                DeviceId srcId = srcHost.location().deviceId();
                DeviceId dstId = dstHost.location().deviceId();
                log.trace("SRC ID is {}, DST ID is {}", srcId, dstId);

                cleanFlowRules(sd, egress.deviceId());

                Set<Path> shortestPaths = srcPaths.get(srcId);
                if (shortestPaths == null) {
                    shortestPaths = topologyService.getKShortestPaths
                                    (topologyService.currentTopology(), egress.deviceId(), srcId).limit(limit)
                            .filter(path -> path.cost() <= 3).sorted(Comparator.comparingDouble(Path::cost)).
                            collect(Collectors.toCollection(LinkedHashSet::new));
                    srcPaths.put(srcId, shortestPaths);
                }
                backTrackBadNodes(shortestPaths, dstId, sd);
            }
        }
    }

    // Backtracks from link down event to remove flows that lead to blackhole
    private void backTrackBadNodes(Set<Path> shortestPaths, DeviceId dstId, SrcDstPair sd) {
        for (Path p : shortestPaths) {
            List<Link> pathLinks = p.links();
            for (int i = 0; i < pathLinks.size(); i = i + 1) {
                Link curLink = pathLinks.get(i);
                DeviceId curDevice = curLink.src().deviceId();

                // skipping the first link because this link's src has already been pruned beforehand
                if (i != 0) {
                    cleanFlowRules(sd, curDevice);
                }

                Set<Path> pathsFromCurDevice = topologyService.getKShortestPaths
                                (topologyService.currentTopology(), curDevice, dstId).limit(limit)
                        .filter(path -> path.cost() <= 3).sorted(Comparator.comparingDouble(Path::cost)).
                        collect(Collectors.toCollection(LinkedHashSet::new));

                if (pickForwardPathIfPossible3(pathsFromCurDevice, curLink.src().port()) != null) {
                    break;
                } else {
                    if (i + 1 == pathLinks.size()) {
                        cleanFlowRules(sd, curLink.dst().deviceId());
                    }
                }
            }
        }
    }

    // Removes flow rules off specified device with specific SrcDstPair
    private void cleanFlowRules(SrcDstPair pair, DeviceId id) {
        log.trace("Searching for flow rules to remove from: {}", id);
        log.trace("Removing flows w/ SRC={}, DST={}", pair.src, pair.dst);
        for (FlowEntry r : flowRuleService.getFlowEntries(id)) {
            boolean matchesSrc = false, matchesDst = false;
            for (Instruction i : r.treatment().allInstructions()) {
                if (i.type() == Instruction.Type.OUTPUT) {
                    // if the flow has matching src and dst
                    for (Criterion cr : r.selector().criteria()) {
                        if (cr.type() == Criterion.Type.ETH_DST) {
                            if (((EthCriterion) cr).mac().equals(pair.dst)) {
                                matchesDst = true;
                            }
                        } else if (cr.type() == Criterion.Type.ETH_SRC) {
                            if (((EthCriterion) cr).mac().equals(pair.src)) {
                                matchesSrc = true;
                            }
                        }
                    }
                }
            }
            if (matchesDst && matchesSrc) {
                log.trace("Removed flow rule from device: {}", id);
                flowRuleService.removeFlowRules((FlowRule) r);
            }
        }

    }

    // Returns a set of src/dst MAC pairs extracted from the specified set of flow entries
    private Set<SrcDstPair> findSrcDstPairs(Set<FlowEntry> rules) {
        ImmutableSet.Builder<SrcDstPair> builder = ImmutableSet.builder();
        for (FlowEntry r : rules) {
            MacAddress src = null, dst = null;
            for (Criterion cr : r.selector().criteria()) {
                if (cr.type() == Criterion.Type.ETH_DST) {
                    dst = ((EthCriterion) cr).mac();
                } else if (cr.type() == Criterion.Type.ETH_SRC) {
                    src = ((EthCriterion) cr).mac();
                }
            }
            builder.add(new SrcDstPair(src, dst));
        }
        return builder.build();
    }


    private Set<FlowEntry> getFlowRulesFrom(ConnectPoint egress) {
        ImmutableSet.Builder<FlowEntry> builder = ImmutableSet.builder();
        flowRuleService.getFlowEntries(egress.deviceId()).forEach(r -> {
            if (r.appId() == appId.id()) {
                r.treatment().allInstructions().forEach(i -> {
                    if (i.type() == Instruction.Type.OUTPUT) {
                        if (((Instructions.OutputInstruction) i).port().equals(egress.port())) {
                            builder.add(r);
                        }
                    }
                });
            }
        });

        return builder.build();
    }

    // Wrapper class for a source and destination pair of MAC addresses
    private final class SrcDstPair {
        final MacAddress src;
        final MacAddress dst;

        private SrcDstPair(MacAddress src, MacAddress dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SrcDstPair that = (SrcDstPair) o;
            return Objects.equals(src, that.src) &&
                    Objects.equals(dst, that.dst);
        }

        @Override
        public int hashCode() {
            return Objects.hash(src, dst);
        }
    }

    private class Log {
        private Path original;
        private boolean threshold;
        private double orivalue;
        private double reroutevalue;
        private Path reroute;

        public Log() {

        }

        public Path getOriginal() {
            return original;
        }

        public void setOriginal(Path original) {
            this.original = original;
        }

        public boolean getThreshold() {
            return threshold;
        }

        public void setThreshold(boolean threshold) {
            this.threshold = threshold;
        }

        public Double getValueOriginal(){ return orivalue;}

        public void setValueOriginal(double orivalue){ this.orivalue= orivalue;}

        public double getValueReroute(){ return reroutevalue;}

        public void setValueReroute(double reroutevalue){ this.reroutevalue= reroutevalue;}

        public Path getReroute() { return reroute;}

        public void setReroute(Path reroute){ this.reroute = reroute;}
    }


    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
            List<Event> reasons = event.reasons();
            if (reasons != null) {
                reasons.forEach(re -> {
                    if (re instanceof LinkEvent) {
                        LinkEvent le = (LinkEvent) re;
                        if (le.type() == LinkEvent.Type.LINK_REMOVED && blackHoleExecutor != null) {
                            blackHoleExecutor.submit(() -> fixBlackhole(le.subject().src()));
                        }
                    }
                });
            }
        }
    }

    private class InternalLinkListener implements LinkListener {
        @Override
        public void event(LinkEvent event) {
            log.info(String.valueOf(event));

        }

    }
}






