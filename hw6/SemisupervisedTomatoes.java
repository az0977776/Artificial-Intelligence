import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.lang.Math;

// Semisupervised Tomatoes:
// EM some Naive Bayes and Markov Models to do sentiment analysis.
// Based on solution code for Assignment 3.
//
// Input from train.tsv.zip at 
// https://www.kaggle.com/c/sentiment-analysis-on-movie-reviews
//
// itself gathered from Rotten Tomatoes.
//
// Format is PhraseID[unused]   SentenceID  Sentence[tokenized]
//
// Just a few sentiment labels this time - this is semisupervised.
//
// We'll only use the first line for each SentenceID, since the others are
// micro-analyzed phrases that would just mess up our counts.
//
// After training, we'll identify the top words for each cluster by
// Pr(cluster | word) - the words that are much more likely in the cluster
// than in the general population - and categorize the new utterances.

public class SemisupervisedTomatoes {

    public static final int CLASSES = 2;
    // Assume sentence numbering starts with this number in the file
    public static final int FIRST_SENTENCE_NUM = 1;
    
    // Probability of either a unigram or bigram that hasn't been seen
    // Gotta make this real generous if we're not using logs
    public static final double OUT_OF_VOCAB_PROB = 0.000001;

    // Words to print per class
    public static final int TOP_N = 10;
    // Times (in expectation) that we need to see a word in a cluster
    // before we think it's meaningful enough to print in the summary
    public static final double MIN_TO_PRINT = 15.0;

    public static boolean USE_UNIFORM_PRIOR = false;
    public static boolean SEMISUPERVISED = true;
    public static boolean FIXED_SEED = false;

    public static final int ITERATIONS = 200;

    // We may play with this in the assignment, but it's good to have common
    // ground to talk about
    public static Random rng = (FIXED_SEED? new Random(2019) : new Random());

    public static NaiveBayesModel nbModel;

    public static class NaiveBayesModel {
        public double[] classCounts;
        public double[] totalWords;
        public ArrayList<HashMap<String, Double>> wordCounts;

        public NaiveBayesModel() {
            classCounts = new double[CLASSES];
            totalWords = new double[CLASSES];
            wordCounts = new ArrayList<HashMap<String, Double>>();
            for (int i = 0; i < CLASSES; i++) {
                wordCounts.add(new HashMap<String, Double>());
            }
        }

        // Update the model given a sentence and its probability of
        // belonging to each class
        void update(String sentence, ArrayList<Double> probs) {

            String[] tokens = sentence.split(" ");
            for (int c = 0; c < CLASSES; c++) {
                // update classCounts
                classCounts[c] += probs.get(c);

                // update totalWords
                totalWords[c] += probs.get(c) * tokens.length;
 
                // update wordCounts
                HashMap<String, Double> classWordCounts = wordCounts.get(c);
                for (int t = 0; t < tokens.length; t++) {
                    if (classWordCounts.containsKey(tokens[t])) {
                        classWordCounts.put(tokens[t], classWordCounts.get(tokens[t]) + probs.get(c));
                    } else {
                        // something new
                        classWordCounts.put(tokens[t], probs.get(c));
                    }
                }
            }
        }

        // Classify a new sentence using the data and a Naive Bayes model.
        // Assume every token in the sentence is space-delimited, as the input
        // was.  Return a list of class probabilities.
        public ArrayList<Double> classify(String sentence) {
            ArrayList<Double> sentProbs = new ArrayList<>();
            String[] tokens = sentence.split(" ");
            
            Double totalClassCounts = 0.0;
            for (int c = 0; c < CLASSES; c++) {
                totalClassCounts += classCounts[c];
            }

            // find the probabilty the sentence is each class
            for (int c = 0; c < CLASSES; c++) {
                // prior for the class
                double prob = classCounts[c] / totalClassCounts;

                HashMap<String, Double> classWordCounts = wordCounts.get(c);

                // Pr(word | class) product, naive bayes
                for (int t = 0; t < tokens.length; t++) {
                    if (classWordCounts.containsKey(tokens[t])) {
                        prob *= classWordCounts.get(tokens[t]) / totalWords[c];
                    } else {
                        // when the word has never been seen before in this class
                        prob *= OUT_OF_VOCAB_PROB;
                    }
                }

                // we do not want 0s here
                if (prob == 0) {
                    prob = Double.MIN_NORMAL;
                }

                sentProbs.add(prob);
            }

            // sum of all class probabilities for the sentence
            Double probSum = 0.0;
            for (Double d: sentProbs) {
                probSum += d;
            }

            // divide all probabilities by the sum to normalize
            for (int i = 0; i < sentProbs.size(); i++) {
                sentProbs.set(i, sentProbs.get(i) / probSum);
            }

            return sentProbs;
        }

