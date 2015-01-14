package net.floodlightcontroller.packetloss;

import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import net.floodlightcontroller.portinfo.IPortinfoService;
import net.floodlightcontroller.portinfo.Lost;
import net.floodlightcontroller.portinfo.Packets;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class Packetloss implements IFloodlightModule, IOFMessageListener {

	protected static Logger log = LoggerFactory.getLogger(Packetloss.class);

	protected IFloodlightProviderService floodlightProvider;
	protected ILinkDiscoveryService linkDiscovery;
	protected IPortinfoService portinfo;
	protected IDeviceService deviceService;
	protected IOFSwitchService switchService;

	protected SingletonTask initTask;
	protected IThreadPoolService threadPool;

	private Set<DatapathId> switches;

	private Map<DatapathId, List<Packets>> pktCounter;
	private Map<DatapathId, List<Packets>> tempPktCounter;
	private Map<DatapathId, List<Lost>> lostCounter;
	private Map<DatapathId, List<Lost>> tempLostCounter;

	protected Map<Long, Set<Link>> switchLinks;
	protected Map<Link, LinkInfo> links;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "packetloss";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		if (name.equals("portinfo")) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IThreadPoolService.class);
		l.add(IPortinfoService.class);
		l.add(IDeviceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		this.floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		this.linkDiscovery = context
				.getServiceImpl(ILinkDiscoveryService.class);
		this.portinfo = context.getServiceImpl(IPortinfoService.class);
		this.threadPool = context.getServiceImpl(IThreadPoolService.class);
		this.deviceService = context.getServiceImpl(IDeviceService.class);
		this.switchService = context.getServiceImpl(IOFSwitchService.class);
		pktCounter = new HashMap<>();
		tempPktCounter = new HashMap<>();
		lostCounter = new HashMap<>();
		tempLostCounter = new HashMap<>();
	}

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
					List<Packets> l = new ArrayList<>();
					List<Lost> ll = new ArrayList<>();
					IOFSwitch iosw = switchService.getSwitch(sw);
					for (short i = 0; i < iosw.getPorts().size() - 1; i++) {
						Packets ps = new Packets();
						Lost lost = new Lost();
						ps.setPacketTX(0);
						ps.setPacketRX(0);
						lost.setRXlost(0);
						lost.setTXlost(0);
						l.add(ps);
						ll.add(lost);
					}
					tempPktCounter.put(sw, l);
					tempLostCounter.put(sw, ll);
				}
				pktCounter = portinfo.getPktCounter();
				lostCounter = portinfo.getLostCounter();
				links = linkDiscovery.getLinks();

			}
		});
		initTask.reschedule(3, TimeUnit.SECONDS);
		Timer timer = new Timer();
		timer.schedule(new MyTask(), 7000, 5000);

	}

	public void deepCopy(Map<DatapathId, List<Packets>> from,
			Map<DatapathId, List<Packets>> to) {
		for (DatapathId l : from.keySet()) {
			List<Packets> list = new ArrayList<>();
			for (int i = 0; i < from.get(l).size(); i++) {
				Packets p = new Packets();
				p.setPacketRX(from.get(l).get(i).getPacketRX());
				p.setPacketTX(from.get(l).get(i).getPacketTX());
				list.add(i, p);
			}
			to.put(l, list);
		}
	}

	class MyTask extends TimerTask {

		@Override
		public void run() {
			log.info("calculating packet loss...");

			pktCounter = portinfo.getPktCounter();
			lostCounter = portinfo.getLostCounter();

			links = linkDiscovery.getLinks();
			if (links.size() == 0) {
				log.info("no links!");
				return;
			}
			if (pktCounter.size() < 4) {
				log.info("no pkt yet!");
				return;
			}
			for (Link l : links.keySet()) {
				long tx = pktCounter.get(l.getSrc())
						.get(l.getSrcPort().getPortNumber() - 1).getPacketTX()
						- tempPktCounter.get(l.getSrc())
								.get(l.getSrcPort().getPortNumber() - 1)
								.getPacketTX();// total
				// packages
				// sent
				// on
				// the
				// link
				long rx = pktCounter.get(l.getDst())
						.get(l.getDstPort().getPortNumber() - 1).getPacketRX()
						- tempPktCounter.get(l.getDst())
								.get(l.getDstPort().getPortNumber() - 1)
								.getPacketRX();// total
				// packages
				// received
				// on
				// the
				// link
				long srcPortLost = lostCounter.get(l.getSrc())
						.get(l.getSrcPort().getPortNumber() - 1).getTXlost()
						- tempLostCounter.get(l.getSrc())
								.get(l.getSrcPort().getPortNumber() - 1)
								.getTXlost();// packets
				// dropped
				// on
				// the
				// src
				// port by phil 2014-11-04
				long dstPortLost = lostCounter.get(l.getDst())
						.get(l.getDstPort().getPortNumber() - 1).getRXlost()
						- tempLostCounter.get(l.getDst())
								.get(l.getDstPort().getPortNumber() - 1)
								.getRXlost();// packets
				// dropped
				// on
				// the
				// dst
				// port by phil 2014-11-04
				log.info("packet loss on link " + l.toString() + ": " + tx
						+ " - " + rx + " = " + (tx - rx));
				if (tx != rx) {
					log.info("packet loss rate is :"
							+ ((double) (tx - rx) / tx));
				}
			}
			deepCopy(pktCounter, tempPktCounter);
			tempLostCounter = (Map<DatapathId, List<Lost>>) ((HashMap) lostCounter)
					.clone();
		}

	}

}
