package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableList;
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
import java.util.Comparator;
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
	private int squareSize, intersquareSpace, totalRows, totalCols;
	private Sensor(List<BufferedImage> images) {
		this.images = ImmutableList.copyOf(images.stream().map(Image::new).iterator());
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

		RangeSet<Integer> rowRanges = TreeRangeSet.create();
		boundaries.stream().map(Region::rowSpan).forEachOrdered(rowRanges::add);
		List<Range<Integer>> rows = rowRanges.asRanges().stream().collect(toList());
		RangeSet<Integer> colRanges = TreeRangeSet.create();
		boundaries.stream().map(Region::colSpan).forEachOrdered(colRanges::add);
		List<Range<Integer>> cols = colRanges.asRanges().stream().collect(toList());

		ImmutableSet.Builder<Pair<Integer, Integer>> boundaryBuilder = ImmutableSet.builder();
		for (Region square : boundaries) {
			Region.Point c = square.centroid();
			int row = rows.indexOf(rowRanges.rangeContaining(c.row()));
			int col = cols.indexOf(colRanges.rangeContaining(c.col()));
			boundaryBuilder.add(new Pair<>(row, col));
		}
		this.boundary = boundaryBuilder.build();
		this.totalRows = rows.size();
		this.totalCols = cols.size();

		int puzzleHeight = rows.get(rows.size()-1).upperEndpoint() - rows.get(0).lowerEndpoint() + 1;
		int interrowSpace = IntMath.divide(puzzleHeight - totalRows * squareSize, totalRows - 1, RoundingMode.UNNECESSARY);
		int puzzleWidth = cols.get(cols.size()-1).upperEndpoint() - cols.get(0).lowerEndpoint() + 1;
		int intercolSpace = IntMath.divide(puzzleWidth - totalCols * squareSize, totalCols - 1, RoundingMode.UNNECESSARY);
		if (interrowSpace != intercolSpace)
			throw new RuntimeException(interrowSpace + " " + intercolSpace);
		this.intersquareSpace = interrowSpace;
		System.out.println(intersquareSpace);
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
		new Sensor(ImmutableList.of(image)).determineBoundary();
	}
}
