package com.pdflib.cookbook.tet.text;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Create a sorted list of unique words in a document along with counts.
 * 
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: concordance.java,v 1.11 2015/12/03 11:26:46 stm Exp $
 */
class concordance {
    /**
     * Global option list. The program expects the "resource" directory parallel
     * to the "java" directory.
     */
    private static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
            + "../resource/glyphlist ../input}";

    /**
     * Document specific option list.
     */
    private static final String DOC_OPTLIST = "";

    /**
     * Page-specific option list.
     */
    private static final String PAGE_OPTLIST = "granularity=word";

    /**
     * The encoding in which the output is sent to System.out. For running
     * the example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING = System.getProperty("file.encoding");
    
    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;
    
    /**
     * Set this to true if all words are to be lowercased.
     */
    private static final boolean LOWERCASE_WORDS = false;
    
    /**
     * The name of the file to process.
     */
    private String filename;

    /**
     * The map to store the per-word counters. The key is the word, the value
     * is the number of occurrences of the word.
     */
    private Map<String, Integer> wordCounters = new HashMap<String, Integer>();

    /**
     * Process a single page of text.
     * 
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle
     * @param pageno
     *            Page to process
     * 
     * @throws TETException
     *             An error occurred in the TET API
     */
    private void process_page(TET tet, final int doc, int pageno)
            throws TETException {
        final int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

        if (page == -1) {
            System.err.println("Error " + tet.get_errnum() + " in "
                    + tet.get_apiname() + "(): " + tet.get_errmsg());
        }
        else {
            /*
             * Fetch the text word-wise.
             */
            for (String text = tet.get_text(page); text != null;
                    text = tet.get_text(page)) {
                /*
                 * Only include words that start with a letter.
                 */
                if (Character.isLetter(text.charAt(0))) {
                    if (LOWERCASE_WORDS) {
                        text = text.toLowerCase();
                    }
                    
                    Integer value = (Integer) wordCounters.get(text);
                    if (value != null) {
                        // Increment counter
                        value = new Integer(value.intValue() + 1);
                    }
                    else {
                        // Initialize with first counted word
                        value = new Integer(1);
                    }
                    wordCounters.put(text, value);
                }
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            tet.close_page(page);
        }
    }

    /**
     * Print out the results.
     * 
     * @throws TETException
     */
    private void print_concordance(TET tet, int doc) throws TETException {
        out.println("List of words in the document \""
                + filename + "\" along with the number of occurrences:");
        out.println();
        
        /*
         * Sort the key-value pairs from the Map descending according to
         * their count.
         */
        String[] words = new String[wordCounters.size()];
        words = (String[]) wordCounters.keySet().toArray(words);
        Arrays.sort(words, new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                Integer count1 = (Integer) wordCounters.get(o1);
                Integer count2 = (Integer) wordCounters.get(o2);
                return count2.compareTo(count1);
            }
        });
        
        for (int i = 0; i < words.length; i += 1) {
            out.println(words[i] + " " + wordCounters.get(words[i]));
        }
        out.println();
        out.println("Total unique words: " + words.length);
    }

    /**
     * Generate the concordance for the given file.
     */
    private void execute() {
        TET tet = null;
        int pageno = 0;

        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            final int doc = tet.open_document(filename, DOC_OPTLIST);
            if (doc == -1) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            else {
                /*
                 * Loop over pages in the document
                 */
                final int n_pages = (int) tet.pcos_get_number(doc,
                        "length:pages");
                for (pageno = 1; pageno <= n_pages; ++pageno) {
                    process_page(tet, doc, pageno);
                }

                print_concordance(tet, doc);

                tet.close_document(doc);
            }
        }
        catch (TETException e) {
            if (pageno == 0) {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "(): " + e.get_errmsg() + "\n");
            }
            else {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "() on page " + pageno + ": "
                        + e.get_errmsg() + "\n");
            }
	    System.exit(1);
        }
        finally {
            tet.delete();
        }
    }

    /**
     * @param filename
     *            the name of the file for which the concordance will be
     *            generated
     */
    private concordance(String filename) {
        this.filename = filename;
    }
    
    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 1) {
            out.println("usage: concordance <infilename>");
            return;
        }

        concordance c = new concordance(args[0]);
        c.execute();
    }
}
