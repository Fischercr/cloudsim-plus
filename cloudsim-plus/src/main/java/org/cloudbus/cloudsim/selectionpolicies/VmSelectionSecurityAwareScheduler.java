package org.cloudbus.cloudsim.selectionpolicies;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;
import java.util.Objects;

/**
 * A VM selection policy that randomly select VMs to migrate from a host.
 * It uses a uniform Pseudo Random Number Generator (PRNG) as default to select VMs.
 *
 * @author Caitlin Fischer
 */
public class VmSelectionSecurityAwareScheduler implements VmSelectionPolicy {

  @Override
  public Vm getVmToMigrate(final Host host) {
    final List<Vm> migratableVms = host.getMigratableVms();
    if (migratableVms.isEmpty()) {
      return Vm.NULL;
    }

    final int index = (int)rand.sample()*migratableVms.size();
    return migratableVms.get(index);
  }
}