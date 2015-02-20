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
	private final ImmutableSortedSet<Coordinate> boundary;
	private final State initialState;
	public Solver(State initialState, Set<Coordinate> boundary) {
		this.boundary = ImmutableSortedSet.copyOf(boundary);
		this.initialState = initialState;
	}

	public State solve() {
		ConcurrentHashMap.KeySetView<Object, Boolean> closedSet = ConcurrentHashMap.newKeySet();
		Optional<State> solution = new ParallelBFS<State>(s -> s.nextStates(boundary).stream(), State::isSolved)
				.filter(s -> closedSet.add(s.pack()))
				.find(initialState);
		System.out.println(closedSet.size()+" states in closed set");
		if (solution.isPresent()) return solution.get();
		throw new AssertionError("search ended with no solution?!");
	}

	public static void main(String[] args) throws Exception {
		List<BufferedImage> imgs = new ArrayList<>();
		for (int i = 1; i < 10; ++i)
			imgs.add(ImageIO.read(new File("C:\\Users\\jbosboom\\Pictures\\Steam Unsorted\\290260_2014-10-23_0000"+i+".png")));
		Pair<State, Set<Coordinate>> sensation = new Sensor(imgs).sense();
		State solution = new Solver(sensation.first, sensation.second).solve();
		System.out.println(solution);
		System.out.println(solution.path());
	}
}
