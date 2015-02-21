package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableSortedSet;
import java.util.Set;

/**
 * Puzzle instances hold information related to the puzzle that does not depend
 * on the state, such as the boundary (blocked squares).  Puzzle instances are
 * passed to various solver methods to avoid storing a reference to them in
 * every State object.
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 2/21/2015
 */
public final class Puzzle {
	private final ImmutableSortedSet<Coordinate> boundary;
	private final State initialState;
	public Puzzle(Set<Coordinate> boundary, State initialState) {
		this.boundary = ImmutableSortedSet.copyOf(boundary);
		this.initialState = initialState;
	}
	public ImmutableSortedSet<Coordinate> boundary() {
		return boundary;
	}
	public State initialState() {
		return initialState;
	}
}
