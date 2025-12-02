import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CosineSearchInterface {
    private Map<String, List<Posting>> index = new HashMap<>(); // inverted index
    private Map<Integer, String> docIdToFile = new HashMap<>(); // The actual path

    // Load a single shard file
    public void loadShard(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;

        while ((line = reader.readLine()) != null) {
            // Splits line into term and posting string
            String[] parts = line.split(" ", 2);
            if (parts.length < 2)
                continue;
            String term = parts[0];
            String postingList = parts[1];

            List<Posting> postings = new ArrayList<>();
            // Splits postings string into individual document postings by ;
            String[] docParts = postingList.split(";");

            for (String docPart : docParts) {
                String[] fields = docPart.split(":");
                int docId = Integer.parseInt(fields[0]);
                int termFreq = Integer.parseInt(fields[1]);
                // Creates a new Posting object with docId
                Posting posting = new Posting(docId);
                posting.termFreq = termFreq;

                String[] posTokens = fields[2].split(",");
                for (String pos : posTokens) {
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
    public void loadIndex(String folderPath) throws IOException {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.startsWith("shard-"));

        if (files == null) {
            throw new IOException("File does not found.");
        }

        for (File file : files) {
            loadShard(file);
        }

        System.out.println("Index loaded, terms: " + index.size());
    }

    // Converts a posting list to a map for quick lookup by docId
    private Map<Integer, Posting> toPostingMap(List<Posting> postings) {
        Map<Integer, Posting> map = new HashMap<>();
        for (Posting p : postings) {
            map.put(p.docId, p);
        }
        return map;
    }

    // Computes shortest distance between two sorted position list
    // Using two pointers to achieve O(n)
    private int shortestDistance(List<Integer> a, List<Integer> b) {
        int parserA = 0;
        int parserB = 0;
        int minDistance = Integer.MAX_VALUE;
        while (parserA < a.size() && parserB < b.size()) {
            int posA = a.get(parserA);
            int posB = b.get(parserB);
            int diff = Math.abs(posA - posB);
            minDistance = Math.min(minDistance, diff);

            if (posA < posB)
                parserA++;
            else
                parserB++;
        }
        return (minDistance == Integer.MAX_VALUE) ? -1 : minDistance;
    }

    // Compute cosine similarity between a document and the query
    private double computeCosine(Map<String, Double> docW, Map<String, Double> queryW) {
        double dot = 0.0; // Dot product between the document vector and the query vector
        // sum of squares of document/query weights
        double docNorm = 0.0; 
        double queryNorm = 0.0;

        // Compute dot product of vectors
        for (String term : queryW.keySet()) {
            double docWeight = docW.getOrDefault(term, 0.0);
            double queryWeight = queryW.get(term);
            dot += docWeight * queryWeight; // product of document and query term weights
        }

        // squared weights
        for (double w : docW.values()){
            docNorm += w * w;
        }
        for (double w : queryW.values()){
            queryNorm += w * w;
        }

        if (docNorm == 0 || queryNorm == 0)
            return 0.0;
        return dot / (Math.sqrt(docNorm) * Math.sqrt(queryNorm)); // cosine similarity
    }

    // Normalized position scoring
    private double computePositionScore(List<String> queryTerms, int docId) {
        double posScore = 0.0; // accumulate proximity scores for term pairs in the query
        for (int i = 0; i < queryTerms.size() - 1; i++) {
            List<Posting> listA = index.get(queryTerms.get(i));
            List<Posting> listB = index.get(queryTerms.get(i + 1));
            if (listA == null || listB == null)
                continue;

            Map<Integer, Posting> mapA = toPostingMap(listA);
            Map<Integer, Posting> mapB = toPostingMap(listB);

            if (mapA.containsKey(docId) && mapB.containsKey(docId)) {
                int shortest = shortestDistance(mapA.get(docId).position, mapB.get(docId).position);
                if (shortest != 0) {
                    posScore += 1.0 / shortest;
                }
            }
        }
        return queryTerms.size() > 1 ? posScore / (queryTerms.size() - 1) : 0.0;
    }

    // Term-at-a-time retrieval to score documents using cosine similarity, tf-idf for weighting
    private Map<Integer, Double> scoreDocuments(List<String> queryTerms) {
        // Accumulates all scoring
        Map<Integer, Double> docScores = new HashMap<>();
        // Store TF-IDF weights for each term for each document. docId -> (term -> w_{t,d})
        Map<Integer, Map<String, Double>> docWeights = new HashMap<>();
        // Compute idf=log(docNum/df) and doc TF-IDF weights for query terms
        Map<String, Double> idfMap = new HashMap<>();

        int docNum = docIdToFile.size(); // total number of documents

        /* ============= Compute IDF for each query term =============== */
        for (String term : queryTerms) {
            // Handle cases where the term is not in the index at all
            if (!index.containsKey(term)) continue;

            List<Posting> postings = index.get(term);
            int df = postings != null ? postings.size() : 0; // number of documents contain the term
            double idf = Math.log10((double) docNum / df); // idf=log(docNum/df)
            idfMap.put(term, idf);

            // Fill docWeights for cosine calculation
            if (postings != null) {
                for (Posting p : postings) {
                    // Log-weighted TF, tf = (log(fik) + 1)
                    double tf = 1 + Math.log10(p.termFreq); // reduces the impact of massive repetition
                    double weight = tf * idf; // tf-idf weight for doc

                    // Store weight for each term
                    docWeights.computeIfAbsent(p.docId, k -> new HashMap<>())
                            .put(term, weight);
                }
            }
        }

        /*======== Compute query weights (tf-idf) ==============*/
        Map<String, Double> queryWeights = new HashMap<>();
        // Count raw frequency of each term in the query, query's tf
        Map<String, Integer> queryFreq = new HashMap<>();
        for (String term : queryTerms)
            queryFreq.put(term, queryFreq.getOrDefault(term, 0) + 1);
        for (String term : queryFreq.keySet()) {
            double tf = 1 + Math.log10(queryFreq.get(term)); // normalized tf for query
            double idf = idfMap.getOrDefault(term, 0.0); // idf=log(docNum/df)
            queryWeights.put(term, tf * idf); // tf-idf weight for query
        }

        // Compute cosine similarity for each document
        for (int docId : docWeights.keySet()) {
            double cosineScore = computeCosine(docWeights.get(docId), queryWeights);
            double positionScore = computePositionScore(queryTerms, docId);
            docScores.put(docId, cosineScore + positionScore);
        }

        return docScores;       
    }

    // Loops infinitely to accept queries until user types "exit"
    public void startInteractiveSearch() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Q> ");
            // skips empty lines
            String line = scanner.nextLine();
            if (line.equalsIgnoreCase("exit"))
                break;

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
            for (String t : stemmedTokens) {
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
        CosineSearchInterface search = new CosineSearchInterface();
        search.loadIndex("inv-index");
        search.loadDocIdMap("input-transform");
        search.startInteractiveSearch();
    }
}
