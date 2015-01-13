package net.floodlightcontroller.linkdelay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.util.HexString;
import org.python.antlr.PythonParser.else_clause_return;
import org.python.antlr.PythonParser.not_test_return;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.org.apache.bcel.internal.generic.NEW;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageDamper;

public class LinkDelay implements ILinkDelayService, IFloodlightModule,
		IOFMessageListener {

	public static final String LINKDELAY = "linkdelay";
	public static final int LINKDELAY_APP_ID = 5;

	protected static Logger log = LoggerFactory.getLogger(LinkDelay.class);

	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	protected OFMessageDamper messageDamper;
	protected SingletonTask addFlowTask, packetOutTask;
	protected IThreadPoolService threadPool;

	private Set<DatapathId> switches;

	private Integer wildcard_hints;

	private long sendTime, sendTime_r, sendTime1, sendTime2, receiveTime,
			receiveTime_r, receiveTime1, receiveTime2;
	private int c = 0;

	@Override
	public String getName() {
		return this.LINKDELAY;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		if (name.equals("linkdiscovery")) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		if (name.equals("forwarding")) {
			return true;
		}
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			// log.info("PACKET_IN received from sw" + sw.getId());
			// log.info(msg.getDataAsString(sw, msg, cntx));
			Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
					IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
			// log.info("Source MAC: " +
			// HexString.toHexString(eth.getSourceMACAddress()) + " Dest MAC: "
			// + HexString.toHexString(eth.getDestinationMACAddress()));
			// log.info("data: " + eth.getPayload().serialize()[0]);
			// log.info("inport: " + ((OFPacketIn) msg).getInPort());
			long tempTime = new Date().getTime();
			if (eth.getDestinationMACAddress().getBytes()
					.equals(HexString.fromHexString("00:00:00:12:34:56"))) {

				receiveTime2 = tempTime;
				log.info("Time2: " + (receiveTime2 - sendTime2));
				return Command.STOP;
			} else if (eth.getDestinationMACAddress().getBytes()
					.equals(HexString.fromHexString("00:00:00:12:34:57"))) {
				receiveTime1 = tempTime;
				log.info("Time1: " + (receiveTime1 - sendTime1));
				return Command.STOP;
			} else {
				if (c == 0) {
					receiveTime = tempTime;
					c++;
					log.info("Time delay 1-2: " + (receiveTime - sendTime));
					return Command.STOP;
				} else if (c == 1) {
					c = 0;
					receiveTime_r = tempTime;
					log.info("Time delay 2-1: " + (receiveTime_r - sendTime));

					log.info("latency time: "
							+ (receiveTime + receiveTime_r - receiveTime1 - receiveTime2));
					return Command.STOP;
				} else {
					return Command.CONTINUE;
				}
			}

		default:
			break;
		}

		return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ILinkDelayService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(ILinkDelayService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
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
		this.threadPool = context.getServiceImpl(IThreadPoolService.class);

		messageDamper = new OFMessageDamper(10000, EnumSet.of(OFType.FLOW_MOD),
				250);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		ScheduledExecutorService ses1 = threadPool.getScheduledExecutor();

		addFlowTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() {
				do {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					switches = switchService.getAllSwitchDpids();
					log.info("switch number: " + switches.size());
				} while (switches.size() == 0);
				// OFMatch ofm = new OFMatch();
				// ofm.setDataLayerDestination(floodlightProvider.getSwitch((long)2).getPort((short)1).getHardwareAddress());
				// ofm.setDataLayerSource(floodlightProvider.getSwitch((long)1).getPort((short)4).getHardwareAddress());
				// ofm.setDataLayerDestination("00:00:00:00:00:04");
				// ofm.setDataLayerSource("5e:a9:9b:a7:f7:2b");
				// addFlowMod(floodlightProvider.getSwitch((long) 1), ofm,
				// (short) 4, (short) 4);
				// addFlowMod(floodlightProvider.getSwitch((long) 2), ofm,
				// (short) 1, (short) 1);
				packetOutTask.reschedule(1, TimeUnit.SECONDS);
			}
		});
		packetOutTask = new SingletonTask(ses1, new Runnable() {
			public void run() {
				// do {
				// try {
				// Thread.sleep(1000);
				// } catch (InterruptedException e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// }
				// switches = floodlightProvider.getAllSwitchDpids();
				// } while (switches.size() == 0);

				sendPacketOut1();
				sendPacketOut2();
				sendPacketOut((long) 1, (short) 4);
				sendPacketOut_r((long) 2, (short) 1);

				packetOutTask.reschedule(2, TimeUnit.SECONDS);
			}
		});
		addFlowTask.reschedule(1, TimeUnit.SECONDS);

	}

	public void addFlowMod(IOFSwitch sw, short inport, short outport) {

		// wildcard_hints = ((Integer) sw
		// .getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
		// & ~OFMatch.OFPFW_IN_PORT;
		// & ~OFMatch.OFPFW_DL_VLAN
		// & ~OFMatch.OFPFW_DL_SRC;
		// & ~OFMatch.OFPFW_DL_DST;
		// & ~OFMatch.OFPFW_NW_SRC_MASK
		// & ~OFMatch.OFPFW_NW_DST_MASK;

		OFFactory ofFactory = sw.getOFFactory();
		Match match = ofFactory.buildMatch()
				.setExact(MatchField.IN_PORT, OFPort.of(inport)).build();
		OFActions actions = ofFactory.actions();
		OFActionOutput output = actions.buildOutput().setMaxLen((short) 0xffff)
				.setPort(OFPort.of(outport)).build();
		List<OFAction> actionList = new ArrayList<OFAction>();
		actionList.add(output);
		OFInstructions instructions = ofFactory.instructions();
		OFInstructionApplyActions applyActions = instructions
				.buildApplyActions().setActions(actionList).build();
		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();
		instructionList.add(applyActions);
		OFFlowAdd flowAdd = ofFactory.buildFlowAdd()
				.setBufferId(OFBufferId.NO_BUFFER).setIdleTimeout((short) 0)
				.setHardTimeout((short) 0).setPriority((short) 100)
				.setMatch(match)
				.setCookie(AppCookie.makeCookie(LINKDELAY_APP_ID, 0))
				.setInstructions(instructionList).build();

		sw.write(flowAdd);
		sw.flush();
		log.info("flow table entry has been added on sw" + sw.getId() + ": "
				+ flowAdd.toString());
	}

	public void sendPacketOut(long from_sw, short outPort) {

		// get switch
		IOFSwitch iofs_from = switchService.getSwitch(DatapathId.of(from_sw));
		IOFSwitch iofs_to = switchService.getSwitch(DatapathId.of(2));
		OFPortDesc ofpPort_from = iofs_from.getPort(OFPort.of(outPort));
		OFPortDesc ofpPort_to = iofs_to.getPort(OFPort.of(1));

		OFFactory ofFactory = iofs_from.getOFFactory();

		IPv4 data = new IPv4();
		data.setPayload(new Data(new byte[] { 'd', 'e', 'l', 'a', 'y' }));

		Ethernet ethernet = (Ethernet) new Ethernet()
				.setSourceMACAddress("5e:a9:9b:a7:f7:2b")
				.setDestinationMACAddress(ofpPort_to.getHwAddr())
				.setPayload(data);
		// set actions
		OFActionOutput output = ofFactory.actions().buildOutput()
				.setPort(OFPort.of(outPort)).build();
		// construct Packet_Out
		OFPacketOut po = ofFactory.buildPacketOut()
				.setData(ethernet.serialize())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setActions(Collections.singletonList((OFAction) output))
				.build();
		sendTime = new Date().getTime();
		log.info("sendtime: " + sendTime);
		iofs_from.write(po);
		log.info("delay packet sent to switch " + from_sw);

	}

	public void sendPacketOut_r(long from_sw, short outPort) {

		// get switch
		IOFSwitch iofs_from = switchService.getSwitch(DatapathId.of(from_sw));
		IOFSwitch iofs_to = switchService.getSwitch(DatapathId.of(1));
		OFPortDesc ofpPort_from = iofs_from.getPort(OFPort.of(outPort));
		OFPortDesc ofpPort_to = iofs_to.getPort(OFPort.of(4));

		OFFactory ofFactory = iofs_from.getOFFactory();

		IPv4 data = new IPv4();
		data.setPayload(new Data(new byte[] { 'd', 'e', 'l', 'a', 'y' }));

		Ethernet ethernet = (Ethernet) new Ethernet()
				.setSourceMACAddress("5e:a9:9b:a7:f7:2b")
				.setDestinationMACAddress(ofpPort_to.getHwAddr())
				.setPayload(data);
		// set actions
		OFActionOutput output = ofFactory.actions().buildOutput()
				.setPort(OFPort.of(outPort)).build();
		// construct Packet_Out
		OFPacketOut po = ofFactory.buildPacketOut()
				.setData(ethernet.serialize())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setActions(Collections.singletonList((OFAction) output))
				.build();
		sendTime = new Date().getTime();
		log.info("sendtime: " + sendTime);
		iofs_from.write(po);
		log.info("delay packet sent to switch " + from_sw);

	}

	public void sendPacketOut2() {

		// get switch
		IOFSwitch iofs_from = switchService.getSwitch(DatapathId.of(2));
		OFFactory ofFactory = iofs_from.getOFFactory();

		IPv4 data = new IPv4();
		data.setPayload(new Data(new byte[] { 'd', 'e', 'l', 'a', 'y' }));

		Ethernet ethernet = (Ethernet) new Ethernet()
				.setSourceMACAddress("5e:a9:9b:a7:f7:2b")
				.setDestinationMACAddress("00:00:00:12:34:56").setPayload(data);
		// set actions
		OFActionOutput output = ofFactory.actions().buildOutput()
				.setPort(OFPort.CONTROLLER).build();
		// construct Packet_Out
		OFPacketOut po = ofFactory.buildPacketOut()
				.setData(ethernet.serialize())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setActions(Collections.singletonList((OFAction) output))
				.build();
		sendTime2 = new Date().getTime();
		log.info("sendtime2: " + sendTime2);
		iofs_from.write(po);
		log.info("delay packet sent to switch 2");
	}

	public void sendPacketOut1() {

		// get switch
		IOFSwitch iofs_from = switchService.getSwitch(DatapathId.of(1));
		OFFactory ofFactory = iofs_from.getOFFactory();

		IPv4 data = new IPv4();
		data.setPayload(new Data(new byte[] { 'd', 'e', 'l', 'a', 'y' }));

		Ethernet ethernet = (Ethernet) new Ethernet()
				.setSourceMACAddress("5e:a9:9b:a7:f7:2b")
				.setDestinationMACAddress("00:00:00:12:34:56").setPayload(data);
		// set actions
		OFActionOutput output = ofFactory.actions().buildOutput()
				.setPort(OFPort.CONTROLLER).build();
		// construct Packet_Out
		OFPacketOut po = ofFactory.buildPacketOut()
				.setData(ethernet.serialize())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setActions(Collections.singletonList((OFAction) output))
				.build();
		sendTime2 = new Date().getTime();
		log.info("sendtime1: " + sendTime1);
		iofs_from.write(po);
		log.info("delay packet sent to switch 1");
	}
}
