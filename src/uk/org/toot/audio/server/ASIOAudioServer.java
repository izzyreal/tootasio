// Copyright (C) 2009 Steve Taylor.
// Distributed under the Toot Software License, Version 1.0. (See
// accompanying file LICENSE_1_0.txt or copy at
// http://www.toot.org.uk/LICENSE_1_0.txt)

package uk.org.toot.audio.server;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.org.toot.audio.core.AudioBuffer;
import uk.org.toot.audio.core.ChannelFormat;

import com.synthbot.jasiohost.*;

public class ASIOAudioServer extends AbstractAudioServer implements AudioServer
{
	private AsioDriver driver;
	private Set<AsioChannel> activeChannels = new HashSet<AsioChannel>();
	private String driverName;
	private String sampleTypeName = null;
	private List<ASIONamedIO> availableInputs = new java.util.ArrayList<ASIONamedIO>();
	private List<ASIONamedIO> availableOutputs = new java.util.ArrayList<ASIONamedIO>();
	
	public ASIOAudioServer(String driverName) {
		driver = AsioDriver.getDriver(driverName);
		driver.addAsioDriverListener(new DriverListener());
		bufferFrames = driver.getBufferPreferredSize();
		this.driverName = driverName;
		System.out.println(driverName+": "+bufferFrames+" frames @ "+(int)getSampleRate()+"Hz");
		detectInputs();
		detectOutputs();
	}
	
	public AsioDriver getDriver() {
		return driver;
	}
	
	public String getDriverName() {
		return driverName;
	}
	
	public String getSampleTypeName() {
		return sampleTypeName;
	}
	
	public int getInputLatencyFrames() {
		return driver.getLatencyInput();
	}

	public int getOutputLatencyFrames() {
		return driver.getLatencyOutput();
	}

	public int getTotalLatencyFrames() {
		return driver.getLatencyInput()+driver.getLatencyOutput();
	}

	public float getSampleRate() {
		return (float)driver.getSampleRate();
	}

	public boolean isRunning() {
		return driver.getCurrentState().equals(AsioDriverState.RUNNING);
	}

	protected void createSampleTypeName(AsioChannel info) {
		sampleTypeName = info.getSampleType().name().substring(6);
	}
	
	public List<String> getAvailableInputNames() {
		List<String> names = new java.util.ArrayList<String>();
		for ( ASIONamedIO io : availableInputs ) {
			names.add(io.name);
		}
		return names;
	}

	public List<String> getAvailableOutputNames() {
		List<String> names = new java.util.ArrayList<String>();
		for ( ASIONamedIO io : availableOutputs ) {
			names.add(io.name);
		}
		return names;
	}

	public IOAudioProcess openAudioInput(String name, String label) throws Exception {
		ASIONamedIO io = availableInputs.get(0);
		if ( name != null ) {
			for ( ASIONamedIO i : availableInputs ) {
				if ( i.name.equals(name) ) {
					io = i;
					break;
				}
			}
		}
		IOAudioProcess process = null;
		AsioChannel info = driver.getChannelInput(io.first);
		switch ( io.count ) {
		case 1:
		    process = new ASIOMonoInputProcess(label, info, io.name);
			break;
		default:
			AsioChannel info2 = driver.getChannelInput(io.first+1);
		    process = new ASIOStereoInputProcess(label, info, info2, io.name);
			break;
		}
		System.out.println("Opening "+io.name);
		process.open();
		return process;
	}

	public IOAudioProcess openAudioOutput(String name, String label) throws Exception {
		ASIONamedIO io = availableOutputs.get(0);
		if ( name != null ) {
			for ( ASIONamedIO i : availableOutputs ) {
				if ( i.name.equals(name) ) {
					io = i;
					break;
				}
			}
		}
		IOAudioProcess process = null;
		AsioChannel info = driver.getChannelOutput(io.first);
		if ( sampleTypeName == null ) createSampleTypeName(info);
		switch ( io.count ) {
/*		case 1:
		    process = new ASIOMonoOutputProcess(label, info, io.name);
			break; */
		default:
			AsioChannel info2 = driver.getChannelOutput(io.first+1);
		    process = new ASIOStereoOutputProcess(label, info, info2);
			break;
		}
		System.out.println("Opening "+io.name);
		process.open();
		return process;
	}

