package com.jeffreybosboom.sokobondbot;

import com.jeffreybosboom.parallelbfs.DataContainer;
import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 2/22/2015
 */
public final class StateUnboxed {
	private static final int BONDS_MASK =	0b00000000_00000000_11111111_11111111;
	private static final int ROW_MASK =		0b00000000_11110000_00000000_00000000;
	private static final int COL_MASK =		0b00000000_00001111_00000000_00000000;
	private static final int ELECTRON_MASK =0b00000111_00000000_00000000_00000000;
	private static final int ELEMENT_MASK =	0b00111000_00000000_00000000_00000000;
	private static final int COORD_MASK = ROW_MASK | COL_MASK;
	private static final int PLAYER_ATOM = 0;
	private final int[] atoms;
	private final Path path;
	public StateUnboxed(Puzzle puzzle) {
		this.path = TwoLongPath.empty();
		this.atoms = new int[puzzle.atoms().size()];
		assert puzzle.bonds().isEmpty() : "TODO initialize bonds";
		atoms[PLAYER_ATOM] = pack(puzzle.atoms().get(puzzle.playerAtom())) |
				packFE(puzzle.atoms().get(puzzle.playerAtom()).maxElectrons()) |
				pack(puzzle.playerAtom());
		int i = 1;
		for (Map.Entry<Coordinate, Element> atom : puzzle.atoms().entrySet()) {
			if (atom.getKey().equals(puzzle.playerAtom())) continue;
			atoms[i++] = pack(atom.getValue()) |
					packFE(atom.getValue().maxElectrons()) |
					pack(atom.getKey());
		}
	}

	private StateUnboxed(int[] atoms, Path path) {
		this.atoms = atoms;
		this.path = path;
	}

	public boolean isSolved() {
		//TODO: & everything and ELECTRON_MASK (avoids branch)
		for (int i = 0; i < atoms.length; ++i)
			if (freeElectrons(atoms, i) != 0)
				return false;
		return true;
	}

	public Path path() {
		return path;
	}

	public Stream<StateUnboxed> nextStates(boolean[] boundary) {
		List<StateUnboxed> nextStates = new ArrayList<>(4);
		char molecule = molecule(atoms, 0);
		fail: for (Direction dir : Direction.values()) {
			int[] newAtoms = atoms.clone();
			int movedAtoms = tryMove(newAtoms, molecule, dir.ordinal(), boundary);
			if (movedAtoms == -1)
				continue fail; //TODO: try to reuse the newly-cloned array

			//if an atom had free electrons before moving, it should bond with
			//every (newly-)adjacent atom that also had electrons and isn't
			//already bonded with.  if we run out of electrons, that's nondeterminism.
			while (movedAtoms != 0) {
				int movedBit = lowestOneBit(movedAtoms);
				int movedIdx = numberOfTrailingZeros(movedAtoms);
				movedAtoms &= ~movedBit;

				if (freeElectrons(atoms, movedIdx) == 0) continue;
				int row = row(newAtoms, movedIdx), col = col(newAtoms, movedIdx);
				for (int i = 0; i < newAtoms.length; ++i) {
					if (freeElectrons(atoms, i) == 0) continue;
					if (bonded(atoms, movedIdx, i)) continue;
					//adjacent iff one of row/col is equal and the other has
					//absolute difference 1.  (implies never adjacent to self)
					int irow = row(newAtoms, i), icol = col(newAtoms, i);
					if ((row == irow && absdiff(col, icol) == 1) ||
							(col == icol && absdiff(row, irow) == 1)) {
						if (!bond(newAtoms, movedIdx, i))
							continue fail; //nondeterminism
					}
				}
			}

			nextStates.add(new StateUnboxed(newAtoms, path.append(dir)));
		}
		return nextStates.stream();
	}

	//returns a bitfield of atoms in this molecule
	private static char molecule(int[] atoms, int atom) {
		//TODO: consider just looping to fixpoint instead of maintaining frontier
		int molecule = (1 << atom);
		int frontier = molecule;
		while (frontier != 0) {
			int bit = lowestOneBit(frontier);
			frontier &= ~bit;
			int cur = numberOfTrailingZeros(bit);
			int bonds = bonds(atoms, cur);
			int notAlreadyInMolecule = bonds & ~molecule;
			frontier |= notAlreadyInMolecule;
			molecule |= bonds;
		}
		assert numberOfTrailingZeros(Integer.highestOneBit(molecule)) < 16 : Integer.toBinaryString(molecule);
		return (char)molecule;
	}

