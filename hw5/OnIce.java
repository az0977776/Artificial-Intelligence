import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class OnIce {

    public static final double GOLD_REWARD = 100.0;
    public static final double PIT_REWARD = -150.0;
    public static final double DISCOUNT_FACTOR = 0.5;
    public static final double EXPLORE_PROB = 0.2;  // for Q-learning
    public static final double LEARNING_RATE = 0.1;
    public static final int ITERATIONS = 10000;
    public static final int MAX_MOVES = 1000;
    public static final int ACTIONS = 4;
    public static final int UP = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;

    // Using a fixed random seed so that the behavior is a little
    // more reproducible across runs & students
    public static Random rng = new Random(5100);

    public static void main(String[] args) {
        Scanner myScanner = new Scanner(System.in);
        String approach = myScanner.nextLine();
        if (approach.equals("test-new-q")) {
            testNewQ(myScanner);
            System.exit(0);
        }
        Problem problem = new Problem(myScanner, approach);
        Policy policy = problem.solve(ITERATIONS);
        if (policy == null) {
            System.err.println("No policy.  Invalid solution approach?");
        } else {
            System.out.println(policy);
        }
        if (args.length > 0 && args[0].equals("eval")) {
            System.out.println("Average utility per move: "
                                + tryPolicy(policy, problem));
        }
    }

    // Format of a Q test:
    // Reward of old square
    // Reward of new square
    // Q-values of UP, RIGHT, DOWN, LEFT for old square (space-separated)
    // Q-values of UP, RIGHT, DOWN, LEFT for new square (space-separated)
    // Which action we took ("UP", etc)
    // Not all of this information is useful.
    public static void testNewQ(Scanner sc) {
        try {
            // Without loss of generality, plop these down in a 1x2 grid.
            // 0,0 is the old space, 0,1 is the new space.
            // For the test, this doesn't have anything to do with which action
            // moved us.
            double[][] rewards = new double[1][2];
            double utilities[][][] = new double[ACTIONS][1][2];
            rewards[0][0] = Double.parseDouble(sc.nextLine());
            rewards[0][1] = Double.parseDouble(sc.nextLine());
            String[] oldQStrings = sc.nextLine().split(" ");
            double[] oldQs = new double[ACTIONS];
            for (int i = 0; i < ACTIONS; i++) {
                utilities[i][0][0] = Double.parseDouble(oldQStrings[i]);
            }
            String[] newQStrings = sc.nextLine().split(" ");
            for (int i = 0; i < ACTIONS; i++) {
                utilities[i][0][1] = Double.parseDouble(newQStrings[i]);
            }
            int move = UP;
            switch(sc.nextLine()) {
                case "UP":
                    move = UP;
                    break;
                case "RIGHT":
                    move = RIGHT;
                    break;
                case "DOWN":
                    move = DOWN;
                    break;
                case "LEFT":
                    move = LEFT;
                    break;
                default:
                    System.err.println("Bad test format");
                    System.exit(1);
            }
            double new_q = newQ(rewards, utilities, 0, 0, 0, 1, move);
            System.out.println(String.format("%.3f", new_q));

        } catch (Exception e) {
            System.err.println("Bad input format for test.  Consult the comments or HackerRank examples.");
            System.exit(1);
        }
    }

    public static class Problem {
        public String approach;
        public double[] moveProbs;
        public ArrayList<ArrayList<String>> map;

        // Format looks like
        // MDP    [approach to be used]
        // 0.7 0.2 0.1   [probability of going 1, 2, 3 spaces]
        // - - - - - - P - - - -   [space-delimited map rows]
        // - - G - - - - - P - -   [G is gold, P is pit]
        //
        // You can assume the maps are rectangular, although this isn't enforced
        // by this constructor.

        Problem (Scanner sc, String approach) {
            this.approach = approach;
            String probsString = sc.nextLine();
            String[] probsStrings = probsString.split(" ");
            moveProbs = new double[probsStrings.length];
            for (int i = 0; i < probsStrings.length; i++) {
                try {
                    moveProbs[i] = Double.parseDouble(probsStrings[i]);
                } catch (NumberFormatException e) {
                    break;
                }
            }
            map = new ArrayList<ArrayList<String>>();
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] squares = line.split(" ");
                ArrayList<String> row = new ArrayList<String>(Arrays.asList(squares));
                map.add(row);
            }
        }

        Policy solve(int iterations) {
            if (approach.equals("MDP")) {
                MDPSolver mdp = new MDPSolver(this);
                return mdp.solve(this, iterations);
            } else if (approach.equals("Q")) {
                QLearner q = new QLearner(this);
                return q.solve(this, iterations);
            }
            return null;
        }

    }

    public static class Policy {
        public String[][] bestActions;

        public Policy(Problem prob) {
            bestActions = new String[prob.map.size()][prob.map.get(0).size()];
        }

        public String toString() {
            String out = "";
            for (int r = 0; r < bestActions.length; r++) {
                for (int c = 0; c < bestActions[0].length; c++) {
                    if (c != 0) {
                        out += " ";
                    }
                    out += bestActions[r][c];
                }
                out += "\n";
            }
            return out;
        }
    }

    // Returns the average utility per move of the policy,
    // as measured from ITERATIONS random drops of an agent onto
    // empty spaces
    public static double tryPolicy(Policy policy, Problem prob) {
        int totalUtility = 0;
        int totalMoves = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            // Random empty starting loc
            int row, col;
            do {
                row = rng.nextInt(prob.map.size());
                col = rng.nextInt(prob.map.get(0).size());
            } while (!prob.map.get(row).get(col).equals("-"));
            // Run until pit, gold, or MAX_MOVES timeout
            // (in case policy recommends driving into wall repeatedly,
            // for example)
            for (int moves = 0; moves < MAX_MOVES; moves++) {
                totalMoves++;
                String policyRec = policy.bestActions[row][col];
                // Determine how far we go in that direction
                int displacement = 1;
                double totalProb = 0;
                double moveSample = rng.nextDouble();
                for (int p = 0; p < prob.moveProbs.length; p++) {
                    totalProb += prob.moveProbs[p];
                    if (moveSample <= totalProb) {
                        displacement = p+1;
                        break;
                    }
                }
                int new_row = row;
                int new_col = col;
                if (policyRec.equals("U")) {
                    new_row -= displacement;
                    if (new_row < 0) {
                        new_row = 0;
                    }
                } else if (policyRec.equals("R")) {
                    new_col += displacement;
                    if (new_col >= prob.map.get(0).size()) {
                        new_col = prob.map.get(0).size()-1;
                    }
                } else if (policyRec.equals("D")) {
                    new_row += displacement;
                    if (new_row >= prob.map.size()) {
                        new_row = prob.map.size()-1;
                    }
                } else if (policyRec.equals("L")) {
                    new_col -= displacement;
                    if (new_col < 0) {
                        new_col = 0;
                    }
                }
                row = new_row;
                col = new_col;
                if (prob.map.get(row).get(col).equals("G")) {
                    totalUtility += GOLD_REWARD;
                    // End the current trial
                    break;
                } else if (prob.map.get(row).get(col).equals("P")) {
                    totalUtility += PIT_REWARD;
                    break;
                }
            }
        }

        return totalUtility/(double)totalMoves;
    }


    public static class MDPSolver {

        // We'll want easy access to the real rewards while iterating, so
        // we'll keep both of these around
        public double[][] utilities;
        public double[][] rewards;

        public MDPSolver(Problem prob) {
            utilities = new double[prob.map.size()][prob.map.get(0).size()];
            rewards = new double[prob.map.size()][prob.map.get(0).size()];
            // Initialize utilities to the rewards in their spaces,
            // else 0
            for (int r = 0; r < utilities.length; r++) {
                for (int c = 0; c < utilities[0].length; c++) {
                    String spaceContents = prob.map.get(r).get(c);
                    if (spaceContents.equals("G")) {
                        utilities[r][c] = GOLD_REWARD;
                        rewards[r][c] = GOLD_REWARD;
                    } else if (spaceContents.equals("P")) {
                        utilities[r][c] = PIT_REWARD;
                        rewards[r][c] = PIT_REWARD;
                    } else {
                        utilities[r][c] = 0.0;
                        rewards[r][c] = 0.0;
                    }
                }
            }
        }

        Policy solve(Problem prob, int iterations) {
            Policy policy = new Policy(prob);

            // the height and width of the map
            int height = prob.map.size();
            int width = prob.map.get(0).size();

            // for <iteration> times
            for (int iter = 0; iter < iterations; iter++) {
                // create the next state utility mapping
                double[][] newUtilities = new double[height][width];

                // for each block in the state
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {

                        // to calculates the expected utility in doing each action
                        double leftVal = 0;
                        double upVal = 0;
                        double rightVal = 0;
                        double downVal = 0;
                        for (int m = 0; m < prob.moveProbs.length; m++) {
                            // if moving out of bounds then util is the block right before wall
                            // unless you are already on that block, then 0
                            leftVal +=  prob.moveProbs[m] * ((j-(m+1)) < 0 ? (j == 0 ? 0 : utilities[i][0]) : utilities[i][j-(m+1)]);
                            upVal +=  prob.moveProbs[m] * ((i-(m+1)) < 0 ? (i == 0 ? 0 : utilities[0][j]) : utilities[i-(m+1)][j]);
                            rightVal +=  prob.moveProbs[m] * ((j+(m+1)) > (width - 1) ? (j == width - 1 ? 0 : utilities[i][width-1]) : utilities[i][j+(m+1)]);
                            downVal +=  prob.moveProbs[m] * ((i+(m+1)) > (height - 1) ? (i == height - 1 ? 0 : utilities[height-1][j]) : utilities[i+(m+1)][j]);
                        }
                        
                        // update util using the Bellman equation
                        newUtilities[i][j] = rewards[i][j] + 
                           DISCOUNT_FACTOR * Math.max(Math.max(leftVal, rightVal), Math.max(upVal, downVal));
                    }
                }
                utilities = newUtilities;
            }

            // finding the policy per block
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    // if it is a gold or pit just write "G" or "P"
                    if (prob.map.get(i).get(j).equals("G") || prob.map.get(i).get(j).equals("P")) {
                        policy.bestActions[i][j] = prob.map.get(i).get(j);
                    } else {
                        // best direction to move in has highest expected util
                        double leftVal = 0;
                        double upVal = 0;
                        double rightVal = 0;
                        double downVal = 0;
                        for (int m = 0; m < prob.moveProbs.length; m++) {
                            // if move out of bound then util is the block next to wall
                            // unless you are already on that block, then 0
                            leftVal +=  prob.moveProbs[m] * ((j-(m+1)) < 0 ? (i == 0 ? 0 : utilities[i][0]) : utilities[i][j-(m+1)]);
                            upVal +=  prob.moveProbs[m] * ((i-(m+1)) < 0 ? (j == 0 ? 0 : utilities[0][j]) : utilities[i-(m+1)][j]);
                            rightVal +=  prob.moveProbs[m] * ((j+(m+1)) > (width - 1) ? (j == width - 1 ? 0 : utilities[i][width-1]) : utilities[i][j+(m+1)]);
                            downVal +=  prob.moveProbs[m] * ((i+(m+1)) > (height - 1) ? (i == height - 1 ? 0 : utilities[height-1][j]) : utilities[i+(m+1)][j]);
                        }

                        // select the action with the highest expected util as policy for block
                        double maxVal = Double.NEGATIVE_INFINITY;
                        if (upVal > maxVal) {
                            maxVal = upVal;
                            policy.bestActions[i][j] = "U";   
                        } 
                        if (rightVal > maxVal) {
                            maxVal = rightVal;
                            policy.bestActions[i][j] = "R";   
                        } 
                        if (downVal > maxVal) {
                            maxVal = downVal;
                            policy.bestActions[i][j] = "D";   
                        } 
                        if (leftVal > maxVal) {
                            maxVal = leftVal;
                            policy.bestActions[i][j] = "L";
                        }
                    }
                }
            }
            return policy;
        }
    }

    // QLearner:  Same problem as MDP, but the agent doesn't know what the
    // world looks like, or what its actions do.  It can learn the utilities of
    // taking actions in particular states through experimentation, but it
    // has no way of realizing what the general action model is
    // (like "Right" increasing the column number in general).
    public static class QLearner {

        public double utilities[][][];  // utilities of actions
        public double rewards[][];

        public QLearner(Problem prob) {
            utilities = new double[ACTIONS][prob.map.size()][prob.map.get(0).size()];
            // Rewards are for convenience of lookup; the learner doesn't
            // actually "know" they're there until encountering them
            rewards = new double[prob.map.size()][prob.map.get(0).size()];
            for (int r = 0; r < rewards.length; r++) {
                for (int c = 0; c < rewards[0].length; c++) {
                    String locType = prob.map.get(r).get(c);
                    if (locType.equals("G")) {
                        rewards[r][c] = GOLD_REWARD;
                    } else if (locType.equals("P")) {
                        rewards[r][c] = PIT_REWARD;
                    } else {
                        rewards[r][c] = 0.0; // not strictly necessary to init
                    }
                }
            }
            // Java: default init utilities to 0
        }

        public Policy solve(Problem prob, int iterations) {
            Policy policy = new Policy(prob);

            // the height and width of the map
            int height = prob.map.size();
            int width = prob.map.get(0).size();

            for (int iter = 0; iter < iterations; iter++) {
                // select a random starting square
                int row = rng.nextInt(height);
                int col = rng.nextInt(width);

                // trial immediately over if dropped into gold or pit
                String currentBlock = prob.map.get(row).get(col);
                if (currentBlock.equals("G") || currentBlock.equals("P")) {
                    continue;
                }

                int moveCount = 0;
                int moveDirection = 0;
                while (moveCount < MAX_MOVES) {
                    moveCount++;
                    
                    // first determine which direction to move in
                    // explore
                    if (rng.nextDouble() < EXPLORE_PROB) {
                        moveDirection = rng.nextInt(ACTIONS);
                    } else { // best Q-value move at current block
                        double maxUtil = Double.NEGATIVE_INFINITY;
                        for (int a = 0; a < ACTIONS; a++) {
                            if (utilities[a][row][col] > maxUtil) {
                                maxUtil = utilities[a][row][col];
                                moveDirection = a;
                            }
                        }
                    }

                    // then figure out how much the agent will move
                    int moveAmount = 0;
                    double totalProb = 0;
                    double moveSample = rng.nextDouble();
                    for (int p = 0; p < prob.moveProbs.length; p++) {
                        totalProb += prob.moveProbs[p];
                        if (moveSample <= totalProb) {
                            moveAmount = p+1;
                            break;
                        }
                    }

                    // find the new block based on move direction and amount
                    int new_row = row;
                    int new_col = col;
                    if (actionToString(moveDirection).equals("U")) {
                        new_row -= moveAmount;
                        if (new_row < 0) {
                            new_row = 0;
                        }
                    } else if (actionToString(moveDirection).equals("R")) {
                        new_col += moveAmount;
                        if (new_col >= width) {
                            new_col = width-1;
                        }
                    } else if (actionToString(moveDirection).equals("D")) {
                        new_row += moveAmount;
                        if (new_row >= height) {
                            new_row = height-1;
                        }
                    } else if (actionToString(moveDirection).equals("L")) {
                        new_col -= moveAmount;
                        if (new_col < 0) {
                            new_col = 0;
                        }
                    }

                    //System.out.printf("Move: (%d, %d) to (%d, %d)\n", row, col, new_row, new_col);

                    // when landing on gold or pit, handle by setting Q values for
                    // the space to the reward of that spot, then go to next iteration
                    if (prob.map.get(new_row).get(new_col).equals("G")) {
                        for (int a = 0; a < ACTIONS; a++) {
                            utilities[a][new_row][new_col] = GOLD_REWARD;
                        }
                    } else if (prob.map.get(new_row).get(new_col).equals("P")) {
                        for (int a = 0; a < ACTIONS; a++) {
                            utilities[a][new_row][new_col] = PIT_REWARD;
                        }
                    } 

                    // finding the best action with highest util from new square
                    int bestNewMove = 0;
                    double bestNewQVal = Double.NEGATIVE_INFINITY;
                    for (int a = 0; a < ACTIONS; a++) {
                        if (utilities[a][new_row][new_col] > bestNewQVal) {
                            bestNewQVal = utilities[a][new_row][new_col];
                            bestNewMove = a;
                        }
                    }

                    //System.out.printf("bestmove at (%d, %d): %s %f\n", new_row, new_col, actionToString(bestNewMove), bestNewQVal);

                    // update the Q val of the previous square
                    double oldQVal = utilities[moveDirection][row][col];
                    utilities[moveDirection][row][col] = oldQVal + LEARNING_RATE * (rewards[row][col] + DISCOUNT_FACTOR * bestNewQVal - oldQVal);

                    row = new_row;
                    col = new_col;
                }
            }

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    System.out.print("( ");
                    for (int a = 0; a < ACTIONS; a++) {
                        System.out.printf("%f ", utilities[a][i][j]);
                    }
                    System.out.print(")");
                }
                System.out.println("\n");
            }

            // finding the policy per block
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    // if it is a gold or pit just write "G" or "P"
                    if (prob.map.get(i).get(j).equals("G") || prob.map.get(i).get(j).equals("P")) {
                        policy.bestActions[i][j] = prob.map.get(i).get(j);
                    } else {
                        double maxVal = Double.NEGATIVE_INFINITY;
                        for (int a = 0; a < ACTIONS; a++) {
                            // policy should be action with highest util at thie block
                            if (utilities[a][i][j] > maxVal) {
                                maxVal = utilities[a][i][j];
                                policy.bestActions[i][j] = actionToString(a);   
                            }
                        }
                    }
                }
            }
            return policy;
        }
    }

    // Testable Q-learning function.  Returns the new utility value for space (r,c).
    // Should use the LEARNING_RATE and DISCOUNT_FACTOR.
    // r and c are the row and column we were in when we took the action.
    // new_r and new_c are the row and column where we landed.
    // move is the action we took, the one resulting in this q-learning step.
    // Rewards is indexed [r][c], utilities is indexed [a][r][c].
    // (If you want to destructively change utilities[][][] here, that's fine.)
    public static double newQ(double rewards[][], double[][][] utilities,
        int r, int c, int new_r, int new_c, int move) {
            int bestNewMove = 0;
            double bestNewQVal = Double.NEGATIVE_INFINITY;
            for (int a = 0; a < ACTIONS; a++) {
                if (utilities[a][new_r][new_c] > bestNewQVal) {
                    bestNewQVal = utilities[a][new_r][new_c];
                    bestNewMove = a;
                }
            }
            double oldQ = utilities[move][r][c];
            return oldQ + LEARNING_RATE * (rewards[r][c] + DISCOUNT_FACTOR * bestNewQVal - oldQ);
        }

    public static String actionToString(int action) {
        switch(action) {
            case UP:
                return "U";
            case RIGHT:
                return "R";
            case DOWN:
                return "D";
            case LEFT:
                return "L";
            default:
                return "-";
        }
    }

}