	public void closeAudioInput(IOAudioProcess input) {
		if ( input != null ) {
			try {
				input.close();		
			} catch ( Exception e ) {
				//
			}
		}
	}

	public void closeAudioOutput(IOAudioProcess output) {
		if ( output != null ) {
			try {
				output.close();		
			} catch ( Exception e ) {
				//
			}
		}
	}

	public void startImpl() {
		int bf = driver.getBufferPreferredSize();
		if ( bufferFrames != bf ) {
			resizeBuffers(bf);
		}
		driver.createBuffers(activeChannels);
		driver.start();
	}

	public void stopImpl() {
		driver.stop();
		driver.disposeBuffers();
	}

	protected class DriverListener implements AsioDriverListener
	{
		public void bufferSwitch(long sampleTime, long samplePosition, Set<AsioChannel> activeChannels) {
			work();
		}

		public void latenciesChanged(int inputLatency, int outputLatency) {
			// ! does not imply buffer size has changed
			System.out.println("ASIO Latencies Changed: in "+inputLatency+", out "+outputLatency);		
		}

		public void bufferSizeChanged(int bufferSize) {
			System.out.println("ASIO Buffer Size Changed: from "+bufferFrames+" to "+bufferSize);		
		}

		public void resetRequest() {
			System.out.println("ASIO Reset Request");
		}

		public void resyncRequest() {
			System.out.println("ASIO Resync Request");
		}

		public void sampleRateDidChange(double sampleRate) {
			System.out.println("ASIO Sample rate Changed: sampleRate");
		}		
	}
	
	protected abstract class ASIOProcess implements IOAudioProcess
	{
		private String name;
		protected AsioChannel info0, info1;
		protected ChannelFormat format;
		
		public ASIOProcess(String name, AsioChannel info0, AsioChannel info1) {
			this.name = name;
			this.info0 = info0;
			this.info1 = info1; // may be null
			format = info1 == null ? ChannelFormat.MONO : ChannelFormat.STEREO;
		}
		
		public ChannelFormat getChannelFormat() {
			return format;
		}

		public String getName() {
			return name;
		}

		public void open() throws Exception {
			activeChannels.add(info0);
			if ( info1 != null ) {
				activeChannels.add(info1);
			}
		}

		public void close() throws Exception {
			// this won't have any effect until next server stop/start
			activeChannels.remove(info0);
			if ( info1 != null ) {
				activeChannels.remove(info1);
			}
		}
	}
	
	protected class ASIOMonoInputProcess extends ASIOProcess
	{
		private AudioBuffer.MetaInfo metaInfo;
		
		public ASIOMonoInputProcess(String name, AsioChannel info0, String location) {
			super(name, info0, null);
            metaInfo = new AudioBuffer.MetaInfo(name, location);
		}

		public int processAudio(AudioBuffer buffer) {
            if ( !buffer.isRealTime() ) return AUDIO_DISCONNECT;
            buffer.setMetaInfo(metaInfo);
			buffer.setChannelFormat(format);
			info0.read(buffer.getChannel(0));
			return AUDIO_OK;
		}
		
	}

	protected class ASIOStereoInputProcess extends ASIOProcess
	{
		private AudioBuffer.MetaInfo metaInfo;
		
		public ASIOStereoInputProcess(String name, AsioChannel info0, AsioChannel info1, String location) {
			super(name, info0, info1);
            metaInfo = new AudioBuffer.MetaInfo(name, location);
		}

		public int processAudio(AudioBuffer buffer) {
            if ( !buffer.isRealTime() ) return AUDIO_DISCONNECT;
            buffer.setMetaInfo(metaInfo);
			buffer.setChannelFormat(format);
			info0.read(buffer.getChannel(0));
			info1.read(buffer.getChannel(1));
			return AUDIO_OK;
		}
		
	}

	protected class ASIOStereoOutputProcess extends ASIOProcess
	{
		public ASIOStereoOutputProcess(String name, AsioChannel info0, AsioChannel info1) {
			super(name, info0, info1);
		}

