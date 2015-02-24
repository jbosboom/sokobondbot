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

	public Path solve() {
		ConcurrentHashMap.KeySetView<Object, Boolean> closedSet = ConcurrentHashMap.newKeySet();

//		//capture the boundary in the lambda, not the puzzle
//		final ImmutableSortedSet<Coordinate> boundary = puzzle.boundary();
//		Optional<State> solution = new ParallelBFS<State>(s -> s.nextStates(boundary).stream(), State::isSolved)
//				.filter(s -> closedSet.add(s.pack()))
//				.find(new State(puzzle));
//		System.out.println(closedSet.size()+" states in closed set");
//		if (solution.isPresent()) return solution.get().path();
//		throw new AssertionError("search ended with no solution?!");

		boolean[] boundary = StateUnboxed.encodeBoundary(puzzle.boundary());
		int totalFreeElectrons = puzzle.atoms().values().stream().mapToInt(Element::maxElectrons).sum();
		Optional<StateUnboxed> solution = new ParallelBFS<>(s -> s.nextStates(boundary), StateUnboxed::isSolved)
				.filter(s -> closedSet.add(s.pack(totalFreeElectrons)))
				.find(new StateUnboxed(puzzle));
		System.out.println(closedSet.size()+" states in closed set");
		if (solution.isPresent()) return solution.get().path();
		throw new AssertionError("search ended with no solution?!");
	}

	public static void main(String[] args) throws Exception {
		List<BufferedImage> imgs = new ArrayList<>();
		for (int i = 1; i < 10; ++i)
			imgs.add(ImageIO.read(new File("C:\\Users\\jbosboom\\Pictures\\Steam Unsorted\\290260_2015-02-19_0000"+i+".png")));
		Puzzle sensation = new Sensor(imgs).sense();
		Path solution = new Solver(sensation).solve();
		System.out.println(solution);
	}
}
