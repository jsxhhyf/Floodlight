/**
 *
Copyright 2015 Open Networking Laboratory

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
package net.floodlightcontroller.flowadd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;

/**
 * @author phillip
 * 
 */
public class Flowadd implements IFloodlightModule, IOFMessageListener {

	public static final int FLOWADD_APP_ID = 10;

	protected static Logger log = LoggerFactory.getLogger(Flowadd.class);

	protected IOFSwitchService switchService;

	private Match match;

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
		l.add(IOFSwitchService.class);
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
		switchService = context.getServiceImpl(IOFSwitchService.class);

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
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.IListener#getName()
	 */
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
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
	 * floodlightcontroller.core.IOFSwitch,
	 * org.projectfloodlight.openflow.protocol.OFMessage,
	 * net.floodlightcontroller.core.FloodlightContext)
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			log.info("PACKET_IN packet received from sw" + sw.getId());
			OFFactory ofFactory = sw.getOFFactory();
			OFPacketIn pi = (OFPacketIn) msg;
			// only no match flow entry leads to add new flow entry
			if (!pi.getReason().equals(OFPacketInReason.NO_MATCH)) {
				log.info("not NO_MATCH and ignore it");
				return Command.CONTINUE;
			}
			match = pi.getMatch().createBuilder().wildcard(MatchField.IN_PORT)
					.wildcard(MatchField.ETH_SRC).build();
			switch ((int) sw.getId().getLong()) {
			case 1:
				if (pi.getInPort().getPortNumber() < 4) {
					addFlowMod(sw, match,
							(short) (pi.getInPort().getPortNumber() + 3));
				} else {
					addFlowMod(sw, match,
							(short) (pi.getInPort().getPortNumber() - 3));
				}
				break;
			case 2:
				if (pi.getInPort().getPortNumber() == 1) {
					addFlowMod(sw, match, (short) 2);
				} else {
					addFlowMod(sw, match, (short) 1);
				}
				break;
			case 3:
				if (pi.getInPort().getPortNumber() < 4) {
					addFlowMod(sw, match,
							(short) (pi.getInPort().getPortNumber() + 3));
				} else {
					addFlowMod(sw, match,
							(short) (pi.getInPort().getPortNumber() - 3));
				}
				break;
			case 4:
				if (pi.getInPort().getPortNumber() == 1) {
					addFlowMod(sw, match, (short) 2);
				} else {
					addFlowMod(sw, match, (short) 1);
				}
				break;
			default:
				break;
			}
			return Command.CONTINUE;
		default:
			return Command.CONTINUE;
		}
	}

	public void addFlowMod(IOFSwitch sw, Match match, short outport) {
		if (match.get(MatchField.ETH_DST).equals(
				MacAddress.of("01:80:c2:00:00:0e"))
				|| match.get(MatchField.ETH_DST).equals(
						MacAddress.of("ff:ff:ff:ff:ff:ff"))) {
			// log.info("LLDP or BBDP");//by phil 2014-10-31
			return;
		}
		// byte[] b = match.getDataLayerDestination();
		// for (int i = 0; i < b.length; i++) {
		// String hex = Integer.toHexString(b[i] & 0xFF);
		// if (hex.length() == 1) {
		// hex = "0" + hex;
		// }
		// log.info(hex.toUpperCase() + " ");
		// }

		OFFactory ofFactory = sw.getOFFactory();

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
				.setCookie(AppCookie.makeCookie(FLOWADD_APP_ID, 0))
				.setInstructions(instructionList).build();

		sw.write(flowAdd);
		log.info("flow table entry has been added on sw" + sw.getId() + ": "
				+ flowAdd.toString());

	}

}
