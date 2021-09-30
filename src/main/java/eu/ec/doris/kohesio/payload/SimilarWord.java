package eu.ec.doris.kohesio.payload;

public class SimilarWord {
    String word;
    double score;
    public SimilarWord(String word,double score){
        this.word = word;
        this.score = score;
    }

    public double getScore() {
        return score;
    }

    public String getWord() {
        return word;
    }
}
