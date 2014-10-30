package com.jeffreybosboom.sokobondbot;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/29/2014
 */
public enum Direction {
	UP(Coordinate::col, Collectors::minBy, Coordinate::row),
	DOWN(Coordinate::col, Collectors::maxBy, Coordinate::row),
	LEFT(Coordinate::row, Collectors::minBy, Coordinate::col),
	RIGHT(Coordinate::row, Collectors::maxBy, Coordinate::col);
	private final Collector<Coordinate, ?, Collection<Coordinate>> projector;
	private Direction(Function<Coordinate, Integer> classifier,
			Function<Comparator<Coordinate>, Collector<Coordinate, ?, Optional<Coordinate>>> minOrMaxBy,
			ToIntFunction<Coordinate> comparisonKeyExtractor) {
		this.projector = collectingAndThen(
			groupingBy(
					classifier,
					collectingAndThen(
							minOrMaxBy.apply(Comparator.comparingInt(comparisonKeyExtractor)),
							Optional::get)),
			Map::values);
	}

	public Coordinate translate(Coordinate c) {
		return c.translate(this);
	}

	/**
	 * Returns the top/bottom/left/rightmost atoms in the given molecule, which
	 * should be checked for obstacles during translation.
	 * @param molecule
	 * @return
	 */
	public Collection<Coordinate> project(Set<Coordinate> molecule) {
		return molecule.stream().collect(projector);
	}
}
