package org.gridkit.nimble.probe.jmx;

import java.io.Serializable;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.gridkit.lab.monitoring.probe.PollProbe;
import org.gridkit.lab.monitoring.probe.PollProbeDeployer;
import org.gridkit.lab.monitoring.probe.SamplerProvider;

public class MBeanProbe implements PollProbeDeployer<MBeanTarget, MBeanSampler>, Serializable {

	private static final long serialVersionUID = 20121017L;
	
	@Override
	public PollProbe deploy(MBeanTarget target, SamplerProvider<MBeanTarget, MBeanSampler> provider) {
		MBeanSampler sampler = provider.getSampler(target);
		if (sampler == null) {
			return null;
		}
		else {
			return new MBeanTracker(target.getConnection(), target.getMbeanName(), sampler);
		}
	}
	
	private static class MBeanTracker implements PollProbe {
		
		MBeanServerConnection connection;
		ObjectName name;
		MBeanSampler sampler;
		
		public MBeanTracker(MBeanServerConnection connection, ObjectName name, MBeanSampler sampler) {
			this.connection = connection;
			this.name = name;
			this.sampler = sampler;
		}

		@Override
		public void poll() {
			try {
				// validate connection
				connection.getMBeanCount();
			}
			catch(Exception e) {
				return;
			}
			sampler.report(connection, name);
		}

		@Override
		public void stop() {
			// do nothing			
		}
	}
}
