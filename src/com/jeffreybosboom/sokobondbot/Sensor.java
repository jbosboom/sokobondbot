package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.jeffreybosboom.region.Region;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/23/2014
 */
public final class Sensor {
	private final ImmutableList<Image> images;
	private Sensor(List<BufferedImage> images) {
		this.images = ImmutableList.copyOf(images.stream().map(Image::new).iterator());
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

	public static void main(String[] args) throws IOException {
		BufferedImage image = ImageIO.read(new File("C:\\Users\\jbosboom\\Pictures\\Steam Unsorted\\290260_2014-10-23_00001.png"));
		new Sensor(ImmutableList.of(image)).gridSquares();
	}
}
