package com.jeffreybosboom.sokobondbot;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/29/2014
 */
public final class Coordinate implements Comparable<Coordinate> {
	//fixed bounds to avoid synchronizing when accessing the cache
	private static final int MIN_COORDINATE = -1, MAX_COORDINATE = 10;
	private static final Coordinate[][] INSTANCES = new Coordinate[MAX_COORDINATE-MIN_COORDINATE+1][MAX_COORDINATE-MIN_COORDINATE+1];
	static {
		for (int i = MIN_COORDINATE; i <= MAX_COORDINATE; ++i)
			for (int j = MIN_COORDINATE; j <= MAX_COORDINATE; ++j)
				INSTANCES[i - MIN_COORDINATE][j - MIN_COORDINATE] = new Coordinate(i, j);
	}

	private final int row, col;
	private Coordinate(int row, int col) {
		this.row = row;
		this.col = col;
	}
	public static Coordinate at(int row, int col) {
		return INSTANCES[row - MIN_COORDINATE][col - MIN_COORDINATE];
	}
	public int row() {
		return row;
	}
	public int col() {
		return col;
	}

	public Coordinate up() {
		return at(row - 1, col);
	}
	public Coordinate down() {
		return at(row + 1, col);
	}
	public Coordinate left() {
		return at(row, col - 1);
	}
	public Coordinate right() {
		return at(row, col + 1);
	}

	public Coordinate translate(Direction d) {
		switch (d) {
			case UP: return up();
			case DOWN: return down();
			case LEFT: return left();
			case RIGHT: return right();
		}
		throw new AssertionError();
	}

	public Stream<Coordinate> neighbors() {
		return Stream.of(up(), down(), left(), right());
	}

	private static final Comparator<Coordinate> COMPARATOR =
			Comparator.comparingInt(Coordinate::row)
			.thenComparingInt(Coordinate::col);
	@Override
	public int compareTo(Coordinate o) {
		return COMPARATOR.compare(this, o);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Coordinate other = (Coordinate)obj;
		if (this.row != other.row)
			return false;
		if (this.col != other.col)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 97 * hash + this.row;
		hash = 97 * hash + this.col;
		return hash;
	}

	@Override
	public String toString() {
		return String.format("(%d, %d)", row(), col());
	}
}
