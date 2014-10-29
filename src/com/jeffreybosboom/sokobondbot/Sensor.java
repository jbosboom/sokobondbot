package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import static java.util.function.Function.identity;
import java.util.stream.Collector;
import static java.util.stream.Collectors.collectingAndThen;
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
	private ImmutableSortedSet<Pair<Integer, Integer>> playfield;
	private Sensor(List<BufferedImage> images) {
		this.images = ImmutableList.copyOf(images.stream().map(Image::new).iterator());
	}

	public State sense() {
		determineBoundary();
		determinePlayfield();
		constructMolecules();
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
				.map(p -> p.map(identity(), c -> c + 1))
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
		this.playfield = ImmutableSortedSet.copyOf(Pair.comparator(), playfield);
	}

	private void constructMolecules() {
		Map<Pair<Integer, Integer>, Element> elementMap = new TreeMap<>(Pair.comparator());
		Map<Pair<Integer, Integer>, Integer> freeElectronsMap = new TreeMap<>(Pair.comparator());
		List<Pair<Integer, Integer>> playerControlledList = new ArrayList<>();
		for (Pair<Integer, Integer> p : playfield) {
			Rectangle r = gridToPixels(p);
			List<Image> square = subimages(r).collect(toList());
			List<Element> possibleElements = square.stream()
					.map(Sensor::recognizeElement)
					.collect(toList());
			//should always agree on the element
			if (new HashSet<>(possibleElements).size() != 1)
				throw new RuntimeException(p+" "+possibleElements);
			Element element = possibleElements.get(0);
			if (element == null) continue;
			elementMap.put(p, element);

			int freeElectrons = square.stream()
					.map(Sensor::recognizeElectrons)
					.collect(mostCommon(1));
			if (freeElectrons > element.maxElectrons())
				throw new RuntimeException(p+" "+element+" "+freeElectrons);
			freeElectronsMap.put(p, freeElectrons);

			boolean isPlayerControlled = square.stream()
					.map(Sensor::isPlayerControlled)
					.collect(mostCommon(1));
			if (isPlayerControlled) playerControlledList.add(p);
			System.out.println(p+" "+ element+" "+freeElectrons+" "+isPlayerControlled);
		}
		if (playerControlledList.size() != 1)
			throw new RuntimeException("player-controlled: "+playerControlledList);
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
	private Rectangle gridToPixels(Pair<Integer, Integer> g) {
		int r = rowRanges.asRanges().asList().get(g.first).lowerEndpoint(),
				c = colRanges.asRanges().asList().get(g.second).lowerEndpoint();
		return new Rectangle(c, r, squareSize, squareSize);
	}

	private Stream<Image> subimages(Rectangle rect) {
		return subimages(rect.y, rect.x, rect.height, rect.width);
	}
	private Stream<Image> subimages(int row, int col, int rows, int cols) {
		return images.stream().map(i -> i.subimage(row, col, rows, cols));
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
				.collect(groupingBy(identity(), counting()));
		Integer mostFrequentElementColor = histogram.entrySet().stream()
				.max(Comparator.comparing(Entry::getValue))
				.map(Entry::getKey)
				.orElse(SQUARE_GRAY);
		return Element.fromColor(mostFrequentElementColor);
	}

	private static final int WHITE = 0xFFFFFFFF;
	private static int recognizeElectrons(Image square) {
		return Region.connectedComponents(square, WHITE).size();
	}

	private static boolean isPlayerControlled(Image square) {
		//normal atoms have one black region; player-controlled atoms have 8,
		//modulo free electrons or bonds joining the regions.
		return Region.connectedComponents(square).stream()
				.filter(r -> r.color() == BLACK)
				.count() >= 3;
	}

	/**
	 * Returns a collector that returns the most common element, provided it
	 * makes up all but {@code tolerance} values in the stream, and throws an
	 * exception otherwise.
	 * @param <T>
	 * @param tolerance
	 * @return
	 */
	private static <T> Collector<T, ?, T> mostCommon(int tolerance) {
		return collectingAndThen(groupingBy(identity(), counting()),
				(Map<T, Long> histogram) -> {
					T mostCommon = histogram.entrySet().stream()
							.max(Comparator.comparing(Entry::getValue))
							.map(Entry::getKey)
							.orElseThrow(() -> new RuntimeException("empty histogram"));
					long others = histogram.entrySet().stream()
							.filter(e -> e.getKey() != mostCommon)
							.mapToLong(Entry::getValue)
							.sum();
					if (others > tolerance)
						throw new RuntimeException(String.format("tolerance %s exceeded: %s", tolerance, histogram));
					return mostCommon;
				}
		);
	}

	public static void main(String[] args) throws IOException {
		List<BufferedImage> imgs = new ArrayList<>();
		for (int i = 1; i < 10; ++i)
			imgs.add(ImageIO.read(new File("C:\\Users\\jbosboom\\Pictures\\Steam Unsorted\\290260_2014-10-23_0000"+i+".png")));
		new Sensor(imgs).sense();
	}
}
