package com.jeffreybosboom.sokobondbot;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 2/18/2015
 */
public interface Path extends Iterable<Direction> {
	public Path append(Direction d);
}