	private static final int[][] COORD_ADD = {
		{-1, 0}, {1, 0}, {0, -1}, {0, 1}
	};
	private static int tryMove(int[] newAtoms, char molecule, int dirOrdinal, boolean[] boundary) {
		int moved = 0, needRecurse = 0;
		char toMove = molecule;
		while (toMove != 0) {
			int atomBit = lowestOneBit(toMove);
			int atom = numberOfTrailingZeros(atomBit);
			toMove &= ~atomBit;
			int targetCoord = pack(row(newAtoms, atom)+COORD_ADD[dirOrdinal][0],
					col(newAtoms, atom)+COORD_ADD[dirOrdinal][1]);
			if (boundary[targetCoord >> numberOfTrailingZeros(COORD_MASK)])
				return -1;
			for (int i = 0; i < newAtoms.length; ++i)
				//atom in the target coordinate, not in our molecule
				if ((newAtoms[i] & COORD_MASK) == targetCoord &&
						(molecule & (1 << i)) == 0)
					needRecurse |= (1 << i);
			newAtoms[atom] = (newAtoms[atom] & ~COORD_MASK) | targetCoord; //set
			moved |= atomBit;
		}

		while (needRecurse != 0) {
			int nrBit = lowestOneBit(needRecurse);
			char recurseMolecule = molecule(newAtoms, numberOfTrailingZeros(nrBit));
			int r = tryMove(newAtoms, recurseMolecule, dirOrdinal, boundary);
			if (r == -1) return -1;
			moved |= r;
			//anything that moved during the recursion no longer needs recursion
			needRecurse &= ~r;
		}

		return moved;
	}

	public Object pack(int totalFEInPuzzle) {
		int remainingFE = 0;
		for (int i = 0; i < atoms.length; ++i)
			remainingFE += freeElectrons(atoms, i);
		int boundCount = (totalFEInPuzzle - remainingFE)/2;

		//because we don't sort the atoms, we need only store their coordinate,
		//not their element or player flag
		//bonds are idx1 << 4 | idx2.
		DataContainer d = DataContainer.create(atoms.length + boundCount);
		int i = 0;
		for (int a : atoms)
			d.set(i++, (byte)((a & COORD_MASK) >> numberOfTrailingZeros(COORD_MASK)));
		//TODO: exit early if we've seen all the bonds?
		for (int idx1 = 0; idx1 < atoms.length; ++idx1) {
			int bonds = bonds(atoms, idx1);
			while (bonds != 0) {
				int bit = lowestOneBit(bonds);
				bonds &= ~bit;
				int idx2 = numberOfTrailingZeros(bit);
				if (idx1 < idx2)
					d.set(i++, (byte)(idx1 << 4 | idx2));
			}
		}
		return d;
	}

	private static char bonds(int[] atoms, int atom) {
		return (char)(atoms[atom] & BONDS_MASK);
	}

	private static void setBonds(int[] atoms, int atom, char bonds) {
		atoms[atom] = (atoms[atom] & ~BONDS_MASK) | bonds;
	}

	private static boolean bonded(int[] atoms, int a, int b) {
		//TODO: assert symmetry
		return (bonds(atoms, a) & (1 << b)) != 0;
	}

	private static int freeElectrons(int[] atoms, int atom) {
		return (atoms[atom] & ELECTRON_MASK) >> numberOfTrailingZeros(ELECTRON_MASK);
	}

	private static void setFreeElectrons(int[] atoms, int atom, int fe) {
		atoms[atom] = (atoms[atom] & ~ELECTRON_MASK) | (fe << numberOfTrailingZeros(ELECTRON_MASK));
	}

	//returns true if successful, false if unsuccessful
	private static boolean bond(int[] atoms, int a, int b) {
		int afe = freeElectrons(atoms, a), bfe = freeElectrons(atoms, b);
		if (afe == 0 || bfe == 0) return false;
		setFreeElectrons(atoms, a, afe-1);
		setFreeElectrons(atoms, b, bfe-1);
		setBonds(atoms, a, (char)(bonds(atoms, a) | (1 << b)));
		setBonds(atoms, b, (char)(bonds(atoms, b) | (1 << a)));
		return true;
	}

	private static int row(int[] atoms, int atom) {
		return (atoms[atom] & ROW_MASK) >> numberOfTrailingZeros(ROW_MASK);
	}

	private static int col(int[] atoms, int atom) {
		return (atoms[atom] & COL_MASK) >> numberOfTrailingZeros(COL_MASK);
	}

	private static int pack(Element e) {
		return e.ordinal() << numberOfTrailingZeros(ELEMENT_MASK);
	}

	private static int packFE(int freeElectrons) {
		assert 0 <= freeElectrons && freeElectrons <= 4 : freeElectrons;
		return freeElectrons << numberOfTrailingZeros(ELECTRON_MASK);
	}

	private static int pack(Coordinate c) {
		return pack(c.row(), c.col());
	}

	private static int pack(int row, int col) {
		return row << numberOfTrailingZeros(ROW_MASK) |
				col << numberOfTrailingZeros(COL_MASK);
	}

	private static int absdiff(int a, int b) {
		return Math.abs(a - b);
	}

	public static boolean[] encodeBoundary(Set<Coordinate> boundary) {
		boolean[] b = new boolean[256];
		for (Coordinate c : boundary)
			b[c.row() << 4 | c.col()] = true;
		return b;
	}
}
