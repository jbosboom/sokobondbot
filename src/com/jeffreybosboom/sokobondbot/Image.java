package com.jeffreybosboom.sokobondbot;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/23/2014
 */
public final class Image {
	//ARGB
	private final int[][] pixels;
	public Image(BufferedImage image) {
		//This could be faster by getting bytes directly, but I don't know how
		//to make that work generically for all images.
		this.pixels = new int[image.getHeight()][image.getWidth()];
		for (int r = 0; r < image.getHeight(); ++r)
			for (int c = 0; c < image.getWidth(); ++c)
				pixels[r][c] = image.getRGB(c, r);
	}
	public Image(int[][] pixels) {
		//not int[]::clone due to https://bugs.openjdk.java.net/browse/JDK-8056051
		this.pixels = Arrays.stream(pixels).map(x -> x.clone()).toArray(int[][]::new);
	}
	public int rows() {
		return pixels.length;
	}
	public int cols() {
		return pixels[0].length;
	}
	public int at(int row, int col) {
		int[] x = pixels[row];
		return x[col];
	}
}
