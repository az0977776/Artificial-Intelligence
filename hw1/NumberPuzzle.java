// Aaron Wang
// Time for 16 Steps (Displaced): 0.531 seconds
// Time for 16 Steps (Manhattan): 0.399 seconds
// Time for 40 Steps (Manhattan): 5.222 seconds

// Manhattan distance is the best heuristic out of the three because
// it is the closest to the actual problem cost compared to the other two heuristics
// and will outperform them on larger problems in finding gradually better states.

// The counting tiles displaced will still be admissible if the "1" tile can jump from 
// anywhere. eg) case where all tiles from 2 to 15 are in the correct position and 
// top left is "blank" and bottom right is "1" (manhattan and euclidean not admissible,
// they both overestimate cost left)

import java.util.*;
import java.lang.*;

// Solving the 16-puzzle with A* using two heuristics:
// tiles-out-of-place and total-distance-to-move
public class NumberPuzzle implements Comparable<NumberPuzzle>{
    public static final int PUZZLE_WIDTH = 4;
    public static final int BLANK = 0;
    // BETTER:  false for tiles-displaced heuristic, true for Manhattan distance
    public static boolean BETTER = true;

    // You probably don't need to touch this representation, but if you want
    // to, notice that you need to keep the tiles and blank location consistent
    private int[][] tiles;  // [row][column]
    private int blank_r, blank_c;   // blank row and column
    public int costSoFar; // cost of path to this state from the original state
    public NumberPuzzle parent; // what is the parent state of this state

    public static void main(String[] args) {
        NumberPuzzle myPuzzle = readPuzzle();
        LinkedList<NumberPuzzle> solutionSteps = myPuzzle.solve();
        printSteps(solutionSteps);
    }

    NumberPuzzle() {
        tiles = new int[PUZZLE_WIDTH][PUZZLE_WIDTH];
    }

    static NumberPuzzle readPuzzle() {
        NumberPuzzle newPuzzle = new NumberPuzzle();

        Scanner myScanner = new Scanner(System.in);
        int row = 0;
        while (myScanner.hasNextLine() && row < PUZZLE_WIDTH) {
            String line = myScanner.nextLine();
            String[] numStrings = line.split(" ");
            for (int i = 0; i < PUZZLE_WIDTH; i++) {
                if (numStrings[i].equals("-")) {
                    newPuzzle.tiles[row][i] = BLANK;
                    newPuzzle.blank_r = row;
                    newPuzzle.blank_c = i;
                } else {
                	try {
                    	newPuzzle.tiles[row][i] = Integer.parseInt(numStrings[i]);
                    } catch (Exception e) {
                    	System.err.println("Bad format!  Bailing out.");
                    	System.exit(0);
                    }
                }
            }
            row++;
        }
        return newPuzzle;
    }

    public String toString() {
        String out = "";
        for (int i = 0; i < PUZZLE_WIDTH; i++) {
            for (int j = 0; j < PUZZLE_WIDTH; j++) {
                if (j > 0) {
                    out += " ";
                }
                if (tiles[i][j] == BLANK) {
                    out += "-";
                } else {
                    out += tiles[i][j];
                }
            }
            out += "\n";
        }
        return out;
    }

    public NumberPuzzle copy() {
        NumberPuzzle clone = new NumberPuzzle();
        clone.blank_r = blank_r;
        clone.blank_c = blank_c;
        clone.costSoFar = costSoFar;
        for (int i = 0; i < PUZZLE_WIDTH; i++) {
            for (int j = 0; j < PUZZLE_WIDTH; j++) {
                clone.tiles[i][j] = this.tiles[i][j];
            }
        }
        return clone;
    }

    public int hashCode() {
        return java.util.Arrays.deepHashCode(this.tiles);
    }

