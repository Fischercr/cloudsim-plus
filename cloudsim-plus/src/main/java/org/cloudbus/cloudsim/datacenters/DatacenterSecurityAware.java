/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.datacenters;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSimEntity;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.core.events.PredicateType;
import org.cloudbus.cloudsim.core.events.SimEvent;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.network.IcmpPacket;
import org.cloudbus.cloudsim.resources.DatacenterStorage;
import org.cloudbus.cloudsim.resources.FileStorage;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletScheduler;
import org.cloudbus.cloudsim.util.Conversion;
import org.cloudbus.cloudsim.util.MathUtil;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.faultinjection.HostFaultInjection;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.listeners.HostEventInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * 
 */
public class DatacenterSecurityAware extends DatacenterSimple implements Datacenter {
    public DatacenterSecurityAware(
        final Simulation simulation,
        final List<? extends Host> hostList,
        final VmAllocationPolicy vmAllocationPolicy) {
        super(simulation, hostList, vmAllocationPolicy);
    }

    protected void checkIfVmMigrationsAreNeeded() {
        if (!isMigrationsEnabled()) {
            return;
        }

        final Map<Vm, Host> migrationMap = getVmAllocationPolicy().getOptimizedAllocationMap(getVmList());
        long idVmToPlace = -1;
        Vm vmToRemove = new VmSimple(-1, 1000, 1);
        for (final Map.Entry<Vm, Host> entry : migrationMap.entrySet()) {
            if (entry.getValue() == null) {
              vmToRemove = entry.getKey();
              idVmToPlace = entry.getKey().getId();
              break;
            }
        }
        migrationMap.remove(vmToRemove);

        for (final Map.Entry<Vm, Host> entry : migrationMap.entrySet()) {
            requestVmMigration(entry.getKey(), entry.getValue());
        }

        if (idVmToPlace != -1 && !migrationMap.isEmpty()) {
            VmSimple vm = new VmSimple(idVmToPlace, 1000, 1);
            vm.setRam(1024).setBw(100).setSize(2500).setSubmissionDelay(600);
            vm.getUtilizationHistory().enable();
          Map.Entry<Vm, Host> entry = migrationMap.entrySet().iterator().next();
          entry.getKey().getBroker().submitVm(vm);
        }
    }
}
