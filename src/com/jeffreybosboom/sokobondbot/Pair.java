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

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

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
}
