# ONOS Traffic Forwarding & Rerouting Application

## Project Description
This project focuses on enhancing network performance by optimizing traffic forwarding based on network performance metrics such as link utilization. The application utilizes the network performance metrics to load balance the traffic to an optimal route when there's network congestion on the current path. 

sFlow and Prometheus are used to collect and store the network performance metrics

The SDN controller application is designed within the [ONOS framework](https://wiki.onosproject.org/display/ONOS/ONOS). As of now, the application is at version 2.7.0 and is in Long-Term Support (LTS) status.

Experiments were conducted using Containernet, a fork of the well-known Mininet network emulator that enables the utilization of Docker containers as hosts within emulated network topologies. These experiments utilized Open vSwitch with OpenFlow version 1.3 for Southbound communication.

The primary topology examined in this project is the campus LAN architecture, which includes the core layer, distribution layer, and access layer. But, this application supports other topologies as well.

## Usage
### Step 1: Configure sFlow-RT
Configure the polling & sampling rate to 10 & 10 in sflow.py script

### Step 2: Configure Prometheus
Refer to this guide [here](https://blog.sflow.com/2019/04/prometheus-exporter.html), to define a metrics "scraping" job in the Prometheus configuration file, prometheus.yml

### Step 3: Compile & install the application
Refer to the general guide [here](https://wiki.onosproject.org/display/ONOS/Template+Application+Tutorial), clone this repository, compile it with `mvn clean install -DskipTests` then install the `.oar` to ONOS. Activate the app on ONOS with the command `app activate org.foo.app`in ONOS CLI

### Step 4: Install supporting ONOS application
Activate the default OpenFlow app to enable Southbound communication from ONOS to OvS. Use the command `app activate org.onosproject.openflow` in ONOS CLI

### Step 5: Enable/Disable Campus LAN Path Filtering
Default is TRUE <br>
If you intend to disable this feature, use the command `onos:cfg set org.foo.app.Foo campus false` in ONOS CLI

### Step 6: Mark Core/Distribution switches (Optional if Step 5 feature is disabled)
By default, all switches are marked as access switches <br>
1 - core <br> 
2 - distribution <br>
3 - access <br>
To mark, use the command `mark switch-device-id 2` in ONOS CLI

### Step 7: Set link bandwidth for the Core/Distribution Layer
By default, all links are set to 10Mbits <br>
Link bandwidth **MUST** follow the value set during Mininet network emulation. <br>
Layer: <br>
core <br>
distribution <br>
access <br>
Use the command, `bandwidth layer value` in ONOS CLI

### Step 8: Set forwarding method
There are 4 forwarding methods: <br>
0 - Shortest Path forwarding and no rerouting <br>
1 - Shortest Path forwarding and least utilized path rerouting <br>
2 - Least utilized path forwarding and rerouting <br>
3 - Round Robin path forwarding and Least utilized path rerouting <br>
Use the command, 'onos:cfg set org.foo.app.Foo forwarding 3' in ONOS CLI

### Step 9: Traffic Generation
Pump high-load traffic using traffic generation tools such as iperf & scapy. <br>
Refer to my other GitHub repo [here](https://github.com/vikki8/real_life_traffic_generator), to implement a real-life traffic generation scenario using a real-life traffic flow dataset.

## License
This project is licensed under the Apache-2 License.