        // printTopWords: Print five words with the highest
        // Pr(thisClass | word) = scale Pr(word | thisClass)Pr(thisClass)
        // but skip those that have appeared (in expectation) less than 
        // MIN_TO_PRINT times for this class (to avoid random weird words
        // that only show up once in any sentence)
        void printTopWords(int n) {
            for (int c = 0; c < CLASSES; c++) {
                System.out.println("Cluster " + c + ":");
                ArrayList<WordProb> wordProbs = new ArrayList<WordProb>();
                for (String w : wordCounts.get(c).keySet()) {
                    if (wordCounts.get(c).get(w) >= MIN_TO_PRINT) {
                        // Treating a word as a one-word sentence lets us use
                        // our existing model
                        ArrayList<Double> probs = nbModel.classify(w);
                        wordProbs.add(new WordProb(w, probs.get(c)));
                    }
                }
                Collections.sort(wordProbs);
                for (int i = 0; i < n; i++) {
                    if (i >= wordProbs.size()) {
                        System.out.println("No more words...");
                        break;
                    }
                    System.out.println(wordProbs.get(i).word);
                }
            }
        }
    }

    public static void main(String[] args) {
        Scanner myScanner = new Scanner(System.in);
        ArrayList<String> sentences = getTrainingData(myScanner);
        trainModels(sentences);
        nbModel.printTopWords(TOP_N);
        classifySentences(myScanner);
    }

    public static ArrayList<String> getTrainingData(Scanner sc) {
        int nextFresh = FIRST_SENTENCE_NUM;
        ArrayList<String> sentences = new ArrayList<String>();
        while(sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.startsWith("---")) {
                return sentences;
            }
            // Data should be filtered now, so just add it
            sentences.add(line);
        }
        return sentences;
    }

    static void trainModels(ArrayList<String> sentences) {
        // We'll start by assigning the sentences to random classes.
        // 1.0 for the random class, 0.0 for everything else
        System.err.println("Initializing models....");
        HashMap<String,ArrayList<Double>> naiveClasses = randomInit(sentences);

        // gets rid of the smiley labels
        for (int i = 0; i < sentences.size(); i++) {
            if (sentences.get(i).startsWith(":)") || sentences.get(i).startsWith(":(")) {
                sentences.set(i, sentences.get(i).substring(3));
            }
        }

        // initial priors
        nbModel = new NaiveBayesModel();
        for (String s: sentences) {
            nbModel.update(s, naiveClasses.get(s));
        }

        for (int i = 0; i < ITERATIONS; i++) {
            System.err.println("EM round " + i);

            // classify (E-Step)
            for (String s: sentences) {
                naiveClasses.put(s, nbModel.classify(s));
            }

            // clear old counts and gets new ones based on new classification
            nbModel.classCounts = new double[CLASSES];
            nbModel.totalWords = new double[CLASSES];
            nbModel.wordCounts = new ArrayList<HashMap<String, Double>>();
            for (int c = 0; c < CLASSES; c++) {
                nbModel.wordCounts.add(new HashMap<String, Double>());
            }

            // update (M-Step)
            for (String s: sentences) {
                nbModel.update(s, naiveClasses.get(s));
            }
        }
    }

    static HashMap<String,ArrayList<Double>> randomInit(ArrayList<String> sents) {
        HashMap<String,ArrayList<Double>> counts = new HashMap<String,ArrayList<Double>>();
        for (String sent : sents) {
            ArrayList<Double> probs = new ArrayList<Double>();
            if (SEMISUPERVISED && sent.startsWith(":)")) {
                // Class 1 = positive
                probs.add(0.0);
                probs.add(1.0);
                for (int i = 2; i < CLASSES; i++) {
                    probs.add(0.0);
                }
            } else if (SEMISUPERVISED && sent.startsWith(":(")) {
                // Class 0 = negative
                probs.add(1.0);
                probs.add(0.0);
                for (int i = 2; i < CLASSES; i++) {
                    probs.add(0.0);
                }
            } else {
                double baseline = 1.0/CLASSES;
                // Slight deviation to break symmetry
                int randomBumpedClass = rng.nextInt(CLASSES);
                double bump = (1.0/CLASSES * 0.25);
                if (SEMISUPERVISED) {
                    // Symmetry breaking not necessary, already got it
                    // from labeled examples
                    bump = 0.0;
                }
                for (int i = 0; i < CLASSES; i++) {
                    if (i == randomBumpedClass) {
                        probs.add(baseline + bump);
                    } else {
                        probs.add(baseline - bump/(CLASSES-1));
                    }
                }
            }

            // gets rid of smiley faces
            if (sent.startsWith(":)") || sent.startsWith(":(")) {
                sent = sent.substring(3);
            }

            counts.put(sent, probs);
        }
        return counts;
    }

    public static class WordProb implements Comparable<WordProb> {
        public String word;
        public Double prob;

        public WordProb(String w, Double p) {
            word = w;
            prob = p;
        }

        public int compareTo(WordProb wp) {
            // Reverse order
            if (this.prob > wp.prob) {
                return -1;
            } else if (this.prob < wp.prob) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public static void classifySentences(Scanner scan) {
        while(scan.hasNextLine()) {
            String line = scan.nextLine();
            System.out.print(line + ":");
            ArrayList<Double> probs = nbModel.classify(line);
            for (int c = 0; c < CLASSES; c++) {
                System.out.print(probs.get(c) + " ");
            }
            System.out.println();
        }
    }

}
