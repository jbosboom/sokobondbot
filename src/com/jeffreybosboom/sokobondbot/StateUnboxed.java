package com.jeffreybosboom.sokobondbot;

import com.google.common.collect.Multiset;
import com.jeffreybosboom.parallelbfs.DataContainer;
import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
	//for player atom (atoms[0]) only
	private static final int TOTAL_BONDS =	0b11111000_00000000_00000000_00000000;
	private static final int COORD_MASK = ROW_MASK | COL_MASK;
	private static final int PLAYER_ATOM = 0;
	private final int[] atoms;
	private final Path path;

	public static final class PreprocessedPuzzle {
		private final boolean[] boundary;
		private final List<Coordinate> initialAtomOrder;
		private final byte[] elementRanges;
		private final char[] forbiddenBonds;
		private PreprocessedPuzzle(boolean[] boundary, List<Coordinate> initialAtomOrder, byte[] elementRanges, char[] forbiddenBonds) {
			this.boundary = boundary;
			this.initialAtomOrder = initialAtomOrder;
			this.elementRanges = elementRanges;
			this.forbiddenBonds = forbiddenBonds;
		}
	}

	public static PreprocessedPuzzle preprocess(Puzzle puzzle) {
		boolean[] boundary = new boolean[256];
		for (Coordinate c : puzzle.boundary())
			boundary[c.row() << 4 | c.col()] = true;

		List<Coordinate> initialAtomOrder = new ArrayList<>(puzzle.atoms().keySet());
		initialAtomOrder.remove(puzzle.playerAtom());
		//reversed sort puts atoms with more bonds first, helium last
		initialAtomOrder.sort(Comparator.comparing(puzzle.atoms()::get).reversed());
		initialAtomOrder.add(0, puzzle.playerAtom());

		List<Byte> elementRangesList = new ArrayList<>();
		//player atom not in an element range
		int start = 1;
		Element e = puzzle.atoms().get(initialAtomOrder.get(start));
		for (int i = start+1; i < initialAtomOrder.size(); ++i) {
			Element c = puzzle.atoms().get(initialAtomOrder.get(i));
			if (e != c) {
				elementRangesList.add((byte)(start << 4 | i));
				start = i;
				e = c;
			}
		}
		elementRangesList.add((byte)(start << 4 | initialAtomOrder.size()));
		byte[] elementRanges = new byte[elementRangesList.size()];
		for (int i = 0; i < elementRangesList.size(); ++i)
			elementRanges[i] = elementRangesList.get(i);

		//hack to get atoms array
		int[] atoms = new StateUnboxed(puzzle, new PreprocessedPuzzle(boundary, initialAtomOrder, elementRanges, null)).atoms;
		char[] forbiddenBonds = computeForbiddenBonds(atoms);

		return new PreprocessedPuzzle(boundary, initialAtomOrder, elementRanges, forbiddenBonds);
	}

	private static char[] computeForbiddenBonds(int[] atoms) {
		char[] possibleBonds = new char[atoms.length];
		solveNoGeometry(atoms, possibleBonds);
		for (int i = 0; i < possibleBonds.length; ++i)
			possibleBonds[i] = (char)~possibleBonds[i];
		return possibleBonds;
	}

	private static void solveNoGeometry(int[] atoms, char[] possibleBonds) {
		int totalFE = 0;
		for (int i = 0; i < atoms.length; ++i)
			totalFE += freeElectrons(atoms, i);
		if (totalFE == 0) {
			//this is a solution, so all these bonds are possible
			for (int i = 0; i < atoms.length; ++i)
				possibleBonds[i] |= bonds(atoms, i);
			return;
		}
		for (int i = 0; i < atoms.length; ++i)
			for (int j = i+1; j < atoms.length; ++j) {
				if (bond(atoms, i, j)) {
					solveNoGeometry(atoms, possibleBonds);
					unbond(atoms, i, j);
				}
			}
	}

	public StateUnboxed(Puzzle puzzle, PreprocessedPuzzle prepro) {
		this.path = TwoLongPath.empty();
		this.atoms = new int[puzzle.atoms().size()];
		atoms[PLAYER_ATOM] = packFE(puzzle.atoms().get(puzzle.playerAtom()).maxElectrons()) |
				pack(puzzle.playerAtom());
		int i = 1;
		for (Coordinate c : prepro.initialAtomOrder) {
			if (c.equals(puzzle.playerAtom())) continue;
			atoms[i++] = packFE(puzzle.atoms().get(c).maxElectrons()) |	pack(c);
		}
		for (Multiset.Entry<Pair<Coordinate, Coordinate>> entry : puzzle.bonds().entrySet()) {
			if (entry.getCount() > 1) throw new UnsupportedOperationException("multiple bonds not supported");
			Pair<Coordinate, Coordinate> bond = entry.getElement();
			int a = prepro.initialAtomOrder.indexOf(bond.first()), b = prepro.initialAtomOrder.indexOf(bond.second());
			if (!bond(atoms, a, b))
				throw new IllegalArgumentException("Insoluble puzzle");
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

	public boolean isViable(PreprocessedPuzzle prepro) {
		//if forbidden bond, not viable.
		for (int i = 0; i < atoms.length; ++i)
			if ((bonds(atoms, i) & prepro.forbiddenBonds[i]) != 0)
				return false;
		return true;
	}

	public Path path() {
		return path;
	}

	public Stream<StateUnboxed> nextStates(PreprocessedPuzzle prepro) {
		List<StateUnboxed> nextStates = new ArrayList<>(4);
		char molecule = molecule(atoms, 0);
		fail: for (Direction dir : Direction.values()) {
			int[] newAtoms = atoms.clone();
			int movedAtoms = tryMove(newAtoms, molecule, dir.ordinal(), prepro.boundary);
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

	public Object pack(PreprocessedPuzzle prepro) {
		//Sort atom coordinates in each element range.  We'll remap bond indices
		//into the (sorted) packed order.
		DataContainer d = DataContainer.create(atoms.length + totalBonds(atoms));
		int i = 0;
		for (int j = 0; j < atoms.length; ++j)
			d.set(i++, coordByte(atoms, j));
		for (byte r : prepro.elementRanges)
			d.sort((r & 0xF0) >> 4, (r & 0xF));
		//bonds are idx1 << 4 | idx2.
		//TODO: exit early if we've seen all the bonds? (esp if many heliums)
		for (int idx1 = 0; idx1 < atoms.length; ++idx1) {
			int bonds = bonds(atoms, idx1);
			while (bonds != 0) {
				int bit = lowestOneBit(bonds);
				bonds &= ~bit;
				int idx2 = numberOfTrailingZeros(bit);
				//translate indices
				int idx1p = d.indexOf(coordByte(atoms, idx1));
				int idx2p = d.indexOf(coordByte(atoms, idx2));
				if (idx1 < idx2)
					d.set(i++, (byte)Math.min((byte)(idx1p << 4 | idx2p), (byte)(idx2p << 4 | idx1p)));
			}
		}
		return d;
	}

	public static final class ClosedSetPruner implements Consumer<List<StateUnboxed>> {
		private final Set<Object> closedSet;
		private final int atoms;
		private int minBondsInClosedSet = 0;
		public ClosedSetPruner(PreprocessedPuzzle prepro, Set<Object> closedSet) {
			this.closedSet = closedSet;
			this.atoms = prepro.initialAtomOrder.size();
		}
		@Override
		public void accept(List<StateUnboxed> frontier) {
			if (frontier.isEmpty()) return;
			//if there are no splitters in a puzzle, and all states in the
			//frontier have at least N bonds, we can remove all states with < N
			//bonds from the closed set.
			//can't short-circuit: http://stackoverflow.com/q/28801293/3614835
			//TODO: parallel if frontier.size() > N
			int minBonds = frontier.stream().mapToInt(s -> totalBonds(s.atoms)).min().getAsInt();
			if (minBonds > minBondsInClosedSet) {
				int before = closedSet.size();
				//TODO: parallel?
				closedSet.removeIf(d -> ((DataContainer)d).size() < (atoms + minBonds));
				minBondsInClosedSet = minBonds;
				int after = closedSet.size();
				System.out.format("pruned closed set to %d bonds, removed %d, now %d%n",
						minBondsInClosedSet, before-after, after);
			}
		}
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
		setTotalBonds(atoms, totalBonds(atoms)+1);
		return true;
	}

	private static boolean unbond(int[] atoms, int a, int b) {
		if (!bonded(atoms, a, b)) return false;
		int afe = freeElectrons(atoms, a), bfe = freeElectrons(atoms, b);
		setFreeElectrons(atoms, a, afe+1);
		setFreeElectrons(atoms, b, bfe+1);
		setBonds(atoms, a, (char)(bonds(atoms, a) & ~(1 << b)));
		setBonds(atoms, b, (char)(bonds(atoms, b) & ~(1 << a)));
		setTotalBonds(atoms, totalBonds(atoms)-1);
		return true;
	}

	private static int totalBonds(int[] atoms) {
		return ((atoms[0] & TOTAL_BONDS) >>> numberOfTrailingZeros(TOTAL_BONDS));
	}

	private static void setTotalBonds(int[] atoms, int totalBonds) {
		atoms[0] = (atoms[0] & ~TOTAL_BONDS) | (totalBonds << numberOfTrailingZeros(TOTAL_BONDS));
	}

	private static int row(int[] atoms, int atom) {
		return (atoms[atom] & ROW_MASK) >> numberOfTrailingZeros(ROW_MASK);
	}

	private static int col(int[] atoms, int atom) {
		return (atoms[atom] & COL_MASK) >> numberOfTrailingZeros(COL_MASK);
	}

	private static byte coordByte(int[] atoms, int atom) {
		return (byte)((atoms[atom] & COORD_MASK) >> numberOfTrailingZeros(COORD_MASK));
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
}
