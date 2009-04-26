// Copyright (C) 2007 Steve Taylor.
// Distributed under the Toot Software License, Version 1.0. (See
// accompanying file LICENSE_1_0.txt or copy at
// http://www.toot.org/LICENSE_1_0.txt)

package uk.org.toot.swingui.audioui.serverui;

import uk.org.toot.audio.server.*;
import uk.org.toot.swingui.audioui.serverui.spi.AudioServerUIServiceProvider;
import uk.org.toot.audio.id.ProviderId;
import javax.swing.JComponent;

public class ASIOAudioServerUIServiceProvider
    extends AudioServerUIServiceProvider
{
    public ASIOAudioServerUIServiceProvider() {
        super(ProviderId.TOOT_PROVIDER_ID, "Toot Software", "ASIO Audio Server UIs", "0.1");
        // we lazily omit to register individual UI services
        // you can't really choose one
        // you need one that matches the server you've chosen
        // which is what createServerUI does
        // effectively the Abstract Factory design pattern where this
        // is a concrete factory implemented as a plugin service provider
        // or more simply, it works
    }

    public JComponent createServerUI(AudioServer server, AudioServerConfiguration p) {
        if ( server instanceof ASIOAudioServer ) {
            return new ASIOAudioServerPanel((ASIOAudioServer)server, p);
        }
        return null; // we can't provide the UI, try another provider
    }
}