    public boolean equals(Object o) {
    	if (o == null || !(o instanceof NumberPuzzle)) {
    		return false;
    	}
        NumberPuzzle other = (NumberPuzzle) o;
    	for (int i = 0; i < PUZZLE_WIDTH; i++) {
            for (int j = 0; j < PUZZLE_WIDTH; j++) {
                if (tiles[i][j] != other.tiles[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    // This helper should assist in ensuring your solution is the same as
    // ours on HackerRank, by generating neighbors in the same order.
    // Notice how it copies the board to generate new
    // moves, instead of trying to work with the same board.  Backtracking
    // does not work well in the context of A*, since we are jumping around
    // the search tree, expanding the most promising node on the frontier.
    LinkedList<NumberPuzzle> legalMoves() {
        LinkedList<NumberPuzzle> legal = new LinkedList<NumberPuzzle>();
        if (blank_r > 0) {
            // Move tile down (blank goes up)
            NumberPuzzle downResult = this.copy();
            downResult.move(blank_r-1, blank_c);
            downResult.costSoFar = costSoFar + 1;
            downResult.parent = this;
            legal.add(downResult);
        }
        if (blank_c > 0) {
            // Move tile right (blank goes left)
            NumberPuzzle rightResult = this.copy();
            rightResult.move(blank_r, blank_c-1);
            rightResult.costSoFar = costSoFar + 1;
            rightResult.parent = this;
            legal.add(rightResult);
        }
        if (blank_r < PUZZLE_WIDTH - 1) {
            // Move tile up (blank goes down)
            NumberPuzzle upResult = this.copy();
            upResult.move(blank_r+1, blank_c);
            upResult.costSoFar = costSoFar + 1;
            upResult.parent = this;
            legal.add(upResult);
        }
        if (blank_c < PUZZLE_WIDTH - 1) {
            // Move tile left (blank goes right)
            NumberPuzzle leftResult = this.copy();
            leftResult.move(blank_r,blank_c+1);
            leftResult.costSoFar = costSoFar + 1;
            leftResult.parent = this;
            legal.add(leftResult);
        }
        return legal;
    }

    public void move(int tile_row, int tile_column) {
        // This is only called in legalMoves(), which only does legal things.
        // So we're just going to plop the tile into the blank space,
        // leaving a blank where it was.
        tiles[blank_r][blank_c] = tiles[tile_row][tile_column];
        tiles[tile_row][tile_column] = BLANK;
        blank_r = tile_row;
        blank_c = tile_column;
    }

    // total numbers out of place
    public int tilesOutOfPlace() {
        int output = 0;
        for (int i = 0; i < PUZZLE_WIDTH; i++) {
            for (int j = 0; j < PUZZLE_WIDTH; j++) {
                int value = tiles[i][j];
                if (value != BLANK && value - 1 != i * PUZZLE_WIDTH + j) {
                    output += 1;
                }
            }
        }
        return output;
    }

    // manhattan distance between two points
    public int manhattanDistanceHelper(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    // sum of manhattan distances of all out of place tiles
    public int manhattanDistance() {
        int output = 0;
        for (int i = 0; i < PUZZLE_WIDTH; i++) {
            for (int j = 0; j < PUZZLE_WIDTH; j++) {
                int value = tiles[i][j];
                if (value != BLANK && value - 1 != i * PUZZLE_WIDTH + j) {
                    output += manhattanDistanceHelper(i, j, (value - 1) / PUZZLE_WIDTH, (value - 1) % PUZZLE_WIDTH);
                }
            }
        }
        return output;
    }

    // cost function = heuristic cost + cost so far
    public int totalCost() {
        return costSoFar + (BETTER ? manhattanDistance() : tilesOutOfPlace());
    }

    @Override
    public int compareTo(NumberPuzzle o) {
        if (this == o) {
            return 0;
        }
        int thisCost = this.totalCost();
        int otherCost = o.totalCost();
        if (thisCost == otherCost) {
            return 0;
        }
        if (thisCost > otherCost) {
            return 1;
        }
        return -1;
    }

    LinkedList<NumberPuzzle> solve() {
        // Using pseudocode similar to one from Lecture 3, but instead of having table hold cost so far,
        // Each node/NumberPuzzle holds costs so far and which node is its parent
        LinkedList<NumberPuzzle> solution = new LinkedList<NumberPuzzle>();
        PriorityQueue<NumberPuzzle> queue = new PriorityQueue<NumberPuzzle>();
        HashSet<NumberPuzzle> closedSet = new HashSet<NumberPuzzle>();
        NumberPuzzle current;
        NumberPuzzle end = null;

        queue.add(this);

        // grabs the state with the lowest cost based on the cost function
        while ((current = queue.poll()) != null) {

            // ignore if state has been processed already
            if (closedSet.contains(current)) {
                continue;
            }

            // if is solution, break out of loop
            if (current.solved()) {
                end = current;
                break;
            }

            // add the new moves from the current state to priority queue
            for (NumberPuzzle np : current.legalMoves()) {
                queue.add(np);
            }

            closedSet.add(current);
        }

        // build the solution path from child -> parent up
        while (end != null) {
            solution.push(end);
            end = end.parent;
        }

        return solution;
    }

    // solved() returns true if the puzzle has reached its goal state
    public boolean solved() {
        int shouldBe = 1;
        for (int i = 0; i < PUZZLE_WIDTH; i++) {
            for (int j = 0; j < PUZZLE_WIDTH; j++) {
                if (tiles[i][j] != shouldBe) {
                    return false;
                } else {
                    // Take advantage of BLANK == 0
                    shouldBe = (shouldBe + 1) % (PUZZLE_WIDTH*PUZZLE_WIDTH);
                }
            }
        }
        return true;
    }

    static void printSteps(LinkedList<NumberPuzzle> steps) {
        for (NumberPuzzle s : steps) {
            System.out.println(s);
        }
    }

}
