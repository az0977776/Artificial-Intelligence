import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.lang.Math;

// Bayesian Tomatoes:
// Doing some Naive Bayes and Markov Models to do basic sentiment analysis.
//
// Input from train.tsv.zip at
// https://www.kaggle.com/c/sentiment-analysis-on-movie-reviews
//
// itself gathered from Rotten Tomatoes.
//
// Format is PhraseID[unused]   SentenceID  Sentence[tokenized] Sentiment
//
// We'll only use the first line for each SentenceID, since the others are
// micro-analyzed phrases that would just mess up our counts.
//
// Sentiment is on a 5-point scale:
// 0 - negative
// 1 - somewhat negative
// 2 - neutral
// 3 - somewhat positive
// 4 - positive
//
// For each kind of model, we'll build one model per sentiment category.
// Following Bayesian logic, base rates matter for each category; if critics
// are often negative, that should be a good guess in the absence of other
// information.
//
// To play well with HackerRank, input is assumed to be the train.tsv
// format of training data until we encounter a line that starts with "---".
// All remaining lines, which should be just space-delimited words/tokens
// in a sentence, are assumed to be test data.
// Output is the following on four lines for each line of test data:
//
// Naive Bayes classification (0-4)
// Naive Bayes most likely class's log probability (with default double digits/precision)
// Markov Model classification (0-4)
// Markov Model most likely class's log probability

public class BayesianTomatoes {

    public static final int CLASSES = 5;
    // Assume sentence numbering starts with this number in the file
    public static final int FIRST_SENTENCE_NUM = 1;
    // Probability of either a unigram or bigram that hasn't been seen -
    // needs to be small enough that it's "practically a rounding error"
    public static final double OUT_OF_VOCAB_PROB = 0.0000000001;

    // Classifications are what your Naive Bayes methods should return.
    // Their toString() method is what's invoked for the program output.
    public static class Classification {
        public int rating;       // the maximum likelihood classification
        public double logProb;   // the log likelihood of that classification

        public Classification(int c, double lp) {
            rating = c;
            logProb = lp;
        }

        public String toString() {
            return String.format("%d\n%.5f\n", rating, logProb);
        }
    }

    // ModelInfo carries around all the count information that we can use to
    // estimate probabilities.
    public static class ModelInfo {
        // Word counts for each sentiment label
        public ArrayList<HashMap<String, Integer>> wordCounts;
        // Bigram counts for each sentiment label, with key a single string
        // separating the words with a space
        public ArrayList<HashMap<String, Integer>> bigramCounts;
        // Overall sentence sentiment counts for taking the prior into account
        // (one is incremented once per sentence)
        public int[] sentimentCounts;

        // A subtle point:  if a word is at the end of the sentence, it's not
        // the beginning of any bigram.  So we need to keep separate track of
        // the number of times a word starts any bigram (ie is not the last word)
        public ArrayList<HashMap<String, Integer>> bigramDenoms;

        public int[] totalWords;
        public int[] totalBigrams;

        ModelInfo() {
            sentimentCounts = new int[CLASSES];
            totalWords = new int[CLASSES];
            totalBigrams = new int[CLASSES];
            wordCounts = new ArrayList<HashMap<String, Integer>>();
            bigramCounts = new ArrayList<HashMap<String, Integer>>();
            bigramDenoms = new ArrayList<HashMap<String, Integer>>();
            for (int i = 0; i < CLASSES; i++) {
                wordCounts.add(new HashMap<String, Integer>());
                bigramCounts.add(new HashMap<String, Integer>());
                bigramDenoms.add(new HashMap<String, Integer>());
            }
        }

        // updateWordCounts:  assume space-delimited words/tokens.
        // Note that "real" natural language processing will typically
        // clean this up a bit better, removing punctuation and perhaps
        // trying to collapse different word forms like "trying" versus "tried."
        // But this will work well enough for us here.
        public void updateWordCounts(String sentence, int sentiment) {
            HashMap<String, Integer> sWordCounts = wordCounts.get(sentiment);
            HashMap<String, Integer> sBigramCounts = bigramCounts.get(sentiment);
            HashMap<String, Integer> sBigramDenoms = bigramDenoms.get(sentiment);
            String[] tokenized = sentence.split(" ");
            for (int i = 0; i < tokenized.length; i++) {
                totalWords[sentiment]++;
                String standardized = tokenized[i].toLowerCase();
                if (sWordCounts.containsKey(standardized)) {
                    sWordCounts.put(standardized, sWordCounts.get(standardized)+1);
                } else {
                    sWordCounts.put(standardized, 1);
                }
                if (i > 0) {
                    String bigram = (tokenized[i-1] + " " + tokenized[i]).toLowerCase();
                    if (sBigramCounts.containsKey(bigram)) {
                        sBigramCounts.put(bigram, sBigramCounts.get(bigram) + 1);
                    } else {
                        sBigramCounts.put(bigram, 1);
                    }

                    String standardizedPrev = tokenized[i-1].toLowerCase();
                    if (sBigramDenoms.containsKey(standardizedPrev)) {
                        sBigramDenoms.put(standardizedPrev, sBigramDenoms.get(standardizedPrev) + 1);
                    } else {
                        sBigramDenoms.put(standardizedPrev, 1);
                    }
                    totalBigrams[sentiment]++;
                }
            }
        }
    }

