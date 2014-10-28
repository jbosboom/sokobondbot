package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.math.IntMath;
import com.jeffreybosboom.region.Region;
import com.jeffreybosboom.sokobondbot.State.Element;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/23/2014
 */
public final class Sensor {
	private final ImmutableList<Image> images;
	private ImmutableSet<Pair<Integer, Integer>> boundary;
	private ImmutableRangeSet<Integer> rowRanges, colRanges;
	private int squareSize, intersquareSpace, totalRows, totalCols;
	private Sensor(List<BufferedImage> images) {
		this.images = ImmutableList.copyOf(images.stream().map(Image::new).iterator());
	}

	public State sense() {
		determineBoundary();
		determinePlayfield();
		return null;
	}

	private static final Set<Integer> BOUNDARY_COLORS = Stream.of(
			0xFFFFD452
	).collect(toSet());
	private void determineBoundary() {
		Set<Region> boundaries = Region.connectedComponents(images.get(0), BOUNDARY_COLORS).stream()
				.filter(r -> r.points().size() > 25)
				.filter(r -> r.boundingBox().width == r.boundingBox().height)
				.collect(toSet());

		Rectangle prototype = boundaries.iterator().next().boundingBox();
		assert !boundaries.stream().filter(r -> r.boundingBox().width != prototype.width).findAny().isPresent() : "squares not all same size";
		this.squareSize = prototype.width + 1;
		ImmutableRangeSet.Builder<Integer> rowRangesBuilder = ImmutableRangeSet.builder();
		boundaries.stream().map(Region::rowSpan).distinct().forEachOrdered(rowRangesBuilder::add);
		this.rowRanges = rowRangesBuilder.build();
		ImmutableRangeSet.Builder<Integer> colRangesBuilder = ImmutableRangeSet.builder();
		boundaries.stream().map(Region::colSpan).distinct().forEachOrdered(colRangesBuilder::add);
		this.colRanges = colRangesBuilder.build();

		ImmutableSet.Builder<Pair<Integer, Integer>> boundaryBuilder = ImmutableSet.builder();
		boundaries.stream().map(Region::centroid)
				.map(this::pointToGrid)
				.forEachOrdered(boundaryBuilder::add);
		this.boundary = boundaryBuilder.build();
		this.totalRows = rowRanges.asRanges().size();
		this.totalCols = colRanges.asRanges().size();

		List<Range<Integer>> rows = rowRanges.asRanges().asList();
		List<Range<Integer>> cols = colRanges.asRanges().asList();
		int puzzleHeight = rows.get(rows.size()-1).upperEndpoint() - rows.get(0).lowerEndpoint() + 1;
		int interrowSpace = IntMath.divide(puzzleHeight - totalRows * squareSize, totalRows - 1, RoundingMode.UNNECESSARY);
		int puzzleWidth = cols.get(cols.size()-1).upperEndpoint() - cols.get(0).lowerEndpoint() + 1;
		int intercolSpace = IntMath.divide(puzzleWidth - totalCols * squareSize, totalCols - 1, RoundingMode.UNNECESSARY);
		if (interrowSpace != intercolSpace)
			throw new RuntimeException(interrowSpace + " " + intercolSpace);
		this.intersquareSpace = interrowSpace;
		System.out.println(intersquareSpace);
	}

	private static final int[][] NEIGHBORHOOD = {
		{-1, 0}, {1, 0}, {0, -1}, {0, 1}
	};
	private void determinePlayfield() {
		//Scan across row 1 for the first cell not in the boundary that's to the
		//right of a boundary cell.
		Set<Pair<Integer, Integer>> firstRow = boundary.stream().filter(p -> p.first == 1).collect(toSet());
		Pair<Integer, Integer> root = firstRow.stream()
				.map(p -> p.map(Function.identity(), c -> c + 1))
				.filter(p -> !firstRow.contains(p))
				.sorted(Comparator.comparingInt(Pair::second))
				.findAny().get();
		System.out.println(root);

		//Flood fill to find the playfield squares.
		Set<Pair<Integer, Integer>> playfield = new HashSet<>();
		Deque<Pair<Integer, Integer>> frontier = new ArrayDeque<>();
		playfield.add(root);
		frontier.push(root);
		while (!frontier.isEmpty()) {
			Pair<Integer, Integer> p = frontier.pop();
			for (int[] n : NEIGHBORHOOD) {
				Pair<Integer, Integer> q = new Pair<>(p.first() + n[0], p.second() + n[1]);
				if (!boundary.contains(q) && playfield.add(q))
					frontier.push(q);
			}
		}
		System.out.println(playfield.size());
	}

	private int pixelToRow(int rowPixel) {
		return rowRanges.asRanges().asList().indexOf(rowRanges.rangeContaining(rowPixel));
	}
	private int pixelToCol(int colPixel) {
		return colRanges.asRanges().asList().indexOf(colRanges.rangeContaining(colPixel));
	}
	private Pair<Integer, Integer> pointToGrid(Region.Point p) {
		return new Pair<>(pixelToRow(p.row()), pixelToCol(p.col()));
	}

	private static final int SQUARE_GRAY = 0xFFF0F0F0;
	private static final int BLACK = 0xFF000000;
	private void gridSquares() {
		//Gray if gray in any image, else black.
		int[][] p = new int[images.get(0).rows()][images.get(0).cols()];
		for (int r = 0; r < images.get(0).rows(); ++r)
			for (int c = 0; c < images.get(0).cols(); ++c) {
				int r_ = r, c_ = c;
				p[r][c] = (images.stream().mapToInt(i -> i.at(r_, c_)).anyMatch(i -> i == SQUARE_GRAY)) ?
						SQUARE_GRAY : BLACK;
			}
		Set<Region> squares = Region.connectedComponents(new Image(p), SQUARE_GRAY).stream()
				.filter(r -> r.points().size() > 5)
				.collect(toSet());

		RangeSet<Integer> rowRanges = TreeRangeSet.create();
		squares.stream().map(Region::rowSpan).forEachOrdered(rowRanges::add);
		List<Range<Integer>> rows = rowRanges.asRanges().stream().collect(toList());
		RangeSet<Integer> colRanges = TreeRangeSet.create();
		squares.stream().map(Region::colSpan).forEachOrdered(colRanges::add);
		List<Range<Integer>> cols = colRanges.asRanges().stream().collect(toList());

		for (Region square : squares) {
			Region.Point c = square.centroid();
			Rectangle b = square.boundingBox();
			int row = rows.indexOf(rowRanges.rangeContaining(c.row()));
			int col = cols.indexOf(colRanges.rangeContaining(c.col()));
			System.out.println(row +" "+col);

		}
	}

	/**
	 * Returns the element of the atom in the given square, or null if the
	 * square is empty.
	 * @param square a grid square
	 * @return the element of the atom in the given square, or null if empty
	 */
	private static Element recognizeElement(Image square) {
		Map<Integer, Long> histogram = square.pixels().boxed()
				.filter(p -> Element.fromColor(p) != null)
				.collect(groupingBy(Function.identity(), counting()));
		Integer mostFrequentElementColor = histogram.entrySet().stream()
				.max(Comparator.comparing(Entry::getValue))
				.map(Entry::getKey)
				.orElse(SQUARE_GRAY);
		return Element.fromColor(mostFrequentElementColor);
	}

	public static void main(String[] args) throws IOException {
		BufferedImage image = ImageIO.read(new File("C:\\Users\\jbosboom\\Pictures\\Steam Unsorted\\290260_2014-10-23_00001.png"));
		new Sensor(ImmutableList.of(image)).sense();
	}
}
