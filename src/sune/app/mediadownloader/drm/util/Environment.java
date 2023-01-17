package sune.app.mediadownloader.drm.util;

import static java.awt.DisplayMode.REFRESH_RATE_UNKNOWN;

import java.awt.GraphicsEnvironment;

public final class Environment {
	
	private static Environment instance;
	
	private final GraphicsEnvironment graphics;
	
	// Forbid anyone to create an instance of this class
	private Environment() {
		graphics = GraphicsEnvironment.getLocalGraphicsEnvironment();
	}
	
	public static final Environment instance() {
		if(instance == null) {
			instance = new Environment();
		}
		
		return instance;
	}
	
	public final double displayRefreshRate() {
		int refreshRate = graphics.getDefaultScreenDevice().getDisplayMode().getRefreshRate();
		
		if(refreshRate == REFRESH_RATE_UNKNOWN) {
			return 0.0;
		}
		
		switch(refreshRate) {
			// Return "effective" refresh rate when the display reports a value
			// rounded to the closest integer (i.e. rounded down). We actually
			// do not know whether the value was rounded to the closest integer
			// but it should be true in most cases.
			// This "list" contains the most common monitor refresh rates that
			// are rounded to the closest integer, i.e. 59.94, 23.97, etc.
			case 23:  //  24 / 1001 * 1000
			case 59:  //  60 / 1001 * 1000
			case 74:  //  75 / 1001 * 1000
			case 119: // 120 / 1001 * 1000
			case 143: // 144 / 1001 * 1000
			case 239: // 240 / 1001 * 1000
				return refreshRate + 1;
			default:
				return refreshRate;
		}
	}
}