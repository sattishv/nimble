package org.gridkit.nimble.zootest;

import javax.management.MBeanServerConnection;

import org.gridkit.nanocloud.CloudFactory;
import org.gridkit.nimble.driver.Activity;
import org.gridkit.nimble.driver.ExecutionDriver;
import org.gridkit.nimble.driver.ExecutionHelper;
import org.gridkit.nimble.driver.MeteringDriver;
import org.gridkit.nimble.driver.PivotMeteringDriver;
import org.gridkit.nimble.metering.DisrtibutedMetering;
import org.gridkit.nimble.metering.Measure;
import org.gridkit.nimble.metering.SampleFactory;
import org.gridkit.nimble.metering.SampleReader;
import org.gridkit.nimble.metering.SampleSchema;
import org.gridkit.nimble.metering.SampleWriter;
import org.gridkit.nimble.metering.SpanSamplerTemplate;
import org.gridkit.nimble.orchestration.Scenario;
import org.gridkit.nimble.orchestration.ScenarioBuilder;
import org.gridkit.nimble.pivot.Filters;
import org.gridkit.nimble.pivot.Pivot;
import org.gridkit.nimble.pivot.PivotPrinter;
import org.gridkit.nimble.pivot.display.DisplayBuilder;
import org.gridkit.nimble.pivot.display.PivotPrinter2;
import org.gridkit.nimble.print.PrettyPrinter;
import org.gridkit.nimble.probe.PidProvider;
import org.gridkit.nimble.probe.jmx.AbstractMBeanTrackingSamplerProvider;
import org.gridkit.nimble.probe.jmx.AbstractThreadSamplerProvider;
import org.gridkit.nimble.probe.jmx.JmxProbeFactory;
import org.gridkit.nimble.probe.jmx.JmxThreadProbe;
import org.gridkit.nimble.probe.jmx.LocalMBeanConnector;
import org.gridkit.nimble.probe.sigar.Sigar;
import org.gridkit.nimble.probe.sigar.SigarDriver;
import org.gridkit.nimble.probe.sigar.SigarMeasure;
import org.gridkit.vicluster.ViManager;
import org.junit.After;
import org.junit.Test;

public class ZooTest {

	private enum ZooMetrics {
		RUNMETRICS,
		THREAD_STATS,
		GC_STATS,
	}
	
//	private ViManager cloud = CloudFactory.createIsolateCloud();
	private ViManager cloud = CloudFactory.createLocalCloud();
	
	@After
	public void dropCloud() {
		cloud.shutdown();
	}
	
	@Test
	public void testMonitoring() {

		cloud.nodes("node11", "node12", "node22");		
		
		Pivot pivot = configurePivot();
		PivotMeteringDriver metrics = new PivotMeteringDriver(pivot, 16 << 10);
		
		Scenario scenario = createMonitoringTestScenario(metrics);
		
		scenario.play(cloud);
		
		print(metrics.getReporter().getReader());
				
		System.out.println();
//		PivotDumper.dump(metrics.getReporter());
		
		System.out.println("Done");
	}

	public void print(SampleReader reader) {
		PivotPrinter2 printer = new PivotPrinter2();
		printer.dumpUnprinted();
		
		DisplayBuilder.with(printer)
			.hostname()
			.nodename()
			.level("sigar-cpu-stats").constant("Source", "SIGAR")
			.level("sigar-cpu-stats").attribute("Name", SigarMeasure.MEASURE_KEY)
			.level("sigar-cpu-stats").attribute("PID", SigarMeasure.PID_KEY)
			.level("sigar-cpu-stats").frequencyStats()
			.level("jmx-cpu-stats").constant("Source", "JMX")
			.level("jmx-cpu-stats").measureName("Name")
			.level("jmx-cpu-stats").frequencyStats()
			.level("run-stats.*").constant("Source", "Task")
			.level("run-stats.*").measureName("Name")
			.level("run-stats.*").distributionStats().asMillis();
			
		PrettyPrinter pp = new PrettyPrinter();		
		pp.print(System.out, printer.print(reader));
		
	}
	
	@Test
	public void testScopeDerivation() {
		
		cloud.nodes("node11", "node12", "node22");		
		
		Pivot pivot = configurePivot();
		PivotMeteringDriver metrics = new PivotMeteringDriver(pivot, 16 << 10);
		
		Scenario scenario = createComplexDependencyTestScenario(metrics);
		
		scenario.play(cloud);
		
		PivotPrinter printer = new PivotPrinter(pivot, metrics.getReporter());
		
		PrettyPrinter pp = new PrettyPrinter();		
		pp.print(System.out, printer);
		System.out.println();
//		PivotDumper.dump(metrics.getReporter());
		
		System.out.println("Done");
	}

	private Pivot configurePivot() {
		Pivot pivot = new Pivot();
		
		pivot.root()
			.level("run-stats")
				.filter(Filters.notNull(ZooMetrics.RUNMETRICS))
				.group(MeteringDriver.HOSTNAME)
					.group(MeteringDriver.NODE)
						.group(Measure.NAME)
							.level("stats")
								.show()
								.display(MeteringDriver.NODE)
								.display(Measure.NAME)
								.calcDistribution(Measure.MEASURE)
								.displayDistribution(Measure.MEASURE);
		pivot.root()
			.level("jmx-cpu-stats")
				.filter(Filters.notNull(ZooMetrics.THREAD_STATS))
					.group(MeteringDriver.HOSTNAME)
						.group(MeteringDriver.NODE)
							.group(Measure.NAME)
								.level("")
									.show()
									.display(MeteringDriver.NODE)
									.display(Measure.NAME)
									.calcFrequency(Measure.MEASURE);
		
		pivot.root()
			.level("sigar-cpu-stats")
				.filter(Filters.notNull(SigarMeasure.PROBE_KEY))
				.group(MeteringDriver.HOSTNAME)
					.group(MeteringDriver.NODE)				
						.group(SigarMeasure.PROBE_KEY)
							.group(SigarMeasure.MEASURE_KEY)
								.group(SigarMeasure.PID_KEY)
									.level("")
										.show()
										.calcFrequency(Measure.MEASURE);
		
		return pivot;
	}

