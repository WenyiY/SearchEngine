import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TextTransformer {

    // Configuration Paths
    public static final String INPUT_DIR = "input-files";
    public static final String OUTPUT_DIR = "input-transform";
    public static final String STOPWORDS_FILE = "stopwords.txt";

    public static Set<String> stopWords;

    /**
     * Loads stop words from the text file into a HashSet for O(1) lookups.
     */
    public static void loadStopWords() throws IOException {
        stopWords = new HashSet<>();
        Path path = Paths.get(STOPWORDS_FILE);
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String clean = line.trim().toLowerCase();
                if (!clean.isEmpty()) {
                    stopWords.add(clean);
                }
            }
        } else {
            System.err.println("Cannot find stopwords.txt.");
        }
    }

    /**
     * Processes a single Zip file: Unzips, transforms text, and writes to output.
     * Opens the zip, determines the correct output path in the input-transform directory, 
     * and creates any necessary parent directories using Files.createDirectories(). 
     * It then reads the content of the text file inside the zip and 
     * calls transformLine() on each line before writing the final result to the new .txt file.
     */
    public static void processZipFile(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            // Mirror the folder structure in the output directory
            Path relativePath = Paths.get(INPUT_DIR).relativize(zipPath);
            Path outputPath = Paths.get(OUTPUT_DIR).resolve(relativePath.getParent());

            // Change extension from .zip to .txt for the output filename
            String fileName = zipPath.getFileName().toString().replace(".zip", ".txt");
            Path outputFile = outputPath.resolve(fileName);

            // Create directories if they don't exist
            Files.createDirectories(outputPath);

            // Process entries (assuming one text file per zip as per instructions)
            Enumeration<? extends ZipEntry> entries = zip.entries();
            StringBuilder transformedContent = new StringBuilder();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(zip.getInputStream(entry), StandardCharsets.US_ASCII))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            String transformedLine = transformLine(line);
                            if (!transformedLine.isEmpty()) {
                                transformedContent.append(transformedLine).append(" ");
                            }
                        }
                    }
                }
            }

            // Write result to file
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
                writer.write(transformedContent.toString().trim());
            }

            System.out.println("Processed: " + zipPath);

        } catch (IOException e) {
            System.err.println("Error processing file: " + zipPath);
            e.printStackTrace();
        }
    }

    /**
     * Transform the text here.
     * First do the tokenization, then filter stop word, last use Porter Stemmer
     */
    public static String transformLine(String line) {
        // Tokenize: Split by non-alphanumeric characters
        String[] tokens = line.split("[^a-zA-Z0-9]+");

        StringBuilder sb = new StringBuilder();
        // Instantiate the external PorterStemmer class
        PorterStemmer stemmer = new PorterStemmer();

        for (String token : tokens) {
            // Make the text lowercase
            String lower = token.toLowerCase();

            // Filter out empty tokens
            if (lower.isEmpty())
                continue;

            // Filter out single characters
            if (lower.length() < 2)
                continue;

            // Filter stop words
            if (stopWords.contains(lower))
                continue;

            // Use Porter Stemming
            stemmer.add(lower.toCharArray(), lower.length());
            stemmer.stem();
            String stemmed = stemmer.toString();

            sb.append(stemmed).append(" ");
        }
        return sb.toString().trim();
    }

    /* ============================ Main Function ========================== */
    /*
     * Call loadStopWords(), uses Files.walk() to recursively find all .zip
     * files in the input-files directory, and calls processZipFile() for each one.
     */
    public static void main(String[] args) {
        try {
            System.out.println("Loading stopwords");
            loadStopWords();

            
            // Try to test the example Input
            if (args.length == 0) {
                System.out.println("\n--- Running Quick Test (No arguments provided) ---");

                String input = "Document will describe marketing strategies carried out by U.S. companies for their agricultural "
                        +
                        "chemicals, report predictions for market share of such chemicals, or report market statistics for "
                        +
                        "agrochemicals, pesticide, herbicide, fungicide, insecticide, fertilizer, predicted sales, market share, "
                        +
                        "stimulate demand, price cut, volume of sales. ";

                String expected = "document describ market strategi carri compani agricultur chemic report predict market share chemic report market statist agrochem pesticid herbicid fungicid insecticid fertil predict sale market share stimul demand price cut volum sale";

                System.out.println("INPUT:\n" + input.trim());

                String output = transformLine(input);

                System.out.println("\nACTUAL OUTPUT:\n" + output);
                System.out.println("\nEXPECTED OUTPUT:\n" + expected);

                if (output.equals(expected)) {
                    System.out.println("\nTEST PASSED");
                } else {
                    System.err.println("\nTEST FAILED");
                }

                return; // Exit main after the test
            }
            


            System.out.println("Starting transformation");
            long startTime = System.currentTimeMillis();

            // Walk through the input directory to find all .zip files
            try (Stream<Path> paths = Files.walk(Paths.get(INPUT_DIR))) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".zip"))
                        .forEach(TextTransformer::processZipFile);
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Transformation finished. Time taken: " + (endTime - startTime) + "ms");
            
        } 
            catch (IOException e) {
            e.printStackTrace();
        }
            
    }
        
}
