package net.floodlight.fileControl;

import java.io.FileWriter;
import java.io.IOException;

public class FileControl {

	/**
	 * Added by Phil 150326 to record the data to file each file represent a
	 * link
	 */
	private static String[] FILENAME;
	private static FileWriter[] fileWriter;

	/**
	 * Constructor with none params
	 */
	public FileControl() {
		this.FILENAME = new String[2];
		for (int i = 0; i < 2; i++) { // 0 for bandwidth 1 for packets
				FILENAME[i] = "records/record_" + i + ".txt";
		}
		this.fileWriter = new FileWriter[2];
		try {
			for (int i = 0; i < 2; i++) {
					fileWriter[i] = new FileWriter(FILENAME[i], true);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void record(int type, String content) {
		try {
			fileWriter[type].write(content);
			fileWriter[type].close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
