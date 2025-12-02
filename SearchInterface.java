import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchInterface {
    private Map<String, List<Posting>> index = new HashMap<>();
    private Map<Integer, String> docIdToFile = new HashMap<>(); // The actual path

    // Load a single shard file
    public void loadShard(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;

        while ((line = reader.readLine()) != null){
            // Splits line into term and posting string
            String[] parts = line.split(" ", 2);
            if (parts.length < 2) continue;
            String term = parts[0];
            String postingList = parts[1];

            List<Posting> postings = new ArrayList<>();
            // Splits postings string into individual document postings by ;
            String[] docParts = postingList.split(";");

            for (String docPart : docParts){
                String[] fields = docPart.split(":");
                int docId = Integer.parseInt(fields[0]);
                int termFreq = Integer.parseInt(fields[1]);
                // Creates a new Posting object with docId
                Posting posting = new Posting(docId);
                posting.termFreq = termFreq;

                String[] posTokens = fields[2].split(",");
                for (String pos : posTokens){
                    posting.position.add(Integer.parseInt(pos));
                }
                // Adds the completed Posting to the list for this term
                postings.add(posting);
            }
            // After processing all documents for a term, store the list in index
            index.put(term, postings);
        }
        reader.close();
    }

    // Build an in-memory index by reading all shards once
    public void loadIndex(String folderPath) throws IOException{
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.startsWith("shard-"));

        if (files == null){
            throw new IOException("File does not found.");
        }

        for (File file : files){
            loadShard(file);
        }

        System.out.println("Index loaded, terms: " + index.size());
    }

    // Converts a posting list to a map for quick lookup by docId
    private Map<Integer, Posting> toPostingMap(List<Posting> postings){
        Map<Integer, Posting> map = new HashMap<>();
        for (Posting p : postings){
            map.put(p.docId, p);
        }
        return map;
    }

    // Computes shortest distance between two sorted position list
    // Using two pointers to achieve O(n)
    private int shortestDistance(List<Integer> a, List<Integer> b){
        int parserA = 0;
        int parserB = 0;
        int minDistance = Integer.MAX_VALUE;
        while (parserA < a.size() && parserB < b.size()) {
            int posA = a.get(parserA);
            int posB = b.get(parserB);
            int diff = Math.abs(posA - posB);
            minDistance = Math.min(minDistance, diff);

            if (posA < posB) parserA++;
            else parserB++;
        }
        return (minDistance == Integer.MAX_VALUE) ? -1 : minDistance;
    }

    // Term-at-a-time retrieval to score documents
    private Map<Integer, Double> scoreDocuments(List<String> queryTerms){
        // accumulates all scoring
        Map<Integer, Double> docScores = new HashMap<>();

        // keeps references to posting lists for each query term
        List<List<Posting>> postingLists = new ArrayList<>();

        for (String term : queryTerms) {
            List<Posting> postings = index.get(term);
            if (postings != null) {
                postingLists.add(postings);

                // update scores term by term, iterate all docs for this term and increment
                // their score by termFreq
                for (Posting p : postings) {
                    docScores.put(p.docId, docScores.getOrDefault(p.docId, 0.0) + p.termFreq);
                }
            }
        }

        // Score_positions(d, q), Proximity Scoring
        for (int i=0; i<queryTerms.size()-1; i++){
            List<Posting> listA = index.get(queryTerms.get(i));
            List<Posting> listB = index.get(queryTerms.get(i + 1));
            if (listA == null || listB == null) continue;

            Map<Integer, Posting> mapA = toPostingMap(listA);
            Map<Integer, Posting> mapB = toPostingMap(listB);

            for (int docId : docScores.keySet()) {
                if (mapA.containsKey(docId) && mapB.containsKey(docId)) {
                    int shortest = shortestDistance(mapA.get(docId).position,
                            mapB.get(docId).position);
                    
                    // Increment score by 1 / shortestDistance
                    if (shortest > 0) {
                        docScores.put(docId, docScores.get(docId) + (1.0 / shortest));
                    }
                }
            }
        }
        return docScores;
    }

    // Loops infinitely to accept queries until user types "exit"
    public void startInteractiveSearch(){
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Q> ");
            // skips empty lines
            String line = scanner.nextLine();
            if (line.equalsIgnoreCase("exit")) break;

            // Process query terms
            String processedLine = TextTransformer.transformLine(line);
            // Split into individual stemmed tokens
            String[] stemmedTokens = processedLine.isEmpty()
                    ? new String[0]
                    : processedLine.split("\\s+");
            
            // If after stopwords + stemming the query is empty:
            if (stemmedTokens.length == 0) {
                System.out.println("Query contains no valid terms after processing.");
                continue;
            }

            System.out.println("Processed query terms: " + Arrays.toString(stemmedTokens));

            List<String> queryTerms = new ArrayList<>();
            // Convert to a list of query terms
            for (String t : stemmedTokens){
                if (!t.isEmpty()) {
                    queryTerms.add(t);
                }
            }
            // Calls term-at-a-time scoring function to compute document scores
            Map<Integer, Double> scores = scoreDocuments(queryTerms);

            // Convert docScores map to a list of entries
            List<Map.Entry<Integer, Double>> ranked = new ArrayList<>(scores.entrySet());
            // Sort by descending score to get top documents
            ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            // Print the top 10 results with rank, docId, and score
            System.out.println("Top 10 results: ");
            int top10 = Math.min(10, ranked.size());
            
            for (int i = 0; i < top10; i++) {
                int docId = ranked.get(i).getKey();
                String filePath = docIdToFile.getOrDefault(docId, "doc " + docId);
                System.out.printf(
                        "%2d. %s (score %.4f)\n",
                        i + 1,
                        filePath,
                        ranked.get(i).getValue());
            }
        }
        scanner.close();
    }

    // Mapping doc IDs to file paths
    public void loadDocIdMap(String folderPath) throws IOException {
        Path folder = Paths.get(folderPath);

        // Recursively collect all .txt files
        List<Path> fileList;
        try (Stream<Path> paths = Files.walk(folder)) {
            fileList = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .sorted() // match InvertedIndexer sorting
                    .collect(Collectors.toList());
        }

        int docId = 1;
        for (Path p : fileList) {
            // Make path relative to the input-transform folder
            Path relative = folder.relativize(p);
            docIdToFile.put(docId, relative.toString());
            docId++;
        }
    }

    /* ========================= Main Function ========================== */
    public static void main(String[] args) throws IOException {
        TextTransformer.loadStopWords();
        SearchInterface search = new SearchInterface();
        search.loadIndex("inv-index");
        search.loadDocIdMap("input-transform");
        search.startInteractiveSearch();
    }
}
