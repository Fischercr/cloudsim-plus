package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.allocationpolicies.migration.VmAllocationPolicyMigrationSecurityAware;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSecurityAware;
import org.cloudbus.cloudsim.hosts.HostStateHistoryEntry;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G5Xeon3075;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.selectionpolicies.VmSelectionPolicyMinimumUtilization;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelPlanetLab;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.time.LocalTime;
import java.util.*;

/**
 * An implementation of Afoulki, Bousquet, and Rouzaud-Cornabas' Security-Aware
 * Scheduler for Virtual Machines on IaaS Clouds algorithm.
 *
 * @author Caitlin Fischer
 */
public class SecurityAwareScheduler {
    // private static final int VMS = 1052;
    private static final int VMS = 8;
    private static final int VM_BANDWIDTH = 100;  // In Mbits / s.
    private static final int VM_MIPS = 2500;
    private static final int VM_PES = 1;
    private static final int VM_RAM = 1024;  // In MB.
    private static final int VM_STORAGE = 2500;  // In MB.

    // private static final int HOSTS = 800;
    private static final int HOSTS = 6;
    // The default bandwidth capacity is 1000.
    private static final int HOST_BANDWIDTH = (VMS + 1) * VM_BANDWIDTH;
    // Should it be 1330 per PE instead of 2660?
    private static final int HOST_MIPS = 2660;
    private static final int HOST_PES = 2;
    private static final int HOST_RAM = 8192;  // In MB.
    private static final int HOST_STORAGE = 80000;  // In MB.

    private static final int CLOUDLETS = VMS - 1;
    private static final int CLOUDLET_FILE_SIZE = 256;
    // The length or size (in MI) of this cloudlet to be executed in a VM.
    private static final int CLOUDLET_LENGTH = 350000;
    private static final int CLOUDLET_PES = VM_PES;

    private static final String TRACE_FILE = "workload/planetlab/20110303/75-130-96-12_static_oxfr_ma_charter_com_irisaple_wup";

    /**
     * The time interval in which precise values can be got from
     * the PlanetLab {@link #TRACE_FILE}.
     * Such a value must be also defined as the Datacenter
     * scheduling interval to ensure that Cloudlets' processing
     * is updated with the values read from the trace file
     * at the given interval.
     *
     * @see UtilizationModelPlanetLab#getSchedulingInterval()
     */
    private static final int SCHEDULING_INTERVAL = 300;

    private static final double HOST_OVER_UTILIZATION_THRESHOLD = 0.9;
    private static final double HOST_UNDER_UTILIZATION_THRESHOLD = 0.1;

