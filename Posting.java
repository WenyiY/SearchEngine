import java.util.ArrayList;
import java.util.List;

// Posting Object
public class Posting {
    public int docId; // Which document the term appears in
    public int termFreq; // How many times the term appears in that document
    public List<Integer> position; // The exact word positions in that document

    // Constructor
    Posting(int docId) {
        this.docId = docId;
        this.termFreq = 0;
        this.position = new ArrayList<>();
    }
}
