package com.jeffreybosboom.sokobondbot;

import com.google.common.util.concurrent.Uninterruptibles;
import com.jeffreybosboom.windows.Windows;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 11/4/2014
 */
public final class Effector {
	public static void main(String[] args) throws Exception {
		Rectangle sokobondRect = Windows.getClientAreaByTitle("Sokobond");
		Robot robot = new Robot();

		//focus on Sokobond window
		robot.mouseMove(sokobondRect.x, sokobondRect.y);
		robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
		robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
		Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);

		List<BufferedImage> images = new ArrayList<>();
		for (int i = 0; i < 10; ++i) {
			images.add(robot.createScreenCapture(sokobondRect));
			Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
		}
		ImageIO.write(images.get(0), "PNG", new File("foo.png"));

		Sensor sensor = new Sensor(images);
		Pair<State, Set<Coordinate>> puzzle = sensor.sense();

		Solver solver = new Solver(puzzle.first, puzzle.second);
		State solution = solver.solve();

		System.out.println(solution.path());
		for (Direction d : solution.path()) {
			int keycode = keycode(d);
			robot.keyPress(keycode);
			robot.keyRelease(keycode);
			Uninterruptibles.sleepUninterruptibly(300, TimeUnit.MILLISECONDS);
		}
	}

	private static int keycode(Direction d) {
		switch (d) {
			case UP: return KeyEvent.VK_UP;
			case DOWN: return KeyEvent.VK_DOWN;
			case LEFT: return KeyEvent.VK_LEFT;
			case RIGHT: return KeyEvent.VK_RIGHT;
		}
		throw new AssertionError();
	}
}
