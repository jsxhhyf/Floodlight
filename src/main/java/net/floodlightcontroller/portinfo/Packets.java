package net.floodlightcontroller.portinfo;


public class Packets {

	private long packetTX = 0;
	private long packetRX = 0;

	public long getPacketTX() {
		return packetTX;
	}

	public void setPacketTX(long packetTX) {
		this.packetTX = packetTX;
	}

	public long getPacketRX() {
		return packetRX;
	}

	public void setPacketRX(long packetRX) {
		this.packetRX = packetRX;
	}

}