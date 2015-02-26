package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedMultiset;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multiset;
import java.util.Map;
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
	private final ImmutableSortedMap<Coordinate, Element> atoms;
	private final ImmutableSortedMultiset<Pair<Coordinate, Coordinate>> bonds;
	private final Coordinate playerAtom;
	public Puzzle(Set<Coordinate> boundary, Map<Coordinate, Element> atoms,
			Multiset<Pair<Coordinate, Coordinate>> bonds, Coordinate playerAtom) {
		this.boundary = ImmutableSortedSet.copyOf(boundary);
		this.atoms = ImmutableSortedMap.copyOf(atoms);
		this.bonds = ImmutableSortedMultiset.copyOf(Pair.comparator(), bonds);
		this.playerAtom = playerAtom;
	}
	public ImmutableSortedSet<Coordinate> boundary() {
		return boundary;
	}
	public ImmutableSortedMap<Coordinate, Element> atoms() {
		return atoms;
	}
	public ImmutableSortedMultiset<Pair<Coordinate, Coordinate>> bonds() {
		return bonds;
	}
	public Coordinate playerAtom() {
		return playerAtom;
	}
}
