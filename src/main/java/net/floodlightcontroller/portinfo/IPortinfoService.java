package net.floodlightcontroller.portinfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IPortinfoService extends IFloodlightService {
	
	public Set<DatapathId> getSwitches();

	public Map<DatapathId, List<Packets>> getPktCounter();
	
	public Map<DatapathId, List<Bytesinports>> getByteCounter();
	
	public Map<DatapathId, List<Lost>> getLostCounter();
}
