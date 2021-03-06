/*
 * Copyright 2014 Jeffrey Bosboom.
 * This file is part of sokobondbot.
 *
 * sokobondbot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sokobondbot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with sokobondbot.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jeffreybosboom.sokobondbot;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 8/16/2014
 */
public final class Pair<A, B> {
	public final A first;
	public final B second;
	public Pair(A first, B second) {
		this.first = first;
		this.second = second;
	}

	public static <T extends Comparable<T>> Pair<T, T> sorted(T a, T b) {
		if (a.compareTo(b) > 0)
			return new Pair<>(b, a);
		return new Pair<>(a, b);
	}

	public A first() {
		return first;
	}

	public B second() {
		return second;
	}

	public Pair<B, A> opposite() {
		return new Pair<>(second, first);
	}

	public <A1, B1> Pair<A1, B1> map(BiFunction<? super A, ? super B, Pair<A1, B1>> mapper) {
		return mapper.apply(first, second);
	}

	public <A1, B1> Pair<A1, B1> map(Function<? super A, A1> firstMapper, Function<? super B, B1> secondMapper) {
		return map((f, s) -> new Pair<>(firstMapper.apply(f), secondMapper.apply(s)));
	}

	//Best we can do; see https://stackoverflow.com/questions/10809234/substitute-for-illegal-lower-bounds-on-a-generic-java-method
	private <T> Stream<T> stream(Class<T> klass) {
		return Stream.of(klass.cast(first), klass.cast(second));
	}

	@SuppressWarnings("unchecked")
	private <T> Stream<T> streamUnchecked() {
		return (Stream<T>)Stream.of(first, second);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Pair<?, ?> other = (Pair<?, ?>)obj;
		if (!Objects.equals(this.first, other.first))
			return false;
		if (!Objects.equals(this.second, other.second))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.first);
		hash = 67 * hash + Objects.hashCode(this.second);
		return hash;
	}

	@Override
	public String toString() {
		return String.format("(%s, %s)", first, second);
	}

	public static <A extends Comparable<? super A>, B extends Comparable<? super B>> Comparator<Pair<A, B>> comparator() {
		return Comparator.comparing((Pair<A, B> p) -> p.first)
				.thenComparing((Pair<A, B> p) -> p.second);
	}
}
