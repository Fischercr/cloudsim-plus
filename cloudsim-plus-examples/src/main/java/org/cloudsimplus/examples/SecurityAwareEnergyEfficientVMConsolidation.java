package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.allocationpolicies.migration.VmAllocationPolicyMigrationLocalRegression;
import org.cloudbus.cloudsim.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelPlanetLab;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * An implementation of Ahamed, Shahrestani, and Javadi's Security Aware and
 * Energy-Efficient Virtual Machine Consolidation algorithm.
 *
 * @author Caitlin Fischer
 */
public class SecurityAwareEnergyEfficientVMConsolidation {
    private static final int HOSTS = 3;
    private static final int HOST_PES = 10;

    private static final int VMS = 4;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 100000000;

    private static final String TRACE_FILE = "";
    private static final int SCHEDULING_INTERVAL = 300;

    private static final double HOST_OVER_UTILIZATION_THRESHOLD = 0.9;
    private static final double HOST_UNDER_UTILIZATION_THRESHOLD = 0.1;

    private VmAllocationPolicyMigrationLocalRegression allocationPolicy;
    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new SecurityAwareEnergyEfficientVMConsolidation();
    }

    private SecurityAwareEnergyEfficientVMConsolidation() {
        simulation = new CloudSim();
        datacenter0 = createDatacenter();

        // Creates a broker, which acts on behalf a cloud customer to manage
        // manage VMs and Cloudlets.
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final List<Cloudlet> doneCloudlets = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(doneCloudlets).build();
    }

    private Datacenter createDatacenter() {
        final List<Host> hostList = new ArrayList<>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        // Do we need a fallback allocation policy? How do we know which policy
        // is being used?
        final VmAllocationPolicyMigrationStaticThreshold fallback =
            new VmAllocationPolicyMigrationStaticThreshold(
                new VmSelectionPolicyMinimumMigrationTime(),
                0.7);

        this.allocationPolicy =
            new VmAllocationPolicyMigrationLocalRegression(
                new VmSelectionPolicyMinimumMigrationTime(),
                HOST_OVER_UTILIZATION_THRESHOLD, fallback);

        return new DatacenterSimple(simulation, hostList, allocationPolicy);
    }

    private Host createHost() {
        final List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        HostSimple host = new HostSimple(ram, bw, storage, peList);
        host.enableStateHistory();
        host.setPowerModel(new PowerModelSpecPowerHpProLiantMl110G4Xeon3040());

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return host;
    }

    private List<Vm> createVms() {
        final List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            final Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10000);
            vm.getUtilizationHistory().enable();
            list.add(vm);
        }
        return list;
    }

    /**
     * Creates a list of Cloudlets setting their CPU UtilizationModel as
     * a {@link UtilizationModelPlanetLab} that read CPU utilization from
     * a PlanetLab trace file.
     */
    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        final UtilizationModel utilizationCpu =
            UtilizationModelPlanetLab.getInstance(TRACE_FILE, SCHEDULING_INTERVAL);

        // Should we follow the approach in MigrationExample2_PowerUsage?
        // It could be interesting to set the CPU of the last VM to increase
        // differently.
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet =
                new CloudletSimple(i, CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(utilizationCpu)
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.2))
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.4));
            list.add(cloudlet);
        }

        return list;
    }
}
