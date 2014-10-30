package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multiset;
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
import static java.util.function.Function.identity;
import static java.util.function.Predicate.isEqual;
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
	private static final int WHITE = 0xFFFFFFFF;
	private static final int SQUARE_GRAY = 0xFFF0F0F0;
	private static final int BLACK = 0xFF000000;
	private static final Set<Integer> BOUNDARY_COLORS = Stream.of(
			0xFFFFD452
	).collect(toSet());

	private final ImmutableList<Image> images;
	private ImmutableSortedSet<Coordinate> boundary;
	private ImmutableRangeSet<Integer> rowRanges, colRanges;
	private int squareSize, intersquareSpace, totalRows, totalCols;
	private ImmutableSortedSet<Coordinate> playfield;
	private State initialState;
	public Sensor(List<BufferedImage> images) {
		this.images = ImmutableList.copyOf(images.stream().map(Image::new).iterator());
	}

	/**
	 * Parses the images to determine the initial state and the boundary cells
	 * (which are not stored in the state as they don't change between states).
	 * @return the initial state and boundary cells
	 */
	public Pair<State, Set<Coordinate>> sense() {
		determineBoundary();
		determinePlayfield();
		constructMolecules();
		return new Pair<>(initialState, boundary);
	}

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

		ImmutableSortedSet.Builder<Coordinate> boundaryBuilder = ImmutableSortedSet.naturalOrder();
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

	private void determinePlayfield() {
		//Scan across row 1 for the first cell not in the boundary that's to the
		//right of a boundary cell.
		Set<Coordinate> firstRow = boundary.stream().filter(p -> p.row() == 1).collect(toSet());
		Coordinate root = firstRow.stream()
				.map(Coordinate::right)
				.filter(p -> !firstRow.contains(p))
				.sorted()
				.findAny().get();
		System.out.println(root);

		//Flood fill to find the playfield squares.
		Set<Coordinate> playfield = new HashSet<>();
		Deque<Coordinate> frontier = new ArrayDeque<>();
		playfield.add(root);
		frontier.push(root);
		while (!frontier.isEmpty())
			frontier.pop().neighbors().filter(c -> !boundary.contains(c))
					.filter(playfield::add) //side-effectful!
					.forEachOrdered(frontier::push);
		this.playfield = ImmutableSortedSet.copyOf(playfield);
	}

	private void constructMolecules() {
		Map<Coordinate, Element> elementMap = new TreeMap<>();
		Map<Coordinate, Integer> freeElectronsMap = new TreeMap<>();
		List<Coordinate> playerControlledList = new ArrayList<>();
		for (Coordinate p : playfield) {
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

		Multiset<Pair<Coordinate, Coordinate>> bondsSet = HashMultiset.create();
		for (Coordinate occupied : elementMap.keySet())
			occupied.neighbors().forEachOrdered(n -> {
				int bonds = subimages(intersquarePixels(occupied, n))
						.map(Sensor::recognizeBonds)
						.collect(mostCommon(2));
				bondsSet.add(Pair.sorted(occupied, n), bonds);
			});

		this.initialState = new State(elementMap, bondsSet, playerControlledList.get(0));
	}

	private int pixelToRow(int rowPixel) {
		return rowRanges.asRanges().asList().indexOf(rowRanges.rangeContaining(rowPixel));
	}
	private int pixelToCol(int colPixel) {
		return colRanges.asRanges().asList().indexOf(colRanges.rangeContaining(colPixel));
	}
	private Coordinate pointToGrid(Region.Point p) {
		return Coordinate.at(pixelToRow(p.row()), pixelToCol(p.col()));
	}
	private Rectangle gridToPixels(Coordinate g) {
		int r = rowRanges.asRanges().asList().get(g.row()).lowerEndpoint(),
				c = colRanges.asRanges().asList().get(g.col()).lowerEndpoint();
		return new Rectangle(c, r, squareSize, squareSize);
	}
	private Rectangle intersquarePixels(Coordinate a, Coordinate b) {
		if (a.compareTo(b) > 0)
			return intersquarePixels(b, a);
		assert a.neighbors().filter(isEqual(b)).count() == 1;

		if (a.row() == 0) { //same row
			int firstRow = rowRanges.asRanges().asList().get(a.row()).lowerEndpoint();
			int firstCol = colRanges.asRanges().asList().get(Math.min(a.col(), b.col())).lowerEndpoint() + squareSize;
			return new Rectangle(firstCol, firstRow, intersquareSpace, squareSize);
		} else { //same col
			int firstCol = colRanges.asRanges().asList().get(a.col()).lowerEndpoint();
			int firstRow = rowRanges.asRanges().asList().get(Math.min(a.row(), b.row())).lowerEndpoint() + squareSize;
			return new Rectangle(firstCol, firstRow, squareSize, intersquareSpace);
		}
	}

	private Stream<Image> subimages(Rectangle rect) {
		return subimages(rect.y, rect.x, rect.height, rect.width);
	}
	private Stream<Image> subimages(int row, int col, int rows, int cols) {
		return images.stream().map(i -> i.subimage(row, col, rows, cols));
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

	private static int recognizeBonds(Image square) {
		return (int)Region.connectedComponents(square).stream()
				.filter(r -> r.color() == BLACK)
				.count();
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
