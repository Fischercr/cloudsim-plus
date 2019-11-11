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
public class HostSecurityAwareEnergyEfficient extends HostSimple {
    public HostSecurityAwareEnergyEfficient(
      final long ram, final long bw, final long storage, final List<Pe> peList) {
        super(ram, bw, storage, peList, true);
    }


    @Override
    public boolean isSuitableForVm(final Vm vm) {
        return (!isFailed() && hasEnoughResources(vm)
          && securityLevel == vm.getSecurityLevel());
    }
}