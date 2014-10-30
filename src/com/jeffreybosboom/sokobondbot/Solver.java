package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.ImmutableSortedSet;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/30/2014
 */
public final class Solver {
	private final ImmutableSortedSet<Coordinate> boundary;
	//states we've visited or are already in frontier
	private final Set<State> closed = new HashSet<>();
	private final Queue<State> frontier = new ArrayDeque<>();
	public Solver(State initialState, Set<Coordinate> boundary) {
		this.boundary = ImmutableSortedSet.copyOf(boundary);
		this.closed.add(initialState);
		this.frontier.add(initialState);
	}

	public State solve() {
		//Breadth-first search.
		while (!frontier.isEmpty()) {
			List<State> next = frontier.remove().nextStates(boundary);
			next.removeIf(closed::contains);
			for (State s : next) {
				if (s.isSolved())
					return s;
				frontier.addAll(next);
				closed.addAll(next);
			}
		}
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
