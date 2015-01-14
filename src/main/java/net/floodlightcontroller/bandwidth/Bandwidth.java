/**
 *
Copyright 2014 Open Networking Laboratory

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package net.floodlightcontroller.bandwidth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.packetloss.Packetloss;
import net.floodlightcontroller.portinfo.Bytesinports;
import net.floodlightcontroller.portinfo.IPortinfoService;
import net.floodlightcontroller.portinfo.Packets;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;

/**
 * @author phillip
 * 
 */
public class Bandwidth implements IFloodlightModule, IOFMessageListener {

	protected static Logger log = LoggerFactory.getLogger(Bandwidth.class);

	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	protected ILinkDiscoveryService linkDiscovery;
	protected IPortinfoService portinfo;
	protected SingletonTask initTask;
	protected IThreadPoolService threadPool;

	protected Map<Link, LinkInfo> links;

	private Set<DatapathId> switches;

	private Map<DatapathId, List<Bytesinports>> bCounter;
	private Map<DatapathId, List<Bytesinports>> tempbCounter;

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.IListener#getName()
	 */
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "bandwidth";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.IListener#isCallbackOrderingPrereq(java
	 * .lang.Object, java.lang.String)
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		if (name.equals("portinfo")) {
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.IListener#isCallbackOrderingPostreq(java
	 * .lang.Object, java.lang.String)
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.IOFMessageListener#receive(net.
	 * floodlightcontroller.core.IOFSwitch, org.openflow.protocol.OFMessage,
	 * net.floodlightcontroller.core.FloodlightContext)
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.module.IFloodlightModule#getModuleServices
	 * ()
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.module.IFloodlightModule#getServiceImpls()
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.module.IFloodlightModule#getModuleDependencies
	 * ()
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		l.add(IThreadPoolService.class);
		l.add(IPortinfoService.class);
		l.add(IDeviceService.class);
		l.add(ILinkDiscoveryService.class);
		return l;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.module.IFloodlightModule#init(net.
	 * floodlightcontroller.core.module.FloodlightModuleContext)
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		this.floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		this.switchService = context.getServiceImpl(IOFSwitchService.class);
		this.linkDiscovery = context
				.getServiceImpl(ILinkDiscoveryService.class);
		this.portinfo = context.getServiceImpl(IPortinfoService.class);
		this.threadPool = context.getServiceImpl(IThreadPoolService.class);
		bCounter = new HashMap<>();
		tempbCounter = new HashMap<>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.module.IFloodlightModule#startUp(net.
	 * floodlightcontroller.core.module.FloodlightModuleContext)
	 */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {

		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		initTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() {

				do {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					switches = switchService.getAllSwitchDpids();
					log.info(switches == null ? "haven't got switch list yet"
							: "find " + switches.size() + " switches");
				} while (switches == null || switches.size() == 0);
				for (DatapathId sw : switches) {
					List<Bytesinports> b = new ArrayList<>();
					IOFSwitch iosw = switchService.getSwitch(sw);
					for (short i = 0; i < iosw.getPorts().size() - 1; i++) {
						Bytesinports bip = new Bytesinports();
						bip.setBytesRX(0);
						bip.setBytesTX(0);
						b.add(bip);
					}
					tempbCounter.put(sw, b);
				}
				bCounter = portinfo.getByteCounter();
				links = linkDiscovery.getLinks();
			}
		});
		initTask.reschedule(3, TimeUnit.SECONDS);
		Timer timer = new Timer();
		timer.schedule(new MyTask(), 5000, 5000);
	}

	class MyTask extends TimerTask {

		@Override
		public void run() {
			log.info("calculating bandwidth...");
			bCounter = portinfo.getByteCounter();
			links = linkDiscovery.getLinks();
			if (links.size() == 0) {
				log.info("no links!");
				return;
			}
			if (bCounter.size() == 0) {
				log.info("no data yet!");
				return;
			}
			for (Link l : links.keySet()) {
				long tx = bCounter.get(l.getSrc()).get(l.getSrcPort().getPortNumber() - 1)
						.getBytesTX()
						- tempbCounter.get(l.getSrc()).get(l.getSrcPort().getPortNumber() - 1)
								.getBytesTX();// total
												// bytes
												// sent
												// on
												// the
												// link
				log.info("bytes transferred on " + l.toString() + ": " + tx);
				log.info("bandwidth is :" + tx * 1.6 / 1000 + "KB/s");
			}
			deepCopy(bCounter, tempbCounter);
		}

	}

	public void deepCopy(Map<DatapathId, List<Bytesinports>> from,
			Map<DatapathId, List<Bytesinports>> to) {
		for (DatapathId l : from.keySet()) {
			List<Bytesinports> list = new ArrayList<>();
			for (int i = 0; i < from.get(l).size(); i++) {
				Bytesinports p = new Bytesinports();
				p.setBytesRX(from.get(l).get(i).getBytesRX());
				p.setBytesTX(from.get(l).get(i).getBytesTX());
				list.add(i, p);
			}
			to.put(l, list);
		}
	}

}
