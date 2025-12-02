# SearchEngine

A simple search-engine for text files.  
Written in Java: transforms text, builds an inverted index, and supports searching + ranking to return the top 10 most relevant results.

---

## Features

- Parses plain text documents and builds an inverted index.  
- Supports stemming (via Porter stemmer) and stop-word removal.  
- Computes relevance scores to rank documents by query relevance.  
- Returns top-10 best matches for a given query.  
- Modular design: easy to extend/modify indexing or ranking logic.  

---

## Repository Structure

/input-files/ // raw text documents you want to index
/input-transform/ // intermediate transformed text
/inv-index/ // inverted-index output (postings, shards, etc.)
*.java // source code: indexer, transformer, search interface, utilities
stopwords.txt // stop-words list


> **Note:** It is recommended *not* to commit the contents of `inv-index/` (index shards or output data) — they tend to be large and are generated data.  

---

## Getting Started

### Prerequisites

- Java 8+  
- (Optionally) an IDE or build tool if you prefer  

### How to build & run

1. Clone the repository  
    ```bash
    git clone https://github.com/WenyiY/SearchEngine.git
    cd SearchEngine
    ```  
2. (Optional) Prepare your documents under `input-files/`.  
3. Run the indexer to generate the inverted index:  
    ```bash
    # e.g. from root directory or via your IDE
    java -cp . InvertedIndexer  # or whatever your main class is
    ```  
4. Use the search interface to query; the program will output the top-10 results (document + score).  

---

## 🧪 Example Usage

```text
> Enter your query:  information retrieval design  
Top 10 results:
1. document1.txt  (score: 0.87)  
2. document42.txt (score: 0.76)  
…  
