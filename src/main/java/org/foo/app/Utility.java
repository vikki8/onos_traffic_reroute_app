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


import org.onlab.packet.MacAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.*;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;

import java.util.*;


public class Utility {
}


class SortById implements Comparator<DeviceId> {
    // Used for sorting in ascending order of ID
    public int compare(DeviceId a, DeviceId b) {
        if (a == b) {
            return 0;
        } else if (String.valueOf(a).compareTo(String.valueOf(b)) > 0) {
            return 1;
        } else
            return -1;
    }

}

class SortByInterface implements Comparator<String> {
    // Used for sorting in ascending order of ID
    public int compare(String a, String b) {
        if (Objects.equals(a, b)) {
            return 0;
        } else if (String.valueOf(a).compareTo(String.valueOf(b)) > 0) {
            return 1;
        } else
            return -1;
    }
}

class ValueComparator implements Comparator<Path> {
    Map<Path, Double> base;

    public ValueComparator(Map<Path, Double> base) {
        this.base = base;
    }

    // Note: this comparator imposes orderings that are inconsistent with
    // equals.
    public int compare(Path a, Path b) {
        int c = base.get(a).compareTo(base.get(b));
        boolean h;
        if (c == 0) {
            h = a.cost() > b.cost();
            if (h){
                return 1;
            }
            else {
                return -1;
            }
        } else if (c > 0) {
            return -1;

        } else {
            return 1;
        } // returning 0 would merge keys
    }
}

class ValueComparator1 implements Comparator<Path> {
    Map<Path, Double> base;

    public ValueComparator1(Map<Path, Double> base) {
        this.base = base;
    }

    // Note: this comparator imposes orderings that are inconsistent with
    // equals.
    public int compare(Path a, Path b) {
        if (a.cost() == b.cost()) {
            int c = base.get(a).compareTo(base.get(b));
            if (c < 0){
                return 1;
            }
            else {
                return -1;
            }
        } else if (a.cost() < b.cost()) {
            return -1;

        } else {
            return 1;
        } // returning 0 would merge keys
    }
}

class Counter {
    public static int COUNT = 0;

    Counter() {

    }

    public static void increment() {
        COUNT++;
    }

    public static void clear() {
        COUNT = 0;
    }
}






