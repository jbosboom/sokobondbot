package com.jeffreybosboom.windows;

import com.jeffreybosboom.windows.Bindings.POINT;
import com.jeffreybosboom.windows.Bindings.RECT;
import java.awt.Rectangle;
import org.bridj.Pointer;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 11/3/2014
 */
public final class Windows {
	private Windows() {}

	public static Rectangle getClientAreaByTitle(String windowTitle) {
		Pointer<Character> title = null;
		Pointer<RECT> rect = null;
		try {
			title = Pointer.pointerToWideCString(windowTitle);
			if (title == null) throw new OutOfMemoryError();
			Pointer<Void> hwnd = Bindings.FindWindow(null, title);
			if (hwnd == null)
				throw new RuntimeException("window not found: "+windowTitle);

			rect = Pointer.allocate(RECT.class);
			if (rect == null) throw new OutOfMemoryError();
			int GetClientRect = Bindings.GetClientRect(hwnd, rect);
			if (GetClientRect == 0) throw new RuntimeException();
			int width = rect.get().right() - rect.get().left();
			int height = rect.get().bottom() - rect.get().top();

			Pointer<POINT> point = rect.as(POINT.class);
			int ClientToScreen = Bindings.ClientToScreen(hwnd, point);
			if (ClientToScreen == 0) throw new RuntimeException();
			return new Rectangle(point.get().x(), point.get().y(), width, height);
		} catch (RuntimeException | Error t) {
			Pointer.release(title, rect);
			throw t;
		}
	}

	public static void main(String[] args) {
		System.out.println(getClientAreaByTitle("SumatraPDF"));
	}
}
