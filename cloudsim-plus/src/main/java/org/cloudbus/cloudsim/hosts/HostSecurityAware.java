package org.cloudbus.cloudsim.hosts;

import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * A Host used to experiment with Ahamed, Shahrestani, and Javadi's Security
 * Aware and Energy-Efficient Virtual Machine Consolidation algorithm.
 *
 * @author Caitlin Fischer
 */
public class HostSecurityAware extends HostSimple {
    public HostSecurityAware(
      final long ram, final long bw, final long storage,
      final List<Pe> peList) {
        super(ram, bw, storage, peList, true);
    }

    /**
     * Calculates the migration score of candidate hosts according to the
     * algorithm proposed by Afoulki, Bousquet, and Rouzaud-Cornabas.
     *
     * A host's total score depends on the number of adversary VMs running on
     * it, the number of adversary users who own VMs running on the host, and
     * the host's available CPU and memory resources.
     *
     * The host with the fewest number of adversary VMs and users and with the
     * most resources has the lowest score and is the best host from which to
     * migrate VMs.
     *
     * @param host the host whose migration score is to be calculated
     * @param vm the VM that needs to be placed on a host
     * @return the host's score
     */
    @Override public double getMigrationScore(final int vmId) {
        int num_incompatible_vms = 0;
        List<Vm> vms = getVmList();
        for (Vm runningVm : vms) {
            // The IDs were chosen to be integers, so this cast is safe.
            int runningVmId = (int) runningVm.getId();
            if (runningVmId == 1) {
                // A VM with the ID 1 is an adversary to all users.
                return Double.MAX_VALUE;
            }
            if (vmId % runningVmId == 0 || runningVmId % vmId == 0) {
                ++num_incompatible_vms;
            }
        }

        // In this simulation, we assume that all jobs are submitted by
        // different users.
        final int num_incompatible_users = num_incompatible_vms;

        // Casting is safe as integers were chosen for the RAM values.
        double resources = getCpuPercentUtilization()
            + (double) getRamUtilization()
            / (double) getRamProvisioner().getCapacity();
        
        return ((double) (num_incompatible_vms
            + num_incompatible_users + 1))
            * resources / 2.0;
    }

    @Override
    public boolean isSuitableForVm(final Vm vm) {
        // In terms of security, a host is unsuitable for a VM that needs to be
        // allocated to a host if any of the VM's adversaries are already
        // running on the host.
        //
        // For the purposes of this simulation, two VMs are considered
        // adversaries if the ID of one VM divides the ID of the other VM.
        //
        // E.g. If one VM has the ID 2 and the other VM has the ID 6, then
        // these VMs are adversaries. Conversely, if one VM has the ID 2 and
        // the other has the ID 3, then these VMs are not adversaries.
        //
        // A consequence of this is that the VM with ID 1 cannot share a
        // physical host; however, this is permitted in the scheme proposed by
        // Afoulki, Bousquet, and Rouzaud-Cornabas.
        if (!isFailed() && hasEnoughResources(vm)) {
            List<Vm> vms = getVmList();
            for (Vm runningVm : vms) {
                long id1 = vm.getId();
                long id2 = runningVm.getId(); 
                if (id1 % id2 == 0 || id2 % id1 == 0) {
                    return false;
                } 
            }
            return true;
        }
        return false;
    }
}