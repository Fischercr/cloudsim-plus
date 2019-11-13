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
      final long ram, final long bw, final long storage, final List<Pe> peList) {
        super(ram, bw, storage, peList, true);
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
                    System.out.println("id1: " + id1 + " id2: " + id2);
                    return false;
                } 
            }
            return true;
        }
        return false;
    }
}