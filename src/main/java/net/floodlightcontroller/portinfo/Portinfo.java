package net.floodlightcontroller.portinfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageDamper;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class Portinfo implements IFloodlightModule, IOFMessageListener,
		IPortinfoService {

	protected static Logger log = LoggerFactory.getLogger(Portinfo.class);

	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;

	protected OFMessageDamper messageDamper;

	private List<OFStatsReply> switchReply;

	private Set<DatapathId> switches;

	private Map<DatapathId, List<Packets>> pktCounter;
	private Map<DatapathId, List<Bytesinports>> byteCounter;
	private Map<DatapathId, List<Lost>> lostCounter;

	public void setFloodlightProvider(
			IFloodlightProviderService floodlightProvider) {
		this.floodlightProvider = floodlightProvider;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "portinfo";
	}

	public List<OFStatsReply> getStatisticsReply() {
		return switchReply;
	}

	public Set<DatapathId> getSwitches() {
		return this.switches;
	}

	public void setSwitches(Set<DatapathId> switches) {
		this.switches = switches;
	}

	public Map<DatapathId, List<Bytesinports>> getByteCounter() {
		return byteCounter;
	}

	public void setByteCounter(Map<DatapathId, List<Bytesinports>> byteCounter) {
		this.byteCounter = byteCounter;
	}

	public Map<DatapathId, List<Packets>> getPktCounter() {
		return pktCounter;
	}

	public void setPktCounter(Map<DatapathId, List<Packets>> pktCounter) {
		this.pktCounter = pktCounter;
	}

	@SuppressWarnings("unchecked")
	protected void sendStatisticsRequest() {

		// log.info("--------------------SendingStatistics Request to switch.----------------------");
		switches = switchService.getAllSwitchDpids();
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
		OFStatsRequest<?> req = null;
		for (DatapathId sw : switches) {
			// OFEchoRequest echo = new OFEchoRequest();
			IOFSwitch iosw = switchService.getSwitch(sw);
			OFFactory ofFactory = iosw.getOFFactory();
			List<Packets> lPackets = new ArrayList<>();
			List<Bytesinports> lbytesList = new ArrayList<>();
			List<Lost> llost = new ArrayList<>();// by phil 2014-11-3 count
													// packet loss inside ports
			try {
				// iosw.write(echo, null);
				for (short i = 0; i < iosw.getPorts().size() - 1; i++) {
					if (ofFactory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
						req = ofFactory
								.buildPortStatsRequest()
								.setPortNo(
										org.projectfloodlight.openflow.types.OFPort
												.of(i)).build();
						if (req != null) {
							future = iosw.writeStatsRequest(req);
							values = (List<OFStatsReply>) future.get(10,
									TimeUnit.SECONDS);
							Packets p = new Packets();
							Bytesinports b = new Bytesinports();
							Lost l = new Lost();
							// log.info("get statistics reply on switch " + sw +
							// " port "
							// + (i + 1));
							b.setBytesTX(((OFPortStatsEntry) values.get(0))
									.getTxBytes().getValue());
							p.setPacketTX(((OFPortStatsEntry) values.get(0))
									.getTxPackets().getValue());
							l.setTXlost(((OFPortStatsEntry) values.get(0))
									.getTxDropped().getValue());// by phil
																// 2014-11-3
																// count
							// packet loss inside ports
							// log.info("sw" + sw + " port" + (i + 1) + " TX: "
							// +
							// p.getPacketTX());
							// log.info("TransmitedBytes: "
							// + b.getBytesTX()
							// + " || TransmitedBandwidth: ---"
							// + ((b.getBytesTX() - byteCounter.get(sw).get(i)
							// .getBytesTX()) * 1.6) + "b/s---");
							// out.write(b.getBytesTX()
							// + ","
							// + ((b.getBytesTX() - byteCounter.get(sw).get(i)
							// .getBytesTX()) * 1.6) + ",");

							// log.info("last tx: on switch " + sw + " port " +
							// (i + 1)
							// + " " + byteCounter.get(sw).get(i).getBytesTX());
							// tempByteCounter.get(sw).get(i).setBytesTX(b.getBytesTX());
							// tempPktCounter.get(sw).get(i).setPacketTX(p.getPacketTX());
							b.setBytesRX(((OFPortStatsEntry) values.get(0))
									.getRxBytes().getValue());
							p.setPacketRX(((OFPortStatsEntry) values.get(0))
									.getRxPackets().getValue());
							l.setRXlost(((OFPortStatsEntry) values.get(0))
									.getRxDropped().getValue());// by phil
																// 2014-11-3
																// count
							// packet loss inside ports
							// log.info("sw" + sw + " port" + (i + 1) + " RX: "
							// +
							// p.getPacketRX());
							// log.info("ReceivedBytes: "
							// + b.getBytesRX()
							// + " || ReceivedBandwidth: ---"
							// + ((b.getBytesRX() - byteCounter.get(sw).get(i)
							// .getBytesRX()) * 1.6) + "b/s---");
							// out.write(b.getBytesRX()
							// + ","
							// + ((b.getBytesRX() - byteCounter.get(sw).get(i)
							// .getBytesRX()) * 1.6) + ",");
							// log.info("last rx: on switch " + sw + " port " +
							// (i + 1)
							// + " " + byteCounter.get(sw).get(i).getBytesRX());
							// tempByteCounter.get(sw).get(i).setBytesRX(b.getBytesRX());
							lbytesList.add(b);
							lPackets.add(p);
							llost.add(l);
							// log.info("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");
							// out.flush();
							// if (sw == (long) 2 && i == (short) 0) {
							// bw[0][1] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 3 && i == (short) 2) {
							// bw[0][2] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 4 && i == (short) 1) {
							// bw[0][3] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 3 && i == (short) 0) {
							// bw[1][2] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 3 && i == (short) 1) {
							// bw[3][2] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 1 && i == (short) 3) {
							// bw[1][0] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 1 && i == (short) 5) {
							// bw[2][0] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 1 && i == (short) 4) {
							// bw[3][0] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 2 && i == (short) 1) {
							// bw[2][1] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// } else if (sw == (long) 4 && i == (short) 0) {
							// bw[2][3] = (double) (b.getBytesRX() - byteCounter
							// .get(sw).get(i).getBytesRX()) * 1.6 / 1000000;
							// }
						}
					}

				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			byteCounter.put(sw, lbytesList);
			pktCounter.put(sw, lPackets);
			lostCounter.put(sw, llost);
		}
		// log.info("----------------BW-------------------");
		// log.info("     |  SW1  |  SW2  |  SW3  |  SW4  ");
		// log.info("SW1  | " + df.format(bw[0][0]) + " | " +
		// df.format(bw[0][1])
		// + " | " + df.format(bw[0][2]) + " | " + df.format(bw[0][3]));
		// log.info("SW2  | " + df.format(bw[1][0]) + " | " +
		// df.format(bw[1][1])
		// + " | " + df.format(bw[1][2]) + " | " + df.format(bw[1][3]));
		// log.info("SW3  | " + df.format(bw[2][0]) + " | " +
		// df.format(bw[2][1])
		// + " | " + df.format(bw[2][2]) + " | " + df.format(bw[2][3]));
		// log.info("SW4  | " + df.format(bw[3][0]) + " | " +
		// df.format(bw[3][1])
		// + " | " + df.format(bw[3][2]) + " | " + df.format(bw[3][3]));
		// log.info("-------------------------------------");
		// log.info("");
		// try {
		// out.write("\n");
		// out.flush();
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
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
		return Command.CONTINUE;

	}

	// protected void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext
	// cntx) {
	// OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
	// .getMessage(OFType.PACKET_OUT);
	// List<OFAction> actions = new ArrayList<OFAction>();
	// if (sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)) {
	// actions.add(new OFActionOutput(OFPort.OFPP_FLOOD.getValue(),
	// (short) 0xFFFF));
	// } else {
	// actions.add(new OFActionOutput(OFPort.OFPP_ALL.getValue(),
	// (short) 0xFFFF));
	// }
	// po.setActions(actions);
	// po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
	//
	// // set buffer-id, in-port and packet-data based on packet-in
	// short poLength = (short) (po.getActionsLength() +
	// OFPacketOut.MINIMUM_LENGTH);
	// po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
	// po.setInPort(pi.getInPort());
	// byte[] packetData = pi.getPacketData();
	// poLength += packetData.length;
	// po.setPacketData(packetData);
	// po.setLength(poLength);
	//
	// try {
	// messageDamper.write(sw, po, cntx);
	// } catch (IOException e) {
	// log.error(
	// "Failure writing PacketOut switch={} packet-in={} packet-out={}",
	// new Object[] { sw, pi, po }, e);
	// }
	//
	// return;
	// }

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IPortinfoService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IPortinfoService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IOFSwitchService.class);
		l.add(IFloodlightProviderService.class);
		l.add(IThreadPoolService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		this.floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		this.switchService = context.getServiceImpl(IOFSwitchService.class);
		messageDamper = new OFMessageDamper(10000, EnumSet.of(OFType.FLOW_MOD),
				250);

		this.pktCounter = new HashMap<>();
		this.byteCounter = new HashMap<>();
		this.lostCounter = new HashMap<>();// by phil 2014-11-3 count packet
											// loss inside ports

		// this.bw = new double[4][4];
		// for (int i = 0; i < 4; i++) {
		// for (int j = 0; j < 4; j++) {
		// bw[i][j] = 0;
		// }
		// }
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {

		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		Timer timer = new Timer();
		Timer timer2 = new Timer();
		timer2.schedule(new Init_Task(), 1000);
		timer.schedule(new EchoTask(), 2500, 5000);

	}

	class Init_Task extends TimerTask {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			do {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				switches = switchService.getAllSwitchDpids();
			} while (switches.size() == 0);
			for (DatapathId sw : switches) {
				List<Bytesinports> l = new ArrayList<>();
				IOFSwitch iosw = switchService.getSwitch(sw);
				for (short i = 0; i < iosw.getPorts().size() - 1; i++) {
					Bytesinports bytesinports = new Bytesinports();
					bytesinports.setBytesRX(0);
					bytesinports.setBytesTX(0);
					l.add(bytesinports);
				}
				byteCounter.put(sw, l);
			}
		}

	}

	class EchoTask extends TimerTask {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				if (switches.size() == 0) {
					return;
				}
				sendStatisticsRequest();
			} catch (StorageException e) {
				log.error("Storage exception in Echo send timer; "
						+ "terminating process", e);
			} catch (Exception e) {
				log.error("Exception in Echo send timer.", e);
			}
		}

	}

	@Override
	public Map<DatapathId, List<Lost>> getLostCounter() {
		// TODO Auto-generated method stub
		return lostCounter;
	}
}