    public static void main(String[] args) {
        Scanner myScanner = new Scanner(System.in);
        ModelInfo info = getModels(myScanner);
        classifySentences(info, myScanner);
    }

    public static ModelInfo getModels(Scanner sc) {
        int nextFresh = FIRST_SENTENCE_NUM;
        ModelInfo info = new ModelInfo();
        while(sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.startsWith("---")) {
                return info;
            }
            String[] fields = line.split("\t");
            try {
                Integer sentenceNum = Integer.parseInt(fields[1]);
                if (sentenceNum != nextFresh) {
                    continue;
                }
                nextFresh++;
                Integer sentiment = Integer.parseInt(fields[3]);
                info.sentimentCounts[sentiment]++;
                info.updateWordCounts(fields[2], sentiment);
            } catch (Exception e) {
                // We probably just read the header of the file.
                // Or some other junk.  Ignore.
            }
        }
        return info;
    }

    // Assume test data consists of just space-delimited words in sentence
    public static void classifySentences(ModelInfo info, Scanner sc) {
        while(sc.hasNextLine()) {
            String line = sc.nextLine();
            Classification nbClass = naiveBayesClassify(info, line);
            Classification mmClass = markovModelClassify(info, line);
            System.out.print(nbClass.toString() + mmClass.toString());
        }
    }

    // Classify a new sentence using the data and a Naive Bayes model.
    // Assume every token in the sentence is space-delimited, as the input
    // was.  You'll need to compute probabilities using the globals
    // defined at the top of the file (wordCounts etc.), perform a naive Bayes
    // computation, and return a Classification containing both a decision
    // and a log likelihood for that decision (not rescaled, since we don't
    // need actual probabilities that sum to 1 to make a decision about which is
    // biggest).
    public static Classification naiveBayesClassify(ModelInfo info, String sentence) {
        // probabilty of sentence being each sentiment
        double[] probabilities = new double[CLASSES];
        String[] tokenized = sentence.toLowerCase().split(" ");
        
        // sum of count of all sentiments
        int totalSentsAll = 0;
        for (int i : info.sentimentCounts) {
            totalSentsAll+= i;
        }

        // need to find probability for each class
        for (int i = 0; i < CLASSES; i++) {
            double prob = Math.log(info.sentimentCounts[i] * 1.0 / totalSentsAll);

            // prob for each word given the sentiment
            for (int j = 0; j < tokenized.length; j++) {
                if (info.wordCounts.get(i).containsKey(tokenized[j])) {
                    prob += Math.log(info.wordCounts.get(i).get(tokenized[j]) * 1.0 / info.totalWords[i]);
                } else {
                    prob += Math.log(OUT_OF_VOCAB_PROB);
                }
            }

            probabilities[i] = prob;
        }

        // find the sentiment with the highest probabilty for this sentence
        int max_idx = 0;
        double max_prob = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < CLASSES; i++) {
            if (probabilities[i] > max_prob) {
                max_idx = i;
                max_prob = probabilities[i];
            }
        }

        return new Classification(max_idx, max_prob);
    }

    // Like naiveBayesClassify, but each word is conditionally dependent
    // on the preceding word.  As with naiveBayesClassify, you'll need to
    // decide what information near the top of the file is actually relevant
    // and compute probabilities from that.
    public static Classification markovModelClassify(ModelInfo info, String sentence) {
        // probabilty of sentence being each sentiment
        double[] probabilities = new double[CLASSES];
        String[] tokenized = sentence.toLowerCase().split(" ");

        // sum of count of all sentiments
        int totalSentsAll = 0;
        for (int i : info.sentimentCounts) {
            totalSentsAll+= i;
        }

        // need to find probability for each class
        for (int i = 0; i < CLASSES; i++) {
            double prob = Math.log(info.sentimentCounts[i] * 1.0 / totalSentsAll);

            // prob for each word given the sentiment
            for (int j = 0; j < tokenized.length; j++) {
                
                // for bigrams, not including the first single word
                if (j > 0) {
                    String bigram = (tokenized[j-1] + " " + tokenized[j]).toLowerCase();

                    if (info.bigramCounts.get(i).containsKey(bigram)) {
                        prob += Math.log(info.bigramCounts.get(i).get(bigram) * 1.0 / info.bigramDenoms.get(i).get(tokenized[j-1]));
                    } else {
                        prob += Math.log(OUT_OF_VOCAB_PROB);
                    }
                } else { // for the first single word, just use single word probabilty as we did for naive bayes
                    if (info.wordCounts.get(i).containsKey(tokenized[j])) {
                        prob += Math.log(info.wordCounts.get(i).get(tokenized[j]) * 1.0 / info.totalWords[i]);
                    } else {
                        prob += Math.log(OUT_OF_VOCAB_PROB);
                    }
                }
            }

            probabilities[i] = prob;
        }

        // find the sentiment with the highest probabilty for this sentence
        int max_idx = 0;
        double max_prob = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < CLASSES; i++) {
            if (probabilities[i] > max_prob) {
                max_idx = i;
                max_prob = probabilities[i];
            }
        }

        return new Classification(max_idx, max_prob);
    }
}
