package com.pdflib.cookbook.tet.font;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Identify the locations in a PDF where a particular font is used; print the
 * page number, location, and start of text for each hit.
 * <p>
 * usage: font_finder [ -ignorefonts &lt;font list&gt; |
 * -includefonts &lt;font list&gt; ] &lt;PDF document&gt;
 * <p>
 * A &lt;font list&gt; is a comma-separated list of font names. If neither
 * -ignorefonts nor -includefonts is specified, all fonts are included. If
 * -ignorefonts is specified, all fonts but the ignored ones are included. If
 * -includefonts is specified, only the fonts in the specified font list are
 * included.
 * <p>
 * The application prints the coordinates in the same manner as Adobe Acrobat,
 * with the origin of the coordinate system in the upper left corner. This is
 * different from the PDF default coordinate system, which has the origin in the
 * lower left corner. If you want to use the PDF default coordinates, set the
 * variable USE_ACROBAT_COORDINATES to false.
 * <p>
 * You can display cursor coordinates in Acrobat as follows:
 * <p>
 * display cursor coordinates:
 * <p>
 * Acrobat 7/8: View, Navigation Panels, Info<br>
 * Acrobat 9: View, Cursor Coordinates<br>
 * <p>
 * select points as unit:
 * <p>
 * Acrobat 7/8/9: Edit, Preferences, [General], Units&Guides, Page&Ruler,
 * Points<br>
 * In Acrobat 7/8 you can also use Options, Points in the Info panel
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: font_finder.java,v 1.15 2015/12/08 11:37:19 stm Exp $
 */
public class font_finder {
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
    private static final String PAGE_OPTLIST = "granularity=page";

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
     * Command line flag for fonts to ignore.
     */
    private static final String IGNORE_OPT = "-ignorefonts";

    /**
     * Command line flag for fonts to include.
     */
    private final static String INCLUDE_OPT = "-includefonts";

    /**
     * Maximum length of text to print out for a text chunk, if file names
     * are prepended.
     */
    private final static int MAX_TEXT_LENGTH_MULTI_FILE = 25;

    /**
     * Maximum length of text to print out for a text chunk, if file names
     * are noz prepended.
     */
    private final static int MAX_TEXT_LENGTH_SINGLE_FILE = 40;
    
    /**
     * Use the Acrobat coordinate system with the origin in the upper right
     * corner, or the PDF default coordinate system in the lower left
     * corner.
     */
    private static final boolean USE_ACROBAT_COORDINATES = true;
    
    /**
     * Fonts to include in the output. If it is null, all fonts are included.
     */
    private Set<String> includedFonts;

    /**
     * Fonts to exclude from the output. If it is null, no fonts are ignored.
     */
    private Set<String> ignoredFonts;

    /**
     * Name of the input file.
     */
    private String filename;

    /**
     * The format for printing the x and y coordinate values.
     */
    private NumberFormat coordFormat;

    /**
     * Print the filename in each line. Intended for invocations with more
     * than one input file.
     */
    private boolean prependFilenames;
    
    /**
     * Unicode code point for ARABIC TATWEEL character.
     */
    private static final int U_ARABIC_TATWEEL = 0x640;

    /**
     * @param filename
     *            The name of the input document.
     * @param fontsToInclude
     *            Set of fonts to include in the output (may be null).
     * @param fontsToIgnore
     *            Set of fonts to exclude from the output (may be null).
     * @param prependFilenames
     *            Prepend the filename in each line.
     */
    private font_finder(String filename, Set<String> fontsToInclude, Set<String> fontsToIgnore,
            boolean prependFilenames) {
        this.filename = filename;
        this.includedFonts = fontsToInclude;
        this.ignoredFonts = fontsToIgnore;
        this.prependFilenames = prependFilenames;
        this.coordFormat = NumberFormat.getInstance();
        coordFormat.setMinimumFractionDigits(0);
        coordFormat.setMaximumFractionDigits(2);
    }

