package org.cloudbus.cloudsim.allocationpolicies.migration;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.selectionpolicies.VmSelectionPolicy;
import org.cloudbus.cloudsim.vms.Vm;

import static java.util.Comparator.comparingDouble;
import java.util.*;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An implementation of Afoulki, Bousquet, and Rouzaud-Cornabas' security-aware
 * VM placement algorithm.
 *
 * It's a VM allocation policy that uses a static CPU threshold to detect host
 * overutilization. [Insert details here about the first host selected.]
 *
 * @author Caitlin Fischer
 */
public class VmAllocationPolicyMigrationSecurityAware extends VmAllocationPolicyMigrationStaticThreshold {
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

    /**
     * Applies additional filters to the stream of hosts and performs the
     * actual host selection. The host with the highest score is selected.
     *
     * @param vm the VM that needs to be placed on a host
     * @param hostStream a {@link Stream} containing candidate hosts
     * @return an {@link Optional} containing a suitable host for the VM or an
     * empty {@link Optional} if not found
     */
    protected Optional<Host> findHostForVmInternal(
        final Vm vm, final Stream<Host> hostStream) {
        final Comparator<Host> hostScoreComparator =
            comparingDouble(host -> getHostScore(host, vm));
        return hostStream.max(hostScoreComparator);
    }

    /**
     * Calculates the score of candidate hosts according to the algorithm
     * proposed by Afoulki, Bousquet, and Rouzaud-Cornabas.
     *
     * A host's total score depends on the number of VMs running on it, the
     * number of VMs scheduled to run on it in the future, and its capacity.
     * Capacity encompasses a host's available CPU and memory.
     *
     * Hosts with the fewest number of VMs and with the highest capacity are
     * scored the highest.
     *
     * @param host the host whose score to calculate
     * @param vm the VM that needs to be placed on a host
     * @return the host's score
     */
    protected double getHostScore(final Host host, final Vm vm) {
        int vm_count = (host.getVmsMigratingIn().size() + 
                        host.getVmList().size());
        long memory = host.getRamUtilization();  // Megabytes.
        double utilization = host.getCpuPercentUtilization();  // [0, 1].
        if (vm_count > 0) {
          return memory + utilization;
        }
        return (memory + utilization) / vm_count;
    }
}