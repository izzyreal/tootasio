// Copyright (C) 2009 Steve Taylor.
// Distributed under the Toot Software License, Version 1.0. (See
// accompanying file LICENSE_1_0.txt or copy at
// http://www.toot.org.uk/LICENSE_1_0.txt)

package uk.org.toot.swingui.audioui.serverui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;

import uk.org.toot.audio.server.ASIOAudioServer;
import uk.org.toot.audio.server.AudioServerConfiguration;
import uk.org.toot.swing.SpringUtilities;

public class ASIOAudioServerPanel extends AbstractAudioServerPanel
{
	private ASIOAudioServer server;
	
	private JLabel sampleRateLabel;
	private JLabel sampleTypeLabel;
	private JLabel outputLatencyLabel;
	private JLabel inputLatencyLabel;
	private JLabel loadLabel;
	private JLabel stateLabel;
	
	public ASIOAudioServerPanel(ASIOAudioServer server, AudioServerConfiguration p) {
		this.server = server;
        setLayout(new BorderLayout());
        add(new JLabel(server.getDriverName(), JLabel.CENTER), BorderLayout.NORTH);
        add(buildStatusPanel(), BorderLayout.WEST);
        add(buildButtonPanel(), BorderLayout.SOUTH);
	}

    protected JPanel buildStatusPanel() {
		// Create and populate the panel.
		JPanel p = new JPanel(new SpringLayout());
        sampleRateLabel = new JLabel("44100", JLabel.CENTER);
        addRow(p, "Sample Rate", sampleRateLabel, "Hz");
        sampleTypeLabel = new JLabel("Float32LSB", JLabel.CENTER);
        addRow(p, "Sample Type", sampleTypeLabel, "");
        outputLatencyLabel = new JLabel("n/a" , JLabel.CENTER);
        addRow(p, "Output Latency", outputLatencyLabel, "ms");
        inputLatencyLabel = new JLabel("n/a" , JLabel.CENTER);
        addRow(p, "Input Latency", inputLatencyLabel, "ms");
        loadLabel = new JLabel("n/a", JLabel.CENTER);
        addRow(p, "Load", loadLabel, "%");
        stateLabel = new JLabel("Inactive", JLabel.CENTER);
        addRow(p, "State", stateLabel, "");
		// Lay out the panel.
		SpringUtilities.makeCompactGrid(p,
            gridRows, 3,	// rows, cols
            6, 6,       	// initX, initY
            6, 6);      	// xPad, yPad

        return p;
    }

    // @Override
  	public void updatePeriodic() {
        if ( !isShowing() ) return;
    	sampleRateLabel.setText(String.valueOf((int)server.getSampleRate()));
    	sampleTypeLabel.setText(server.getSampleTypeName());
  		float latencyMillis = 1000 * server.getOutputLatencyFrames() / server.getSampleRate();
    	outputLatencyLabel.setText(dpString(latencyMillis, 2));
  		latencyMillis = 1000 * server.getInputLatencyFrames() / server.getSampleRate();
    	inputLatencyLabel.setText(dpString(latencyMillis, 2));
    	loadLabel.setText(dpString(100 * server.getLoad(), 1));
    	stateLabel.setText(server.isRunning() ? "Active" : "Inactive");
  	}
  	
    protected JPanel buildButtonPanel() {
    	JPanel p = new JPanel();
       	p.add(new ASIOEditButton());
    	return p;
    }
    
    public class ASIOEditButton extends JButton implements ActionListener
	{
		public ASIOEditButton() {
			super("Edit");
			addActionListener(this);
		}
		
		public void actionPerformed(ActionEvent ae) {
			server.getDriver().openControlPanel();
		}
	}
}
