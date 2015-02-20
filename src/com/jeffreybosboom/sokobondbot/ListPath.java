package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 2/18/2015
 */
public final class ListPath implements Path {
	private final ImmutableList<Direction> foo;
	private ListPath(ImmutableList<Direction> foo) {
		this.foo = foo;
	}

	public static ListPath empty() {
		return new ListPath(ImmutableList.of());
	}

	@Override
	public ListPath append(Direction d) {
		return new ListPath(ImmutableList.<Direction>builder().addAll(foo).add(d).build());
	}

	@Override
	public Iterator<Direction> iterator() {
		return foo.iterator();
	}

	@Override
	public String toString() {
		return StreamSupport.stream(spliterator(), false)
				.map(Direction::toString)
				.collect(Collectors.joining(", "));
	}
}