	private Scenario createMonitoringTestScenario(PivotMeteringDriver metrics) {
		
		ScenarioBuilder sb = new ScenarioBuilder();
		
		MeteringDriver metering = sb.deploy(metrics);
		ExecutionDriver executor = sb.deploy("node1*", ExecutionHelper.newDriver());
		
		ZooTestDriver zoo = sb.deploy("node1*", new ZooTestDriver.Impl());
		
		Runnable task = zoo.getReader();	
		
        SigarDriver sigar = sb.deploy("node22", new SigarDriver.Impl(2, 100));
        
        PidProvider provider = sigar.newPtqlPidProvider("Exe.Name.ct=java");

        sigar.monitorProcCpu(provider, metering.bind(Sigar.defaultReporter()));

        JmxThreadProbe probe = sb.deploy("**", JmxProbeFactory.newThreadProbe(new LocalMBeanConnector()));
        probe.addSampler(metering.bind(new TotalCpuSamplerProvider()));
        probe.addSampler(metering.bind(new FilteredCpuSamplerProvider()));
		
		sb.checkpoint("test-start");

		SpanSamplerTemplate t = new SpanSamplerTemplate();
		t.setStatic(ZooMetrics.RUNMETRICS, true);
		t.setStatic(Measure.NAME, "Reader");

		Activity run = executor.start(task, ExecutionHelper.constantRateExecution(10, 1, true), metering.bind(t));
		
		zoo.newSample(metering);
		
		sb.sleep(10000);

		run.stop();
		
		sb.checkpoint("test-finish");
		
		metering.flush();
		
		sb.fromStart();
		run.join();
		sb.join("test-finish");
		
		Scenario scenario = sb.getScenario();
		return scenario;
	}
	
	private Scenario createComplexDependencyTestScenario(PivotMeteringDriver metrics) {
		
		ScenarioBuilder sb = new ScenarioBuilder();
		
		MeteringDriver metering = sb.deploy(metrics);
		ExecutionDriver executor = sb.deploy("**", ExecutionHelper.newDriver());
		
		ZooTestDriver zoo1 = sb.deploy("node1*", new ZooTestDriver.Impl());
		ZooTestDriver zoo2 = sb.deploy("node2*", new ZooTestDriver.Impl());
		
		Runnable task1 = zoo1.getReader();	
		Runnable task2 = zoo2.getReader();	
		
		sb.checkpoint("test-start");

		SpanSamplerTemplate t = new SpanSamplerTemplate();
		t.setStatic(ZooMetrics.RUNMETRICS, true);
		t.setStatic(Measure.NAME, "Reader");

		Activity run1 = executor.start(task1, ExecutionHelper.constantRateExecution(10, 1, true), metering.bind(t));
		Activity run2 = executor.start(task2, ExecutionHelper.constantRateExecution(10, 1, true), metering.bind(t));
		
		zoo1.newSample(metering);
		zoo2.newSample(metering);
		
		sb.sleep(1000);

		run1.stop();
		run2.stop();
		
		sb.checkpoint("test-finish");
		
		metering.flush();
		
		sb.fromStart();
		run1.join();
		run2.join();
		sb.join("test-finish");
		
		Scenario scenario = sb.getScenario();
		return scenario;
	}	
	
	@SuppressWarnings("serial")
	private static class TotalCpuSamplerProvider extends AbstractThreadSamplerProvider {

		@Override
		protected SampleSchema configureConnectionSchema(MBeanServerConnection connection, SampleSchema root) {
			SampleSchema deriv = root.createDerivedScheme();
			deriv.setStatic(ZooMetrics.THREAD_STATS, true);
			deriv.declareDynamic(Measure.MEASURE, double.class);
			return deriv;
		}

		@Override
		protected void writeSample(SampleWriter writer, long threadId, double cpuTime, double userTime, double blockedTime, long blockedCount, double waitTime, long waitCount, long allocated) {
			writer.setMeasure(cpuTime);
		}
	}

	@SuppressWarnings("serial")
	private static class FilteredCpuSamplerProvider extends AbstractThreadSamplerProvider {
		
		@Override
		protected SampleSchema configureConnectionSchema(MBeanServerConnection connection, SampleSchema root) {
			SampleSchema deriv = root.createDerivedScheme();
			deriv.setStatic(ZooMetrics.THREAD_STATS, true);
			deriv.setStatic(Measure.NAME, "pool*");
			deriv.declareDynamic(Measure.MEASURE, double.class);
			return deriv;
		}
		
		@Override
		protected SampleSchema configureThreadSchema(String threadName, SampleSchema root) {
			if (threadName.toLowerCase().startsWith("pool")) {
				return root;
			}
			else {
				return null;
			}
		}

		@Override
		protected void writeSample(SampleWriter writer, long threadId, double cpuTime, double userTime, double blockedTime, long blockedCount, double waitTime, long waitCount, long allocated) {
			writer.setMeasure(cpuTime);
		}
	}
	
	private static class GcSamplerProvider extends AbstractMBeanTrackingSamplerProvider {

		@Override
		protected void report(SampleFactory factory, MBeanContext ctx) {
		}		
	}
}
