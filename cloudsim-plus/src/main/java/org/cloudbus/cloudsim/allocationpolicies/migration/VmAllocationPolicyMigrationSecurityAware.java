package org.cloudbus.cloudsim.allocationpolicies.migration;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.selectionpolicies.VmSelectionPolicy;
import org.cloudbus.cloudsim.vms.Vm;


import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of Afoulki, Bousquet, and Rouzaud-Cornabas' security-aware
 * VM placement algorithm.
 *
 * @author Caitlin Fischer
 */
public class VmAllocationPolicyMigrationSecurityAware extends 
    VmAllocationPolicyMigrationStaticThreshold {
    /**
     * Creates a VmAllocationPolicyMigrationSecurityAware.
     *
     * @param vmSelectionPolicy the policy that defines how VMs are selected
     * for migration
     * @param overUtilizationThreshold the over utilization threshold
     */
    public VmAllocationPolicyMigrationSecurityAware(
        final VmSelectionPolicy vmSelectionPolicy,
        final double overUtilizationThreshold) {
        super(vmSelectionPolicy, overUtilizationThreshold, null);
    }

    @Override
    public Map<Vm, Host> getOptimizedAllocationMap(final List<? extends Vm> vmList) {
        return new HashMap<>();
    }

    private int computeVmMigrationScore(
        final int incomingVmId, Vm runningVm) {
        final int runningVmId = (int) runningVm.getId();
        
        if (incomingVmId % runningVmId == 0
            || runningVmId % incomingVmId == 0) {
                return -1;
        }

        return (int) runningVm.getRam().getAvailableResource()
                + (int) runningVm.getCurrentRequestedTotalMips();
    }

    private Optional<Host> simulateMigration(
        final Host host, final Vm incomingVm) {
        LinkedList<Vm> vms = new LinkedList<Vm>(host.getVmList());
        if (vms.isEmpty()) {
            return Optional.empty();
        }

        final int vmId = (int) incomingVm.getId();
        for (Vm runningVm : vms) {
            runningVm.setMigrationScore(
                computeVmMigrationScore(vmId, runningVm));
        }
        final Comparator<Vm> vmComparator =
            comparingDouble(vm -> vm.getMigrationScore());
        Collections.sort(vms, vmComparator);

        saveAllocation();

        LinkedList<Vm> adversaryVms = new LinkedList<Vm>();
        System.out.println(vms.size());
        for (int i = 0; i < vms.size(); ++i) {
            System.out.println(vms.get(i));
        }
        while (vms.getFirst().getMigrationScore() == -1) {
            adversaryVms.add(vms.removeFirst());
        }

        final Map<Vm, Host> migrationMap = new HashMap<Vm, Host>();
        final StringBuilder builder = new StringBuilder();
        for (final Vm vm : adversaryVms) {
            defaultFindHostForVm(vm).ifPresent(targetHost -> {
                addVmToMigrationMap(migrationMap, vm, targetHost);
                appendVmMigrationMsgToStringBuilder(builder, vm, targetHost);
            });
        }

        LOGGER.info(
            "{}: VmAllocationPolicy: Reallocation of VMs from hosts: {}{}",
            getDatacenter().getSimulation().clockStr(), System.lineSeparator(),
            builder.toString());

        restoreAllocation();

        if (migrationMap.isEmpty()) {
            return Optional.empty();
        }

        Datacenter dc = getDatacenter();
        if (isNotHostOverloadedAfterAllocation(host, incomingVm)) {
            restoreAllocation();
            for (final Map.Entry<Vm, Host> entry : migrationMap.entrySet()) {
                dc.requestVmMigration(entry.getKey(), entry.getValue());
            }
            // dc.requestVm
            return Optional.of(host);
            // if (host.isSuitableForVm(incomingVm)) {
            //     return Optional.of(host);
            // }
            // System.out.println("SOMETHING WENT WRONG");
            // return Optional.empty();
        } else {
            System.out.println("YOU ARE HERE");
            return Optional.empty();
        }
    }

    /**
     * Attempts to migrate VMs from a host so that the incoming VM can be
     * placed.
     *
     * @param vm the VM that needs to be placed on a host
     * @return an Optional containing a suitable host for the VM or an empty
     * Optional if not found
     */    
    private Optional<Host> attemptMigration(final Vm vm) {
        final Set<Host> excludedHosts = new HashSet<>();
        excludedHosts.add(vm.getHost());


        final Stream<Host> hostStream = this.getHostList().stream()
            .filter(host -> !excludedHosts.contains(host))
            .filter(host -> !host.isFailed())
            .filter(host -> host.hasEnoughResources(vm))
            .filter(host -> isNotHostOverloadedAfterAllocation(host, vm))
            .filter(host -> true);

        // This cast is safe as the number of VMs, and hence, the ID, is at
        // most 1000.
        int vmId = (int) vm.getId();

        final Comparator<Host> comparator =
            comparingDouble(host -> host.getMigrationScore(vmId));
        final Optional<Host> maybe_host = hostStream.min(comparator);

        if (maybe_host.isEmpty()
            || maybe_host.get().getMigrationScore(vmId) == Double.MAX_VALUE) {
            return Optional.empty();
        }

        System.out.println("Trying to migrate VMs from host " 
            + maybe_host.get().getId());

        return simulateMigration(maybe_host.get(), vm);
    }

    /**
     * Applies additional filters to the stream of hosts and performs the
     * actual host selection. The host with the highest score is selected.
     *
     * At this point, adversarial hosts and hosts without sufficient resources
     * for the incoming VM have already been filtered out.
     *
     * @param vm the VM that needs to be placed on a host
     * @param hostStream a Stream containing candidate hosts
     * @return an Optional containing a suitable host for the VM or an empty
     * Optional if not found
     */
    protected Optional<Host> findHostForVmInternal(
        final Vm vm, final Stream<Host> hostStream) {
        ArrayList<Host> hosts =
            hostStream.collect(Collectors.toCollection(ArrayList::new));

        if (hosts.size() == 0) {
            return attemptMigration(vm);
        }
        if (hosts.size() == 1) {
            return Optional.of(hosts.get(0));
        }

        final Comparator<Host> hostScoreComparator =
             comparingDouble(host -> getHostPlacementScore(host, vm));
        Collections.sort(hosts, hostScoreComparator);

        final int last_index = hosts.size() - 1;
        return Optional.of(hosts.get(last_index));
    }

    /**
     * Calculates the placement score of candidate hosts according to the
     * algorithm proposed by Afoulki, Bousquet, and Rouzaud-Cornabas.
     *
     * A host's total score depends on the number of VMs running on it, the
     * number of VMs scheduled to run on it in the future, and its capacity.
     * Capacity encompasses a host's available CPU and memory.
     *
     * The host with the fewest number of VMs and with the highest capacity has
     * the highest score and is the preferred candidate for placing the VM.
     *
     * @param host the host whose score to calculate
     * @param vm the VM that needs to be placed on a host
     * @return the host's score
     */
    protected double getHostPlacementScore(final Host host, final Vm vm) {
        int vm_count = (host.getVmsMigratingIn().size() + 
                        host.getVmList().size()) + 1;
        double free_ram = 1 - (host.getRamUtilization() / host.getRamProvisioner().getCapacity());
        double free_cpu = 1 - host.getCpuPercentUtilization();  // [0, 1].
        return (free_ram + free_cpu) / vm_count;
    }
}