    /**
     * Run the actual font finder algorithm.
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
     * Extract text from page and identify all the contiguous chunks that
     * use the same font.
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
             * Retrieve the text from the whole page and split it in contiguous
             * chunks of text that use the same font.
             */
            for (String text = tet.get_text(page); text != null;
                                            text = tet.get_text(page)) {
                process_char_info(tet, doc, pageno, page, text);
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            tet.close_page(page);
        }
    }

    /**
     * Process the character information for the given page, and print out the
     * results.
     * 
     * @param tet
     *           TET object
     * @param doc
     *            TET document handle.
     * @param pageno
     *            Page number
     * @param page
     *            TET page handle
     * @param text
     *            The text of the page
     * 
     * @throws TETException
     */
    private void process_char_info(TET tet, int doc, int pageno,
            int page, String text) throws TETException {
        int currentFontId = -1;
        double xPos = 0;
        double yPos = 0;
        
        /*
         * Get the page height for transforming the coordinates to Acrobat's
         * coordinate system.
         */
        final double pageHeight = tet.pcos_get_number(doc,
                "pages[" + (pageno - 1) + "]/height");
        
        StringBuffer chunk = new StringBuffer();
        
        int ci = tet.get_char_info(page);
        while (ci != -1) {
            /*
             * Under certain conditions get_char_info() returns information
             * about a character that can be ignored:
             * 
             * 1) Unicode character ARABIC TATWEEL
             * 2) Control characters
             * 3) Unmappable glyphs
             * 4) Hyphens removed by dehyphenation (bit 5 set in attributes)
             * 
             *  In these cases the character must not be counted.
             */
            if (tet.uv != U_ARABIC_TATWEEL
                                    && !Character.isISOControl(tet.uv)
                                    && !tet.unknown
                                    && (tet.attributes & 0x20) == 0) {
                
                if (tet.fontid != currentFontId) {
                    if (currentFontId != -1) {
                        /* Print information about the finished chunk */
                        print_chunk_info(tet, doc, pageno, chunk.toString(),
                            currentFontId, xPos, yPos, pageHeight);
                    }
    
                    currentFontId = tet.fontid;
                    xPos = tet.x;
                    yPos = tet.y;
                    chunk = new StringBuffer();
                }
                
                /* Insert Unicode code point into the current chunk. */
                chunk.append(Character.toChars(tet.uv));
            }
            
            ci = tet.get_char_info(page);
        }
        
        /* Print information for final chunk */
        if (currentFontId != -1) {
            print_chunk_info(tet, doc, pageno, chunk.toString(),
                currentFontId, xPos, yPos, pageHeight);
        }
    }

    /**
     * Print information about a chunk of text that has the same font.
     * 
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle.
     * @param pageno
     *            Page number
     * @param chunk
     *            The current text chunk that has the same font assigned
     * @param currentFontId
     *            pCOS id of the current font
     * @param xPos
     *            x position of chunk
     * @param yPos
     *            y position of chunk
     * @param pageHeight
     *            height of page
     *
     * @throws TETException
     */
    private void print_chunk_info(TET tet, int doc, int pageno,
        String chunk, int currentFontId,
        double xPos, double yPos, double pageHeight) throws TETException {
        
        // Output information for current chunk
        String fontName = tet.pcos_get_string(doc, "fonts["
                + currentFontId + "]/name");

        if (includeFontInOutput(fontName)) {
            if (USE_ACROBAT_COORDINATES) {
                yPos = pageHeight - yPos;
            }
            
            /*
             * Only print filename if there is more than one
             * file name given on the command line.
             */
            if (prependFilenames) {
                out.print(filename + ", ");
            }
            out.print("page " + pageno);
            out.print(" at (" + coordFormat.format(xPos)
                    + " " + coordFormat.format(yPos) + "), ");
            out.print("font " + fontName + ": ");

            int displayLength = Math.min(
                    prependFilenames
                        ? MAX_TEXT_LENGTH_MULTI_FILE
                        : MAX_TEXT_LENGTH_SINGLE_FILE,
                    chunk.length());
            
            /* 
             * Avoid splitting a surrogate pair: If the Unicode code point
             * is beyond the Basic Multilingual Plane (BMP), add another
             * Unicode code unit.
             */
            if (chunk.codePointAt(displayLength - 1) > 0xFFFF) {
                displayLength += 1;
            }

            out.print(chunk.substring(0, displayLength));
            if (chunk.length() > displayLength) {
                out.print("...");
            }
            out.println();
        }
    }

    /**
     * Whether to include the font in the output.
     * 
     * @param fontName
     *            The name of the font to check
     * 
     * @return true if the font has to be included in the output, otherwise
     *         false
     */
    private boolean includeFontInOutput(String fontName) {
        return (includedFonts == null && ignoredFonts == null)
            || (includedFonts != null && includedFonts.contains(fontName))
            || (ignoredFonts != null && !ignoredFonts.contains(fontName));
    }

    /**
     * Prints out a font set as a comma-separated list.
     * 
     * @param fonts
     * A set of fonts to print as a list.
     */
    private static void print_font_list(Set<String> fonts) {
        Iterator<String> i = fonts.iterator();
        int pos = 0;
        while (i.hasNext()) {
            if (pos > 0) {
                out.print(", ");
            }
            String fontName = (String) i.next();
            out.print(fontName);
        }
    }
    
    /**
     * Splits the list of font names and generates a Set of font names from
     * them.
     * 
     * @param fontList
     *            A comma-separated list of font names.
     * 
     * @return A Set containing the elements of the font list
     */
    private static Set<String> parse_font_list(String fontList) {
        Set<String> retval = new TreeSet<String>();

        StringTokenizer tokenizer = new StringTokenizer(fontList, ",");

        while (tokenizer.hasMoreTokens()) {
            retval.add(tokenizer.nextToken());
        }

        return retval;
    }

    /**
     * Main program
     * 
     * @param args
     *            command line arguments
     * 
     * @throws UnsupportedEncodingException
     *             Unsupported encoding specified for System.out
     */
    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);
        
        Set<String> fontsToInclude = null;
        Set<String> fontsToIgnore = null;
        int i;

        for (i = 0; i < args.length; i += 1) {
            if (args[i].equals(IGNORE_OPT)) {
                i += 1;
                if (i < args.length && fontsToIgnore == null
                        && fontsToInclude == null) {
                    fontsToIgnore = parse_font_list(args[i]);
                }
                else {
                    usage();
                }
            }
            else if (args[i].equals(INCLUDE_OPT)) {
                i += 1;
                if (i < args.length && fontsToIgnore == null
                        && fontsToInclude == null) {
                    fontsToInclude = parse_font_list(args[i]);
                }
                else {
                    usage();
                }
            }
            else {
                break;
            }
        }

        // at least one item must be left as the input file
        if (i < args.length) {
            /*
             * Header describing the included and excluded fonts.
             */
            out.print("included fonts: ");
            if (fontsToInclude == null) {
                out.print("all except ignored fonts");
            }
            else {
                print_font_list(fontsToInclude);
            }
            out.println();
            out.print("ignored fonts: ");
            if (fontsToIgnore == null) {
                out.print("none");
            }
            else {
                print_font_list(fontsToIgnore);
            }
            out.println();

            /*
             * Only prepend input filenames to each line if there is more than
             * one input file.
             */
            boolean printFilenames = args.length - i > 1;
            
            for (; i < args.length; i += 1) {
                font_finder f = new font_finder(args[i], fontsToInclude,
                        fontsToIgnore, printFilenames);
                f.execute();
            }
        }
        else {
            usage();
        }
    }

    private static void usage() {
        System.err.println("usage: font_finder [ -ignorefonts <font list> | "
                + " -includefonts <font list> ] <PDF document> ...");
        System.exit(1);
    }
}
