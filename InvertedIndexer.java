import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InvertedIndexer {

    // Read one file and updates the index for the provided docId
    public static void processSingleFile(File file, int docId, Map<String, List<Posting>> index) throws IOException{
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;

        int positionCounter = 0; // Track absolute word positions

        while ((line = reader.readLine()) != null) {
            String[] terms = line.toLowerCase().split("[^a-z0-9]+");;

            for (String term : terms){
                if (term.isEmpty()) continue;

                positionCounter++;

                // If term is not already a key in the index, insert it with a new empty
                // ArrayList<Posting>
                index.computeIfAbsent(term, k -> new ArrayList<>());

                // Retrieve the postings list for this term
                List<Posting> postings = index.get(term); 
                // Check whether the term is an existing posting in the current document
                if (!postings.isEmpty() && postings.get(postings.size()-1).docId == docId){
                    Posting posting = postings.get(postings.size()-1);
                    posting.termFreq++;
                    posting.position.add(positionCounter);
                }
                else { 
                    // If the term does not exist in the current document, create a new posting object
                    Posting posting = new Posting(docId);
                    posting.termFreq = 1;
                    posting.position.add(positionCounter);

                    postings.add(posting);
                }
            }
        }
        reader.close();
    }

    // Build Inverted Index from all files in folderPath, returns a Map
    // mapping terms to posting lists
    public static Map<String, List<Posting>> buildInvertedIndex(String folderPath) throws IOException{
        Path folder = Paths.get(folderPath);
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new IOException("Folder not found or is not a directory: " + folderPath); 
        }

        // Create the index as a TreeMap that keeps keys (terms) sorted alphabetically
        Map<String, List<Posting>> index = new TreeMap<>();

        int docId = 0;

        // Recursively walk through all files in the directory tree
        try (Stream<Path> paths = Files.walk(folder)) {
            List<Path> fileList = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".txt"))
                .sorted() // Sort for consistent document ordering
                .collect(Collectors.toList());

            if (fileList.isEmpty()) {
                throw new IOException("No .txt files found in: " + folderPath);
            }

            for (Path filePath : fileList) {
                File file = filePath.toFile();
                docId++;
                processSingleFile(file, docId, index);
            }
        }
        return index;
    }

    // write the in-memory index to disk, split into numShards files under outputFolder
    public static void writeShardedIndex(Map<String, List<Posting>> index, String outputFolder, int numShards) throws IOException{
        // Create a File object for the output directory
        File folder = new File(outputFolder);

        // Create folder if it does not exist
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Create an array of BufferedWriter objects, one per shard file
        BufferedWriter[] shardWriters = new BufferedWriter[numShards];
        for (int i=0; i<numShards; i++){
            // Explicitly set UTF-8 when writing
            shardWriters[i] = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFolder + "/shard-" + i + ".txt"), "UTF-8"));
        }

        // Iterate over all terms in the index
        for (String term : index.keySet()){
            // Hash the term string, taking absolute value and mod by numShards
            int shardId = Math.abs(term.hashCode()) % numShards;
            BufferedWriter writer = shardWriters[shardId];
            writer.write(term + " ");
            List<Posting> postings = index.get(term);

            for (int i = 0; i < postings.size(); i++) {
                Posting p = postings.get(i);

                // add semicolon between doc postings
                if (i > 0)
                    writer.write(";");

                // convert positions list → 3,11,15,25
                StringBuilder posList = new StringBuilder();
                for (int j = 0; j < p.position.size(); j++) {
                    if (j > 0)
                        posList.append(",");
                    posList.append(p.position.get(j));
                }

                writer.write(p.docId + ":" + p.termFreq + ":" + posList);
            }
            writer.newLine();
        }
        for (BufferedWriter w : shardWriters) w.close();
    }

    /* ========================== Main Function ============================ */
    public static void main(String[] args) throws IOException {
        
        Map<String, List<Posting>> index = buildInvertedIndex("input-transform");
        // 3 is the number of shards to create
        writeShardedIndex(index, "inv-index", 3);
        
        System.out.println("Sharded inverted index created successfully!");

        
         String testDoc1 = "document describ market strategi carri compani agricultur chemic report predict market "
                +
                "share chemic report market statist agrochem pesticid herbicid fungicid insecticid fertil "
                +
                "predict sale market share stimul demand price cut volum sale";
        String testDoc2 = "document predict sale market share demand price cut";
        String[] docs = { testDoc1, testDoc2 };
        Map<String, List<Posting>> testIndex = new TreeMap<>();
        for (int docId = 1; docId <= docs.length; docId++) {
            String docText = docs[docId - 1];
            String[] tokens = docText.split("\\s+");

            for (int pos = 0; pos < tokens.length; pos++) {
                String term = tokens[pos].toLowerCase();

                testIndex.putIfAbsent(term, new ArrayList<>());
                List<Posting> postingList = testIndex.get(term);

                Posting posting = null;

                // Check if last posting is same document
                if (!postingList.isEmpty() && postingList.get(postingList.size() - 1).docId == docId) {
                    posting = postingList.get(postingList.size() - 1);
                } else {
                    posting = new Posting(docId);
                    postingList.add(posting);
                }

                posting.termFreq++;
                posting.position.add(pos + 1); // +1 for 1-based positions
            }
        }
        System.out.println("===== TEST INVERTED INDEX =====");
        for (Map.Entry<String, List<Posting>> entry : testIndex.entrySet()) {
            String term = entry.getKey();
            List<Posting> postings = entry.getValue();

            System.out.print(term + " ");

            for (int i = 0; i < postings.size(); i++) {
                Posting p = postings.get(i);
                if (i > 0)
                    System.out.print(";"); // separate documents

                // convert positions list -> "3,11,15"
                StringBuilder posList = new StringBuilder();
                for (int j = 0; j < p.position.size(); j++) {
                    if (j > 0) {
                        posList.append(",");
                    }
                    posList.append(p.position.get(j));
                }

                System.out.print(p.docId + ":" + p.termFreq + ":" + posList);
            }

            System.out.println();
        }

    }
}
