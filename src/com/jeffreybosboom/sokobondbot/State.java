package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedMultiset;
import com.google.common.collect.Multiset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/27/2014
 */
public final class State {
	public static enum Element {
		//declared in order of max electron count
		HELIUM(0xFFFFFFFF),
		HYDROGEN(0xFFFE8A80),
		OXYGEN(0xFFA6C4FD),
		NITROGEN(0xFFAFDC8A),
		CARBON(0xFFFED776);
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

	private final ImmutableSortedMap<Coordinate, Element> atoms;
	private final ImmutableSortedMultiset<Pair<Coordinate, Coordinate>> bonds;
	private final Coordinate playerAtom;
	public State(Map<Coordinate, Element> atoms, Multiset<Pair<Coordinate, Coordinate>> bonds, Coordinate playerAtom) {
		this.atoms = ImmutableSortedMap.copyOf(atoms);
		this.bonds = ImmutableSortedMultiset.copyOf(Pair.comparator(), bonds);
		this.playerAtom = playerAtom;
	}

	public static int freeElectrons(Coordinate atom, Map<Coordinate, Element> atoms, Multiset<Pair<Coordinate, Coordinate>> bonds) {
		assert atoms.containsKey(atom);
		int b = countBonds(atom, bonds);
		int f = atoms.get(atom).maxElectrons() - b;
		assert f >= 0 : String.format("too many bonds %d for %s", b, atom);
		return f;
	}

	private static int countBonds(Coordinate atom, Multiset<Pair<Coordinate, Coordinate>> bonds) {
		return bonds.entrySet().stream()
				.filter(p -> p.getElement().first().equals(atom) || p.getElement().second.equals(atom))
				.mapToInt(Multiset.Entry::getCount)
				.sum();
	}

	public boolean isSolved() {
		return atoms.keySet().stream().noneMatch(a -> freeElectrons(a, atoms, bonds) > 0);
	}

	private Set<Coordinate> molecule(Coordinate atom) {
		//Flood-fill to find all atoms connected via bonds to this atom.
		Set<Coordinate> molecule = new HashSet<>();
		Deque<Coordinate> frontier = new ArrayDeque<>();
		molecule.add(atom);
		frontier.add(atom);
		while (!frontier.isEmpty())
			frontier.pop().neighbors()
					.map(n -> Pair.sorted(atom, n))
					.filter(bonds::contains)
					.map(b -> b.first().equals(atom) ? b.second() : b.first())
					.filter(molecule::add) //side-effecting
					.forEachOrdered(frontier::push);
		return molecule;
	}

