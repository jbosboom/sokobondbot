package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableSortedSet;
import com.jeffreybosboom.parallelbfs.ParallelBFS;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/30/2014
 */
public final class Solver {
	private final Puzzle puzzle;
	public Solver(Puzzle puzzle) {
		this.puzzle = puzzle;
	}

	public State solve() {
		//capture the boundary in the lambda, not the puzzle
		final ImmutableSortedSet<Coordinate> boundary = puzzle.boundary();
		ConcurrentHashMap.KeySetView<Object, Boolean> closedSet = ConcurrentHashMap.newKeySet();
		Optional<State> solution = new ParallelBFS<State>(s -> s.nextStates(boundary).stream(), State::isSolved)
				.filter(s -> closedSet.add(s.pack()))
				.find(new State(puzzle));
		System.out.println(closedSet.size()+" states in closed set");
		if (solution.isPresent()) return solution.get();
		throw new AssertionError("search ended with no solution?!");
	}

	public static void main(String[] args) throws Exception {
		List<BufferedImage> imgs = new ArrayList<>();
		for (int i = 1; i < 10; ++i)
			imgs.add(ImageIO.read(new File("C:\\Users\\jbosboom\\Pictures\\Steam Unsorted\\290260_2014-10-23_0000"+i+".png")));
		Puzzle sensation = new Sensor(imgs).sense();
		State solution = new Solver(sensation).solve();
		System.out.println(solution);
		System.out.println(solution.path());
	}
}