    private VmAllocationPolicyMigrationSecurityAware allocationPolicy;
    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new SecurityAwareScheduler();
    }

    private SecurityAwareScheduler() {
        System.out.println("Starting " + getClass().getSimpleName());
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
        // new CloudletsTableBuilder(doneCloudlets).build();
        new CloudletsTableBuilder(doneCloudlets).build();
        doneCloudlets.sort(
            Comparator.comparingLong((Cloudlet c) -> c.getVm().getHost().getId())
                      .thenComparingLong(c -> c.getVm().getId()));
        // new CloudletsTableBuilder(doneCloudlets).build();
        System.out.printf("%n    WHEN A HOST CPU ALLOCATED MIPS IS LOWER THAN THE REQUESTED, IT'S DUE TO VM MIGRATION OVERHEAD)%n%n");
        hostList.stream().forEach(this::printHistory);
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private Datacenter createDatacenter() {
        this.hostList = new ArrayList<>(HOSTS);

        for(int i = 0; i < HOSTS; i++) {
            Host host = createHost();
            hostList.add(host);
        }
        System.out.println();

        this.allocationPolicy =
            new VmAllocationPolicyMigrationSecurityAware(
                new VmSelectionPolicyMinimumUtilization(),
                HOST_OVER_UTILIZATION_THRESHOLD);

        DatacenterSimple dc = new DatacenterSimple(
            simulation, hostList, allocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        //List of Host's CPUs (Processing Elements, PEs)
        final List<Pe> peList = createPeList();

        // 1000 is the default bandwidth capacity.
        HostSecurityAware host = new HostSecurityAware(
            HOST_RAM, HOST_BANDWIDTH, HOST_STORAGE, peList);
        // Do we want SpaceShared or TimeShared?
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.enableStateHistory();
        host.setPowerModel(new PowerModelSpecPowerHpProLiantMl110G4Xeon3040());

        return host;
    }

    public List<Pe> createPeList() {
        List<Pe> list = new ArrayList<>(HOST_PES);
        for(int i = 0; i < HOST_PES; i++) {
            list.add(new PeSimple(HOST_MIPS, new PeProvisionerSimple()));
        }
        return list;
    }

    private List<Vm> createVms() {
        // Why is this list final?
        final List<Vm> list = new ArrayList<>(VMS);
        Random random = new Random(System.currentTimeMillis());     

        for (int i = 0; i < VMS; i++) {
            VmSimple vm = new VmSimple(i + 1, VM_MIPS, VM_PES);
            if (i > VMS / 2 + 1) {
                vm.setSubmissionDelay(2000);
            }
            vm.securityLevel = random.nextInt(10) + 1;
            vm.setRam(VM_RAM).setBw(VM_BANDWIDTH).setSize(VM_STORAGE)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
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
                    .setFileSize(CLOUDLET_FILE_SIZE)
                    .setOutputSize(CLOUDLET_FILE_SIZE)
                    .setUtilizationModelCpu(utilizationCpu)
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.6))
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.6));
            if (i > CLOUDLETS / 2 + 1) {
                cloudlet.setSubmissionDelay(2000);
            }
            list.add(cloudlet);
        }

        return list;
    }

    private void printHistory(Host host){
        if(printHostStateHistory(host)) {
            printHostCpuUsageAndPowerConsumption(host);
        }
    }

    /**
     * Prints Host state history
     * @param host the host to print information
     * @return true if the Host was powered on during simulation, false otherwise
     */
    private boolean printHostStateHistory(Host host) {
        if(host.getStateHistory().stream().anyMatch(HostStateHistoryEntry::isActive)) {
            System.out.printf("%nHost: %6d State History%n", host.getId());
            System.out.println("-------------------------------------------------------------------------------------------");
            host.getStateHistory().forEach(System.out::print);
            System.out.println();
            return true;
        }
        else System.out.printf("Host: %6d was powered off during all the simulation%n", host.getId());
        return false;
    }

    /**
     * Shows Host CPU utilization history and power consumption.
     * The history is shown in the interval defined by {@link #SCHEDULING_INTERVAL},
     * which is the interval in which simulation is updated and usage data is collected.
     *
     * <p>The Host CPU Utilization History also is only computed
     * if VMs utilization history is enabled by calling
     * {@code vm.getUtilizationHistory().enable()}
     * </p>
     *
     * @param host the Host to print information
     */
    private void printHostCpuUsageAndPowerConsumption(final Host host) {
        System.out.printf("Host: %6d | CPU Usage | Power Consumption in Watt-Second (Ws)%n", host.getId());
        System.out.println("-------------------------------------------------------------------------------------------");
        SortedMap<Double, DoubleSummaryStatistics> utilizationHistory = host.getUtilizationHistory();
        //The total power the Host consumed in the period (in Watt-Sec)
        double totalHostPowerConsumptionWattSec = 0;
        for (Map.Entry<Double, DoubleSummaryStatistics> entry : utilizationHistory.entrySet()) {
            final double time = entry.getKey();
            //The sum of CPU usage of every VM which has run in the Host
            final double hostCpuUsage = entry.getValue().getSum();
            System.out.printf("Time: %6.1f | %9.2f | %.2f%n", time, hostCpuUsage, host.getPowerModel().getPower(hostCpuUsage));
            totalHostPowerConsumptionWattSec += host.getPowerModel().getPower(hostCpuUsage);
        }
        System.out.printf("Total Host power consumption in the period: %.2f Watt-Sec%n", totalHostPowerConsumptionWattSec);
        System.out.println();
    }
}
