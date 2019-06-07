import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.lang.Math;

// An assignment on decision trees, using the "Adult" dataset from
// the UCI Machine Learning Repository.  The dataset predicts
// whether someone makes over $50K a year from their census data.
//
// Input data is a comma-separated values (CSV) file where the
// target classification is given a label of "Target."
// The other headers in the file are the feature names.
//
// Features are assumed to be strings, with comparison for equality
// against one of the values as a decision, unless the value can
// be parsed as a double, in which case the decisions are < comparisons
// against the values seen in the data.

public class DecisionTree {

    public Feature feature;   // if true, follow the yes branch
    public boolean decision;  // for leaves
    public DecisionTree yesBranch;
    public DecisionTree noBranch;

    public static double CHI_THRESH = 3.84;
    public static double EPSILON = 0.00000001;
    public static boolean PRUNE = false;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        // Keep header line around for interpreting decision trees
        String header = scanner.nextLine();
        ArrayList<Example> trainExamples;
        HashSet<Feature> features;
        // Check for HackerRank tests.  These will only run to the end of their cases.
        switch(header) {
            case "test-entropy":
                try {
                    double p = Double.parseDouble(scanner.nextLine());
                    String out = String.format("%.3f", probToEntropy(p));
                    System.out.println(out);
                    System.exit(0);
                } catch (Exception e) {
                    System.err.println("Bad input; expected a probability on next line");
                    System.exit(1);
                }
                break;
            case "test-best-split":
                header = scanner.nextLine();
                Feature.featureNames = header.split(",");
                trainExamples = readExamples(scanner);
                features = generateFeatures(trainExamples);
                ExampleSplit bestSplit = GetBestSplit(features, trainExamples);
                System.out.println(bestSplit);
                System.exit(0);
                break;
            case "test-prune":
                header = scanner.nextLine();
                Feature.featureNames = header.split(",");
                trainExamples = readExamples(scanner);
                features = generateFeatures(trainExamples);
                ExampleSplit pruneSplit = GetBestSplit(features, trainExamples);
                System.out.println(pruneSplit);
                if (pruneSplit.positives.size() > 0 && pruneSplit.negatives.size() > 0) {
                    System.out.println(String.format("Chi square val: %.3f", pruneSplit.getChiValue()));
                }
                if (pruneSplit.shouldReturnMajority()) {
                    System.out.println("Don't split");
                } else {
                    System.out.println("Split");
                }
                System.exit(0);
                break;
            default:
                Feature.featureNames = header.split(",");
                break;
        }
        System.err.println("Reading training examples...");
        trainExamples = readExamples(scanner);
        // We'll assume a delimiter of "---" separates train and test as before
        DecisionTree tree = new DecisionTree(trainExamples);
        System.out.println(tree);
        System.out.println("Training data results: ");
        System.out.println(tree.classify(trainExamples));
        System.err.println("Reading test examples...");
        ArrayList<Example> testExamples = readExamples(scanner);
        Results results = tree.classify(testExamples);
        System.out.println("Test data results: ");
        System.out.print(results);
    }

    public static ArrayList<Example> readExamples(Scanner scanner) {
        ArrayList<Example> examples = new ArrayList<Example>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("---")) {
                break;
            }
            // Skip missing data lines
            if (!line.contains("?")) {
                Example newExample = new Example(line);
                examples.add(newExample);
            }
        }
        return examples;
    }

    public static class Example {
        public String[] strings;     // Use only if isNumerical[i] is false
        public double[] numericals;  // Use only if isNumerical[i] is true
        boolean target;

        public Example(String dataline) {
            // Assume a basic CSV with no double-quotes to handle real commas
            strings = dataline.split(",");
            // We'll maintain a separate array with everything that we can
            // put into numerical form, in numerical form.
            // No real need to distinguish doubles from ints.
            numericals = new double[strings.length];
            if (Feature.isNumerical == null) {
                // First data line; we're determining types
                Feature.isNumerical = new boolean[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    if (Feature.featureNames[i].equals("Target")) {
                        target = strings[i].equals("1");
                    } else {
                        try {
                            numericals[i] = Double.parseDouble(strings[i]);
                            Feature.isNumerical[i] = true;
                        } catch (NumberFormatException e) {
                            Feature.isNumerical[i] = false;
                            // string stays where it is, in strings
                        }
                    }
                }
            } else {
                for (int i = 0; i < strings.length; i++) {
                    if (i >= Feature.isNumerical.length) {
                        System.err.println("Too long line: " + dataline);
                    } else if (Feature.featureNames[i].equals("Target")) {
                        target = strings[i].equals("1");
                    } else if (Feature.isNumerical[i]) {
                        try {
                            numericals[i] = Double.parseDouble(strings[i]);
                        } catch (NumberFormatException e) {
                            Feature.isNumerical[i] = false;
                            // string stays where it is
                        }
                    }
                }
            }
        }

        public String toString() {
            String out = "";
            for (int i = 0; i < Feature.featureNames.length; i++) {
                out += Feature.featureNames[i] + "=" + strings[i] + ";";
            }
            return out;
        }
    }

    public static class Feature {
        public int featureNum;
        // WLOG assume numerical features are "less than"
        // and String features are "equal to"
        public String svalue;
        public double dvalue;
        public static String[] featureNames;
        public static boolean[] isNumerical = null;

        public Feature(int featureNum, String value) {
            this.featureNum = featureNum;
            this.svalue = value;
        }

        public Feature(int featureNum, double value) {
            this.featureNum = featureNum;
            this.dvalue = value;
        }

        public boolean apply(Example e) {
            if (Feature.isNumerical[featureNum]) {
                return (e.numericals[featureNum] < dvalue);
            } else {
                return (e.strings[featureNum].equals(svalue));
            }
        }

        public boolean equals(Object o) {
            if (!(o instanceof Feature)) {
                return false;
            }
            Feature otherFeature = (Feature) o;
            if (featureNum != otherFeature.featureNum) {
                return false;
            } else if (Feature.isNumerical[featureNum]) {
                if (Math.abs(dvalue - otherFeature.dvalue) < EPSILON) {
                    return true;
                }
                return false;
            } else {
                if (svalue.equals(otherFeature.svalue)) {
                    return true;
                }
                return false;
            }
        }

        public int hashCode() {
            return (featureNum + (svalue == null ? 0 : svalue.hashCode()) + (int) (dvalue * 10000));
        }

        public String toString() {
            if (Feature.isNumerical[featureNum]) {
                return Feature.featureNames[featureNum] + " < " + dvalue;
            } else {
                return Feature.featureNames[featureNum] + " = " + svalue;
            }
        }


    }

    public static HashSet<Feature> generateFeatures(ArrayList<Example> examples) {
        HashSet<Feature> featureSet = new HashSet<Feature>();

        for (Example e : examples) {
            for (int i = 0; i < e.strings.length; i++) {
                if (Feature.featureNames[i].equals("Target")) {
                    continue;
                }
                if (Feature.isNumerical[i]) {
                    featureSet.add(new Feature(i, e.numericals[i]));
                } else {
                    featureSet.add(new Feature(i, e.strings[i]));
                }
            }
        }
        return featureSet;
    }

    public static class ExampleSplit {
        public ArrayList<Example> positives;
        public ArrayList<Example> negatives;
        public Feature feature;
        public double newEntropy;  // not populated by constructor

        public ExampleSplit(Feature f, ArrayList<Example> examples) {
            feature = f;
            positives = new ArrayList<Example>();
            negatives = new ArrayList<Example>();
            for (Example e : examples) {
                if (f.apply(e)) {
                    positives.add(e);
                } else {
                    negatives.add(e);
                }
            }
        }

        public String toString() {
            String out = feature.toString();
            out += String.format(" (%.3f)", newEntropy);
            return out;
        }

        public double getChiValue() {
            // first character represents split feature, second character represents target
            // [yy yn ny nn]
            double[] observed = new double[4];
            for (Example e: positives) {
                if (e.target) {
                    observed[0]++;
                } else {
                    observed[1]++;
                }
            }
            
            for (Example e: negatives) {
                if (e.target) {
                    observed[2]++;
                } else {
                    observed[3]++;
                }
            }

            // calculate expected counts from the observed table
            double totalCount = positives.size() + negatives.size();
            double probTargetYes = (observed[0] + observed[2]) / totalCount;
            double probSplitYes = (observed[0] + observed[1]) / totalCount;

            double[] expected = new double[4];
            expected[0] = probSplitYes * probTargetYes * totalCount;
            expected[1] = probSplitYes * (1 - probTargetYes) * totalCount;
            expected[2] = (1 - probSplitYes) * probTargetYes * totalCount;
            expected[3] = (1 - probSplitYes) * (1 - probTargetYes) * totalCount;

            // find (O-E)^2/E for each square
            // EPISILON added to denom to prevent divide by zero
            double[] calculated = new double[4];
            calculated[0] = (observed[0] - expected[0]) * (observed[0] - expected[0]) / (expected[0] + EPSILON); 
            calculated[1] = (observed[1] - expected[1]) * (observed[1] - expected[1]) / (expected[1] + EPSILON);
            calculated[2] = (observed[2] - expected[2]) * (observed[2] - expected[2]) / (expected[2] + EPSILON);
            calculated[3] = (observed[3] - expected[3]) * (observed[3] - expected[3]) / (expected[3] + EPSILON);
            
            // chi value
            return calculated[0] + calculated[1] + calculated[2] + calculated[3];
        }

        public boolean shouldReturnMajority() {
            // no positive or no negative examples
            if (positives.size() == 0 || negatives.size() == 0) {
                return true;
            } else if (getChiValue() < CHI_THRESH && PRUNE) {
                // significant chi value if over the threshold -> meaning the two variables are dependent 
                // if PRUNE flag set to true and chi val insignificant
                return true;
            }
            return false;
        }
    }

    public static ExampleSplit GetBestSplit(HashSet<Feature> features, ArrayList<Example> examples) {
        ExampleSplit bestSplit = null;
        double lowestEntropy = Double.POSITIVE_INFINITY;

        // for every feature in the feature set
        for (Feature f : features) {
            // split the examples on this feature
            ExampleSplit tempSplit = new ExampleSplit(f, examples);

            // get count of examples in each branch
            double yesBranchCount = tempSplit.positives.size();
            double noBranchCount = tempSplit.negatives.size();
            double totalExampleCount = yesBranchCount + noBranchCount;

            // gets count of target true in each branch 
            double yesBranchTargetTrue = 0;
            double noBranchTargetTrue = 0;

            for (Example e : tempSplit.positives) {
                if (e.target) {
                    yesBranchTargetTrue += 1;
                }
            }

            for (Example e: tempSplit.negatives) {
                if (e.target) {
                    noBranchTargetTrue += 1;
                }
            }

            // Expected Entropy = Pr(N) * En(N) + Pr(Y) * En(Y)
            // Epsilon added to denom to prevent divide by zero errors
            tempSplit.newEntropy = 
                (yesBranchCount/totalExampleCount) * (probToEntropy(yesBranchTargetTrue/(yesBranchCount + EPSILON))) +
                (noBranchCount/totalExampleCount) * (probToEntropy(noBranchTargetTrue/(noBranchCount + EPSILON)));
            
            // if expected entropy of this split is best so far, remember it
            if (tempSplit.newEntropy < lowestEntropy) {
                lowestEntropy = tempSplit.newEntropy;
                bestSplit = tempSplit;
            }
        }
        return bestSplit;
    }

    static double probToEntropy(double p) {
        // change of base rule for log
        if (p == 0 || p == 1) {
            return 0;
        }
        return -1 * p * Math.log(p)/Math.log(2) + (p-1) * Math.log(1-p)/Math.log(2);
    }

    DecisionTree(ArrayList<Example> examples) {
        HashSet<Feature> features = generateFeatures(examples);
        ExampleSplit bestSplit = GetBestSplit(features, examples);

        boolean unanimous = false;
        int yesCount = 0;
        int noCount = 0;
        for (Example e: examples) {
            if (e.target) {
                yesCount++;
            } else {
                noCount++;
            }
        }
        if (yesCount == 0 || noCount == 0) {
            unanimous = true;
        }

        // if decision does not split the examples, no feature to split on, examples unanimous
        if (bestSplit == null || bestSplit.shouldReturnMajority() || unanimous) {
            // leaf node recommending the majority action of the examples
            if (yesCount > noCount) {
                this.decision = true;
            } else { // if equal return false
                this.decision = false;
            }
        } else {
            // recurse to build the tree down if split successful
            this.feature = bestSplit.feature;
            this.yesBranch = new DecisionTree(bestSplit.positives);
            this.noBranch = new DecisionTree(bestSplit.negatives);
        }
    }

    public static class Results {
        public int true_positive;
        public int true_negative;
        public int false_positive;
        public int false_negative;

        public Results() {
            true_positive = 0;
            true_negative = 0;
            false_positive = 0;
            false_negative = 0;
        }

        public String toString() {
            String out = "Precision: ";
            out += String.format("%.4f", true_positive/(double)(true_positive + false_positive));
            out += "\nRecall: " + String.format("%.4f",true_positive/(double)(true_positive + false_negative));
            out += "\n";
            out += "Accuracy: ";
            out += String.format("%.4f", (true_positive + true_negative)/(double)(true_positive + true_negative + false_positive + false_negative));
            out += "\n";
            return out;
        }
    }

    public Results classify(ArrayList<Example> examples) {
        System.err.println("Classifying examples...");
        Results results = new Results();
        for (Example e: examples) {
            DecisionTree node = this;

            // if it is not a leaf node (node.feature is only non-null for non-leaves)
            while (node.feature != null) {
                if (node.feature.apply(e)) {
                    node = node.yesBranch;
                } else {
                    node = node.noBranch;
                }
            }
            // Actual True
            if (e.target) {
                // Tree True
                if (node.decision) {
                    results.true_positive++;
                } else { // Tree False
                    results.false_negative++;
                }
            } else { // Actual False
                // Tree True
                if (node.decision) {
                    results.false_positive++;
                } else { // Tree False
                    results.true_negative++;
                }
            }
        }
        return results;
    }

    // recursive toString for decision trees - prints as if/then, indenting
    // with the depth argument
    public String toString() {
        return toString(0);
    }

    public String toString(int depth) {
        String out = "";
        for (int i = 0; i < depth; i++) {
            out += "    ";
        }
        if (feature == null) {
            out += (decision ? "YES" : "NO");
            out += "\n";
            return out;
        }
        out += "if " + feature + "\n";
        out += yesBranch.toString(depth+1);
        for (int i = 0; i < depth; i++) {
            out += "    ";
        }
        out += "else\n";
        out += noBranch.toString(depth+1);
        return out;
    }

}
