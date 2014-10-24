/*
 * Copyright 2014 Jeffrey Bosboom.
 * This file is part of lynebot.
 *
 * lynebot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * lynebot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with lynebot.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jeffreybosboom.region;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.jeffreybosboom.sokobondbot.Image;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 8/16/2014
 */
public final class Region {
	/**
	 * as from BufferedImage.getRGB
	 */
	private final int color;
	private final ImmutableList<Point> points;
	private final IntSummaryStatistics xStats, yStats;
	private Region(int color, List<Point> points) {
		this.color = color;
		this.points = ImmutableList.copyOf(points);
		//TODO: lazy initialize?
		//TODO: compute in one pass?
		this.xStats = this.points.stream().mapToInt(Point::x).summaryStatistics();
		this.yStats = this.points.stream().mapToInt(Point::y).summaryStatistics();
	}

	private static final int[][] NEIGHBORHOOD = {
		{-1, -1}, {-1, 0}, {-1, 1},
		{0, -1}, {0, 1},
		{1, -1}, {1, 0}, {1, 1},
	};
	public static ImmutableSet<Region> connectedComponents(Image image) {
		return connectedComponents(image, ContiguousSet.create(Range.all(), DiscreteDomain.integers()));
	}
	public static ImmutableSet<Region> connectedComponents(Image image, int... interestingColors) {
		return connectedComponents(image, Arrays.stream(interestingColors).boxed().collect(Collectors.toSet()));
	}
	public static ImmutableSet<Region> connectedComponents(Image image, Set<Integer> interestingColors) {
		final int imageSize = image.cols() * image.rows();
		BitSet processed = new BitSet(imageSize);
		for (int r = 0; r < image.rows(); ++r)
			for (int c = 0; c < image.cols(); ++c)
				if (!interestingColors.contains(image.at(r, c)))
					processed.set(c * image.rows() + r);

		ImmutableSet.Builder<Region> builder = ImmutableSet.builder();
		int lastClearBit = 0;
		while ((lastClearBit = processed.nextClearBit(lastClearBit)) != imageSize) {
			int fillCol = lastClearBit / image.rows(), fillRow = lastClearBit % image.rows();
			int color = image.at(fillRow, fillCol);
			List<Point> points = new ArrayList<>();

			//flood fill
			Deque<Point> frontier = new ArrayDeque<>();
			frontier.push(new Point(fillRow, fillCol));
			while (!frontier.isEmpty()) {
				Point p = frontier.pop();
				int bitIndex = p.row() * image.rows() + p.col();
				if (processed.get(bitIndex)) continue;
				if (image.at(p.x, p.y) != color) continue;

				points.add(p);
				processed.set(bitIndex);
				for (int[] n : NEIGHBORHOOD) {
					int nx = p.x + n[0], ny = p.y + n[1];
					int nBitIndex = nx + ny * image.rows();
					if (0 <= nx && nx < image.rows() && 0 <= ny && ny < image.cols()
							&& !processed.get(nBitIndex))
						frontier.push(new Point(nx, ny));
				}
			}
			assert !points.isEmpty();
			builder.add(new Region(color, points));
		}
		return builder.build();
	}

	public int color() {
		return color;
	}

	public ImmutableList<Point> points() {
		return points;
	}

	public Point centroid() {
		return new Point((int)xStats.getAverage(), (int)yStats.getAverage());
	}

	public Rectangle boundingBox() {
		return new Rectangle(xStats.getMin(), yStats.getMin(),
				xStats.getMax() - xStats.getMin(), yStats.getMax() - yStats.getMin());
	}

	public Range<Integer> rowSpan() {
		Rectangle boundingBox = boundingBox();
		return Range.closedOpen(boundingBox.y, boundingBox.y + boundingBox.height);
	}

	public Range<Integer> colSpan() {
		Rectangle boundingBox = boundingBox();
		return Range.closedOpen(boundingBox.x, boundingBox.x + boundingBox.width);
	}

	public static final class Point {
		public final int x, y;
		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
		//these are mostly for method references (field refs don't exist)
		public int x() {
			return x;
		}
		public int y() {
			return y;
		}
		//row/col is reversed, of course
		public int row() {
			return y;
		}
		public int col() {
			return x;
		}
		@Override
		public boolean equals(Object obj) {
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final Point other = (Point)obj;
			if (this.x != other.x)
				return false;
			if (this.y != other.y)
				return false;
			return true;
		}
		@Override
		public int hashCode() {
			int hash = 3;
			hash = 13 * hash + this.x;
			hash = 13 * hash + this.y;
			return hash;
		}
		@Override
		public String toString() {
			return String.format("(%d, %d)", x, y);
		}
	}
}
