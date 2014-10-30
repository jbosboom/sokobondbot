package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableSortedMultiset;
import java.util.Comparator;
import java.util.stream.Collector;

/**
 * Some general-purpose utilities that don't really belong in any of the domain
 * classes.
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/30/2014
 */
public final class Utils {
	private Utils() {}

	public static <T extends Comparable<T>> Collector<T, ImmutableSortedMultiset.Builder<T>, ImmutableSortedMultiset<T>> toSortedMultiset() {
		return Collector.of(() -> ImmutableSortedMultiset.naturalOrder(),
							ImmutableSortedMultiset.Builder::add,
							(left, right) -> left.addAll(right.build()),
							ImmutableSortedMultiset.Builder::build,
							Collector.Characteristics.UNORDERED);
	}

	public static <T> Collector<T, ImmutableSortedMultiset.Builder<T>, ImmutableSortedMultiset<T>> toSortedMultiset(Comparator<? super T> comparator) {
		return Collector.of(() -> new ImmutableSortedMultiset.Builder<>(comparator),
							ImmutableSortedMultiset.Builder::add,
							(left, right) -> left.addAll(right.build()),
							ImmutableSortedMultiset.Builder::build,
							Collector.Characteristics.UNORDERED);
	}
}
