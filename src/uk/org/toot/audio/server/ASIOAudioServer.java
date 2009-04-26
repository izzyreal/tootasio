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
	private Set<AsioChannelInfo> activeChannels = new HashSet<AsioChannelInfo>();
	private String driverName;
	private String sampleTypeName = null;
	
	public ASIOAudioServer(String driverName) {
		driver = JAsioHost.getAsioDriver(driverName);
		driver.addAsioDriverListener(new DriverListener());
		bufferFrames = driver.getBufferPreferredSize();
		this.driverName = driverName;
		System.out.println(driverName+": "+bufferFrames+" frames @ "+(int)getSampleRate()+"Hz");
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
		return driver.getState().equals(AsioDriverState.RUNNING);
	}

	protected void createSampleTypeName(AsioChannelInfo info) {
		sampleTypeName = info.getSampleType().name().substring(6);
	}
	
	public List<String> getAvailableInputNames() {
		List<String> names = new java.util.ArrayList<String>();
		AsioChannelInfo info;
		int num = driver.getNumChannelsInput();
		for ( int i = 0; i < num; i += 2 ) { // Stereo
			info = driver.getChannelInfoInput(i);
			names.add(info.getChannelName());
		}
		return names;
	}

	public List<String> getAvailableOutputNames() {
		List<String> names = new java.util.ArrayList<String>();
		AsioChannelInfo info;
		int num = driver.getNumChannelsOutput();
		for ( int i = 0; i < num; i += 2 ) { // Stereo
			info = driver.getChannelInfoOutput(i);
			names.add(info.getChannelName());
		}
		return names;
	}

	public IOAudioProcess openAudioInput(String name, String label) throws Exception {
		AsioChannelInfo info, info2;
		int num = driver.getNumChannelsInput();
		for ( int i = 0; i < num; i += 2 ) { // !!!
			info = driver.getChannelInfoInput(i);
			if ( name == null || info.getChannelName().equals(name) ) {
				info2 = driver.getChannelInfoInput(1+i);
				if ( name == null ) name = info.getChannelName();
				IOAudioProcess process = 
					new ASIOInputProcess(label, info, info2, name);
				process.open();
				return process;
			}
		}
		return null;
	}

	public IOAudioProcess openAudioOutput(String name, String label) throws Exception {
		AsioChannelInfo info, info2;
		int num = driver.getNumChannelsOutput();
		for ( int i = 0; i < num; i += 2 ) { // !!!
			info = driver.getChannelInfoOutput(i);
			if ( name == null || info.getChannelName().equals(name) ) {
				info2 = driver.getChannelInfoOutput(1+i);
				IOAudioProcess process = 
					new ASIOOutputProcess(label, info, info2);
				process.open();
				if ( sampleTypeName == null ) createSampleTypeName(info);
				return process;
			}
		}
		return null;
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
/*	    for ( AsioChannelInfo info2 : activeChannels ) {
	    	System.out.println(info2);
	        System.out.println(info2.getByteBuffer());
	    } */
		driver.start();
	}

	public void stopImpl() {
		driver.stop();
		driver.disposeBuffers();
	}

	protected class DriverListener implements AsioDriverListener
	{
		public void bufferSwitch(long sampleTime, long samplePosition, Set<AsioChannelInfo> activeChannels) {
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
		protected AsioChannelInfo info0, info1;
		
		public ASIOProcess(String name, AsioChannelInfo info0, AsioChannelInfo info1) {
			this.name = name;
			this.info0 = info0;
			this.info1 = info1;
/*			System.out.println(name);
			System.out.println("  Left : "+info0.getChannelName()+", "+info0.getChannelIndex());
			System.out.println("  Right: "+info1.getChannelName()+", "+info1.getChannelIndex()); */
		}
		
		public ChannelFormat getChannelFormat() {
			return ChannelFormat.STEREO;
		}

		public String getName() {
			return name;
		}

		public void open() throws Exception {
			activeChannels.add(info0);
			activeChannels.add(info1);
		}

		public void close() throws Exception {
			// this won't have any effect until next server stop/start
			activeChannels.remove(info0);
			activeChannels.remove(info1);
		}
	}
	
	protected class ASIOInputProcess extends ASIOProcess
	{
		private AudioBuffer.MetaInfo metaInfo;
		
		public ASIOInputProcess(String name, AsioChannelInfo info0, AsioChannelInfo info1, String location) {
			super(name, info0, info1);
            metaInfo = new AudioBuffer.MetaInfo(name, location);
		}

		public int processAudio(AudioBuffer buffer) {
            if ( !buffer.isRealTime() ) return AUDIO_DISCONNECT;
            buffer.setMetaInfo(metaInfo);
			buffer.setChannelFormat(ChannelFormat.STEREO);
			info0.read(buffer.getChannel(0));
			info1.read(buffer.getChannel(1));
			return AUDIO_OK;
		}
		
	}

	protected class ASIOOutputProcess extends ASIOProcess
	{
		public ASIOOutputProcess(String name, AsioChannelInfo info0, AsioChannelInfo info1) {
			super(name, info0, info1);
		}

		public int processAudio(AudioBuffer buffer) {
            if ( !buffer.isRealTime() ) return AUDIO_DISCONNECT;
			info0.write(buffer.getChannel(0));
			info1.write(buffer.getChannel(1));
			return AUDIO_OK;
		}
	}
}