	public List<State> nextStates(Set<Coordinate> boundary) {
		List<State> retval = new ArrayList<>(4);
		Set<Coordinate> molecule = molecule(playerAtom);
		for (Direction dir : Direction.values()) {
			Set<Coordinate> movingAtoms = tryTranslate(molecule, dir, boundary);
			if (movingAtoms == null) continue;
			//move the moving atoms, and no other atoms
			Map<Coordinate, Element> newAtoms = atoms.keySet().stream()
					.collect(toMap(c -> movingAtoms.contains(c) ? c.translate(dir) : c, atoms::get));
			ImmutableSortedMultiset<Pair<Coordinate, Coordinate>> translatedBonds = bonds.asList().stream()
					.map(b -> movingAtoms.contains(b.first()) || movingAtoms.contains(b.second) ? new Pair<>(b.first().translate(dir), b.second().translate(dir)) : b)
					.collect(() -> ImmutableSortedMultiset.orderedBy(Pair.<Coordinate, Coordinate>comparator()),
							ImmutableSortedMultiset.Builder::add,
							(left, right) -> left.addAll(right.build()))
					.build();
			Set<Coordinate> movedAtoms = movingAtoms.stream().map(dir::translate).collect(toSet());

			//If a moved atom is now adjacent to another atom, they both have
			//free electrons, and they aren't already bonded, bond them.  To
			//detect nondeterminism, we compute all the bonds, then commit all
			//at once (if possible).
			ImmutableSortedMultiset<Pair<Coordinate, Coordinate>> newlyFormedBonds = movedAtoms.stream()
					.flatMap(c -> c.neighbors()
					.filter(newAtoms::containsKey) //adjacent?
					.map(d -> Pair.sorted(c, d)))
					.filter(p -> !translatedBonds.contains(p)) //not already bonded?
					.filter(p -> freeElectrons(p.first, newAtoms, translatedBonds) > 0) //free electron?
					.filter(p -> freeElectrons(p.second, newAtoms, translatedBonds) > 0) //free electron?
					.collect(() -> ImmutableSortedMultiset.orderedBy(Pair.<Coordinate, Coordinate>comparator()),
							ImmutableSortedMultiset.Builder::add,
							(left, right) -> left.addAll(right.build()))
					.build();
			Multiset<Coordinate> atomsFormingBonds = newlyFormedBonds.elementSet().stream()
					.flatMap(p -> Stream.of(p.first(), p.second()))
					.collect(toSortedMultiset());
			for (Multiset.Entry<Coordinate> e : atomsFormingBonds.entrySet())
				if (e.getCount() > freeElectrons(e.getElement(), newAtoms, translatedBonds))
					throw new RuntimeException("nondeterminism"); //TODO: just ignore it?
			ImmutableSortedMultiset<Pair<Coordinate, Coordinate>> newBonds =
					Stream.concat(translatedBonds.stream(), newlyFormedBonds.stream())
					.collect(toSortedMultiset(Pair.comparator()));
			retval.add(new State(newAtoms, newBonds, playerAtom.translate(dir)));
		}
		return retval;
	}

	//returns a set of all atoms that should move (may include pushing other
	//molecules, and may exclude some atoms that were split from the molecule)
	private Set<Coordinate> tryTranslate(Set<Coordinate> molecule, Direction dir, Set<Coordinate> boundary) {
		Collection<Coordinate> projection = dir.project(molecule);
		if (projection.stream()
				.map(dir::translate)
				.filter(boundary::contains)
				.findAny().isPresent())
			return null;
		Set<Set<Coordinate>> pushedMolecules = projection.stream()
				.map(dir::translate)
				.filter(atoms::containsKey)
				.map(this::molecule)
				.collect(toSet());
		Set<Coordinate> movingAtoms = new HashSet<>();
		for (Set<Coordinate> m : pushedMolecules) {
			Set<Coordinate> subtranslate = tryTranslate(m, dir, boundary);
			if (subtranslate == null)
				return null;
			movingAtoms.addAll(subtranslate);
		}
		movingAtoms.addAll(molecule);
		return movingAtoms;
	}

	private static <T extends Comparable<T>> Collector<T, ImmutableSortedMultiset.Builder<T>, ImmutableSortedMultiset<T>> toSortedMultiset() {
		return Collector.of(() -> ImmutableSortedMultiset.naturalOrder(),
							ImmutableSortedMultiset.Builder::add,
							(left, right) -> left.addAll(right.build()),
							ImmutableSortedMultiset.Builder::build,
							Collector.Characteristics.UNORDERED);
	}

	//TODO: use this
	private static <T> Collector<T, ImmutableSortedMultiset.Builder<T>, ImmutableSortedMultiset<T>> toSortedMultiset(Comparator<? super T> comparator) {
		return Collector.of(() -> new ImmutableSortedMultiset.Builder<>(comparator),
							ImmutableSortedMultiset.Builder::add,
							(left, right) -> left.addAll(right.build()),
							ImmutableSortedMultiset.Builder::build,
							Collector.Characteristics.UNORDERED);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final State other = (State)obj;
		if (!Objects.equals(this.atoms, other.atoms))
			return false;
		if (!Objects.equals(this.bonds, other.bonds))
			return false;
		if (!Objects.equals(this.playerAtom, other.playerAtom))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 59 * hash + Objects.hashCode(this.atoms);
		hash = 59 * hash + Objects.hashCode(this.bonds);
		hash = 59 * hash + Objects.hashCode(this.playerAtom);
		return hash;
	}
}
