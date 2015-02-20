package com.jeffreybosboom.sokobondbot;

import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 2/18/2015
 */
public final class TwoLongPath implements Path {
	private final long a, b;
	private final byte length;
	private TwoLongPath(long a, long b, byte length) {
		this.a = a;
		this.b = b;
		this.length = length;
	}

	public static TwoLongPath empty() {
		return new TwoLongPath(0, 0, (byte)0);
	}

	@Override
	public TwoLongPath append(Direction d) {
		long bits = d.ordinal();
		int idx = length * 2;
		//somehow we're switching to b too early here, leaving top bits of a all 0
		if (idx < 64)
			return new TwoLongPath(a | (bits << idx), b, (byte)(length+1));
		else
			return new TwoLongPath(a, b | (bits << idx-64), (byte)(length+1));
	}

	@Override
	public Iterator<Direction> iterator() {
		return new Iterator<Direction>() {
			private int i = 0;
			@Override
			public boolean hasNext() {
				return i < length;
			}
			@Override
			public Direction next() {
				int ordinal;
				int idx = i * 2;
				if (idx < 63)
					ordinal = (int)((a & (0b11L << idx)) >>> idx);
				else
					ordinal = (int)((b & (0b11L << idx-64)) >>> idx-64);
				++i;
				return Direction.values()[ordinal];
			}
		};
	}

	@Override
	public String toString() {
		return StreamSupport.stream(spliterator(), false)
				.map(Direction::toString)
				.collect(Collectors.joining(", "));
	}
}
