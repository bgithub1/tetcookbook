package com.pdflib.cookbook.tet.text;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Create a sorted list of all words in the document along with the page numbers
 * where the words occur.
 * <p>
 * Note that the index is limited to words starting with characters [A-Za-z],
 * as the demonstration program lacks the features that would be necessary 
 * for making it a truly internationalized index program.
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: back_of_the_book_index.java,v 1.10 2015/12/03 11:32:29 stm Exp $
 */
class back_of_the_book_index {
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
    private static final String OUTPUT_ENCODING =
                    System.getProperty("file.encoding");
    
    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;

    /**
     * A word must start with one of the characters in this string to be
     * included in the index (case doesn't matter).
     */
    private static final String INCLUDE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    
    /**
     * Set this to true if all words are to be lowercased.
     */
    private static final boolean LOWERCASE_WORDS = false;
    
    /**
     * The name of the file to process.
     */
    private String filename;
    
    /**
     * A map of sets. The map key is the word, the value is a set of page
     * numbers. For the page set a LinkedHashSet is used, as we traverse the
     * document in page order, and the LinkedHashSet preserves the insertion
     * order, which will give us the desired sorted list of page numbers.
     */
    private Map<String, Set<Integer>> wordPages = new HashMap<String, Set<Integer>>();

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
             * Fetch the text word-wise
             */
            for (String text = tet.get_text(page); text != null;
                    text = tet.get_text(page)) {
                /*
                 * Only include words that start with a letter out of the
                 * set of interesting characters.
                 */
                if (INCLUDE_CHARS.indexOf(Character.toLowerCase(text.charAt(0))) != -1) {
                    if (LOWERCASE_WORDS) {
                        text = text.toLowerCase();
                    }
                    
                    Set<Integer> pages = wordPages.get(text);
                    if (pages == null) {
                        pages = new LinkedHashSet<Integer>();
                        wordPages.put(text, pages);
                    }
                    pages.add(new Integer(pageno));
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
    private void print_index(TET tet, int doc) throws TETException {
        out.println("Alphabetical list of words in the document \""
                + filename + "\" along with their page number:");
        out.println();

        String[] words = new String[wordPages.size()];
        words = wordPages.keySet().toArray(words);
        
        /*
         * Sort according to the sorting rules of the default locale.
         */
        final Collator collator = Collator.getInstance();
        Arrays.sort(words, new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                return collator.compare(o1, o2);
            }
        });
        
        char currentGroup = 0;
        
        /**
         * Print out the words with the pages they appear on, grouped by
         * first letter.
         */
        for (int i = 0; i < words.length; i += 1) {
            String word = words[i];
            char firstChar = Character.toUpperCase(word.charAt(0));
            
            if (firstChar != currentGroup) {
                out.println(firstChar);
                currentGroup = firstChar;
            }
            
            out.print(word + " ");
            
            Set<Integer> pages = wordPages.get(word);
            Iterator<Integer> j = pages.iterator();
            boolean first = true;
            while (j.hasNext()) {
                if (!first) {
                    out.print(", ");
                }
                else {
                    first = false;
                }
                out.print(j.next());
            }
            out.println();
        }
    }

    /**
     * Generate the index for the given file.
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

                print_index(tet, doc);

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
    private back_of_the_book_index(String filename) {
        this.filename = filename;
    }
    
    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 1) {
            out.println("usage: back_of_the_book_index <infilename>");
            return;
        }

        back_of_the_book_index c = new back_of_the_book_index(args[0]);
        c.execute();
    }
}