		public int processAudio(AudioBuffer buffer) {
            if ( !buffer.isRealTime() ) return AUDIO_DISCONNECT;
			info0.write(buffer.getChannel(0));
			info1.write(buffer.getChannel(1));
			return AUDIO_OK;
		}
	}

	/* Merges names to make Stereo out of dual Mono
	 * i.e. Analog In 1 Delta-66 [1]
	 * and  Analog In 2 Delta-66 [1]
	 * are merged to
	 *      Analog In 1/2 Delta-66 [1]
	 * Typically only ASIO inputs are naturally Mono
	 */
	protected String mergeNames(String name0, String name1) {
		String[] parts0 = name0.split("\\s");
		String[] parts1 = name1.split("\\s");
		StringBuffer name = new StringBuffer();
		for ( int i = 0; i < parts0.length; i++) {
			if ( parts0[i].equals(parts1[i]) ) {
				name.append(parts0[i]);
			} else {
				name.append(parts0[i]);
				name.append('/');
				name.append(parts1[i]);
			}
			name.append(' ');
		}
		return name.toString().trim();
	}
	
	protected String splitNames(String aname, boolean first) {
		String[] parts = aname.split("\\s");
		StringBuffer name = new StringBuffer();
		for ( int i = 0; i < parts.length; i++) {
			if ( !parts[i].contains("/") ) {
				name.append(parts[i]);
			} else {
				String[] subparts = parts[i].split("/");
				name.append(subparts[first ? 0 : 1]);
			}
			name.append(' ');
		}
		return name.toString().trim();
	}
	
	protected void detectInputs() {
		AsioChannel info, info1;
		String name0, name1;
		int num = driver.getNumChannelsInput();
//		System.out.println("Stereo ASIO Inputs");
		for ( int i = 0; i < num; i += 2 ) { // Stereo
			info = driver.getChannelInput(i);
			name0 = info.getChannelName();
			name1 = driver.getChannelInput(i+1).getChannelName();
			String name = mergeNames(name0, name1);
			availableInputs.add(new ASIONamedIO(name, i, 2));
//			System.out.println(i+", "+name);
		}
//		System.out.println("Mono ASIO Inputs");
		for ( int i = 0; i < num; i += 2 ) { // Mono
			info = driver.getChannelInput(i);
			name0 = info.getChannelName();
			info1 = driver.getChannelInput(i+1);
			name1 = info1.getChannelName();
			if ( name0.equals(name1) ) {
			    name0 = splitNames(name0, true);
			    name1 = splitNames(name1, false);
			}
			availableInputs.add(new ASIONamedIO(name0, i, 1));
//			System.out.println(i+", "+name0);
			availableInputs.add(new ASIONamedIO(name0, i+1, 1));
//			System.out.println((i+1)+", "+name1);
		}
	}
	
	protected void detectOutputs() {
		AsioChannel info, info1;
		String name0, name1;
		int num = driver.getNumChannelsOutput();
//		System.out.println("Stereo ASIO Outputs");
		for ( int i = 0; i < num; i += 2 ) { // Stereo
			info = driver.getChannelOutput(i);
			name0 = info.getChannelName();
			name1 = driver.getChannelOutput(i+1).getChannelName();
			String name = mergeNames(name0, name1);
			availableOutputs.add(new ASIONamedIO(name, i, 2));
//			System.out.println(i+", "+name);
		}
//		System.out.println("Mono ASIO Outputs");
		for ( int i = 0; i < num; i += 2 ) { // Mono
			info = driver.getChannelOutput(i);
			name0 = info.getChannelName();
			info1 = driver.getChannelOutput(i+1);
			name1 = info1.getChannelName();
			if ( name0.equals(name1) ) {
			    name0 = splitNames(name0, true);
			    name1 = splitNames(name1, false);
			}
			availableOutputs.add(new ASIONamedIO(name0, i, 1));
//			System.out.println(i+", "+name0);
			availableOutputs.add(new ASIONamedIO(name0, i+1, 1));
//			System.out.println((i+1)+", "+name1);
		}
	}
	
	protected class ASIONamedIO
	{
		private String name;
		private int first;
		private int count;
		
		public ASIONamedIO(String name, int first, int count) {
			this.name = name;
			this.first = first;
			this.count = count;
		}
	}
}
