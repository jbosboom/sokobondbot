package com.jeffreybosboom.sokobondbot;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 2/22/2015
 */
public enum Element {
	HELIUM(0xFFFFFFFF), HYDROGEN(0xFFFE8A80), OXYGEN(0xFFA6C4FD), NITROGEN(0xFFAFDC8A), CARBON(0xFFFED776);
	private final int color;

	private Element(int color) {
		this.color = color;
	}

	public int color() {
		return color;
	}

	public int maxElectrons() {
		return ordinal();
	}

	public static Element fromColor(int color) {
		for (Element e : values())
			if (e.color() == color)
				return e;
		return null;
	}
}
