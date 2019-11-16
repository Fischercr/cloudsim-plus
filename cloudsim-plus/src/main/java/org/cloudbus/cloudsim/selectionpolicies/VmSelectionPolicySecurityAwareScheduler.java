package org.cloudbus.cloudsim.selectionpolicies;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;
import java.util.Objects;

/**
 * An implementation of Afoulki, Bousquet, and Rouzaud-Cornabas' security-aware
 * VM migration algorithm.
 *
 * @author Caitlin Fischer
 */
public class VmSelectionPolicySecurityAwareScheduler implements VmSelectionPolicy {
 
    @Override
    /**
     * This method returns a VmNull to ensure that no VMs are migrated due to
     * utilization as this is not a part of the aforementioned researchers'
     * approach.
     *
     * Since migration occurs only during placement with their approach, the
     * code that performs this is found in 
     * VmAllocationPolicyMigrationSecurityAware.java.
     *
     * @param host the host that needs to have a VM migrated out
     * @param vm the VM that needs to be migrated
     * @return a VmNull
     */
    public Vm getVmToMigrate(final Host host) {
        return Vm.NULL;
    }
}