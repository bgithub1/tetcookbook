package com.pdflib.cookbook.tet.tet_and_pdflib;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.pdflib.PDFlibException;
import com.pdflib.TET;
import com.pdflib.TETException;
import com.pdflib.pdflib;

/**
 * Extract some text from a PDF based on certain typographic criteria (font,
 * fontsize) along with the corresponding page numbers, and use PDFlib to create
 * a table of contents (TOC) for the original document, possibly enriched with
 * active links to the respective pages. With PDFlib+PDI the TOC could be
 * prepended to the original pages. With plain PDFlib a stand-alone TOC can be
 * created.
 * <p>
 * Required software: TET 3 and PDFlib+PDI 8 or PDFlib 8
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: create_table_of_contents.java,v 1.5 2015/12/03 13:52:51 stm Exp $
 */
class create_table_of_contents {
    /**
     * Common search path for PDI and TET to find the input document.
     */
    private static final String DOC_SEARCH_PATH = "../input";

    /**
     * Global option list. The program expects the "resource" directory parallel
     * to the "java" directory.
     */
    private static final String GLOBAL_OPTLIST =
        "searchpath={../resource/cmap ../resource/glyphlist "
            + DOC_SEARCH_PATH + "}";

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
     * The name of the input file
     */
    private String infilename;

    /**
     * The name of the output file
     */
    private String outfilename;

    /**
     * The name of the font to search for.
     */
    private static final String FONT_NAME = "TheSansBold-Plain";

    /**
     * The font size to search for in points.
     */
    private static final double FONT_SIZE = 9;

    /**
     * The tolerance for the font size in points.
     */
    private static final double FONT_SIZE_TOLERANCE = 0.01;
    
    /**
     * Nudge factor for ascender height of the Web links (relative to the font
     * size)
     */
    private static final double ASCENDER = 0.85;

    /**
     * Whether to use PDI to create a new document that consists of the original
     * document and the TOC prepended to it. Set this to false in order not to
     * use PDI, and in order to produce a document that only contains the TOC.
     */
    private static final boolean USE_PDI = true;

    /**
     * The page width for the TOC pages (see "width" option for
     * begin_page_ext() in the PDFlib Reference Manual)
     */
    private static final String TOC_WIDTH = "a4.width";
    
    /**
     * The page height for the TOC pages (see "height" option for
     * begin_page_ext() PDFlib Reference Manual)
     */
    private static final String TOC_HEIGHT = "a4.height";
    
    /**
     * The title for the TOC.
     */
    private static final String TOC_TITLE = "Table of Contents";
    
    /**
     * The font to use for the headline that is placed on each page of the TOC.
     */
    private static final String TOC_TITLE_FONT = "Helvetica-Bold";

    /**
     * The fontsize to use for the headline that is placed on each page of the
     * TOC.
     */
    private static final int TOC_TITLE_FONTSIZE = 18;

    /**
     * The font to use for the TOC entries.
     */
    private static final String TOC_FONT = "Helvetica";

    /**
     * The fontsize for the TOC entries.
     */
    private static final int TOC_FONTSIZE = 12;

    /**
     * x-position of the lower-left corner of the TOC fitbox.
     */
    private static final int TOC_LLX = 110;

    /**
     * y-position of the lower-left corner of the TOC fitbox.
     */
    private static final int TOC_LLY = 100;

    /**
     * x-position of the upper-right corner of the TOC fitbox.
     */
    private static final int TOC_URX = 450;

    /**
     * y-position of the upper-right corner of the TOC fitbox.
     */
    private static final int TOC_URY = 700;

    /**
     * Lower-left y-position for the TOC headline.
     */
    private static final int TOC_TITLE_LLY = 740;

    /**
     * The prefix for a destination name.
     */
    private static final String TOC_DESTINATION_PREFIX = "tmx";
    
    /**
     * The text flow (including options) for the TOC contents.
     */
    private StringBuffer tocTextflow = new StringBuffer();
    
    /**
     * The current destination number. Used to generate unique destination
     * names.
     */
    private int destNumber = 0;
    
    /**
     * Import the current page from the PDI import document and place it in the
     * ouput document.
     *
     * @param p
     *            the pdflib object
     * @param pdiHandle
     *            the PDI handle for the input document
     * @param pageno
     *            the current page number
     *
     * @throws PDFlibException
     *             an error occurred in the PDFlib API
     */
    private int put_pdi_page(pdflib p, int pdiHandle, int pageno)
            throws PDFlibException {
        /*
         * The page size will be adjusted later to match the size of the
         * input pages
         */
        p.begin_page_ext(10, 10, "group content");

        int pageHandle = p.open_pdi_page(pdiHandle, pageno, "");
        
        if (pageHandle != -1) {
            /* Place the input page and adjust the page size */
            p.fit_pdi_page(pageHandle, 0, 0, "adjustpage");
        }
        
        return pageHandle;
    }

    /**
     * Tests whether the current character matches the criteria for text that
     * shall get a an entry in the TOC. get_char_info must have been called
     * before in order to ensure that the TET object contains the information
     * for the current character.
     * 
     * @param tet
     *            The TET object
     * @param doc
     *            The TET document handle
     * @throws TETException
     */
    private boolean font_matches(TET tet, final int doc) throws TETException {
        String name = tet.pcos_get_string(doc,
                "fonts[" + tet.fontid + "]/name");
        return name.equals(FONT_NAME) &&
            (Math.abs(tet.fontsize - FONT_SIZE) <= FONT_SIZE_TOLERANCE);
    }
    
    /**
     * Add text and options to the textflow for the current TOC entry. The
     * options take care that the TOC will be properly formattted (do not split
     * TOC entries over page boundaries).
     * 
     * @param p
     *            The pdflib object
     * @param tocText
     *            The text of the TOC entry to add
     * @param pageno
     *            The page number for the TOC entry
     * @param ulx
     *            x-position of the identified text on the page
     * @param uly
     *            y-position of the identified text on the page
     *            
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private void add_toc_entry(pdflib p, String tocText, int pageno,
            double ulx, double uly) throws PDFlibException {
        /*
         * The same name is used for the matchbox name in the TOC and for the
         * named destination that is the target of the "GoTo" action in the TOC.
         */
        String destName = get_destination_name(destNumber);

        /*
         * We need pairwise marks that enclose the text that shall be kept
         * together.
         */
        tocTextflow.append("<mark=").append(destNumber * 2).append(
                " alignment=left matchbox={name=").append(destName).append(
                " boxheight={fontsize descender}}>").append(tocText).append(
                "<leader={alignment={grid}}>\t").append(pageno).append(
                "<matchbox=end nextline mark=").append((destNumber * 2) + 1)
                .append(">\n");

        if (USE_PDI) {
            p.add_nameddest(destName, "type=fixed left=" + ulx + " top=" + uly);
        }

        destNumber += 1;
    }

    /**
     * Create a unique name for each destination.
     * 
     * @param number
     *            The number of the destination.
     *            
     * @return A string that can be used as a destination name and as the
     *         corresponding matchbox name.
     */
    private String get_destination_name(int number) {
        return TOC_DESTINATION_PREFIX + number;
    }

    /**
     * Process a page: Create a new page in the output document, place the page
     * from the input document in the output document, and create TOC entries
     * for all occurrences of text with the desired properties.
     * 
     * @param tet
     *            TET object
     * @param doc
     *            TET document handle
     * @param p
     *            pdflib object
     * @param pdiHandle
     *            PDI document handle
     * @param pageno
     *            The current page number
     * @throws TETException
     *             An error occurred in the TET API
     * @throws PDFlibException
     *             An error occurred in the PDFlib API
     */
    private void process_page(TET tet, final int doc, pdflib p, int pdiHandle, int pageno)
            throws TETException, PDFlibException {
        if (USE_PDI) {
            put_pdi_page(p, pdiHandle, pageno);
        }
        
        final int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

        if (page == -1) {
            System.err.println("Error " + tet.get_errnum() + " in "
                    + tet.get_apiname() + "(): " + tet.get_errmsg());
        }
        else {
            /* Retrieve all text fragments for the page */
            for (String text = tet.get_text(page); text != null; text = tet
                    .get_text(page)) {
                int nextCharPos = 0;
                int matchStart = -1;

                double uly = 0;
                double ulx = 0;

                while (tet.get_char_info(page) != -1) {
                    nextCharPos += 1;

                    if (font_matches(tet, doc)) {
                        if (matchStart == -1) {
                            // start of new matching chunk
                            matchStart = nextCharPos - 1;
                            uly = tet.y + ASCENDER * tet.fontsize;
                            ulx = tet.x;
                        }
                    }
                    else {
                        if (matchStart != -1) {
                            /*
                             * End of matching chunk. The last character that is
                             * belonging to the chunk is the one preceding the
                             * next character. matchEnd is the index of the
                             * first character after the bookmark characters.
                             */
                            int matchEnd = nextCharPos - 1;

                            /*
                             * remove trailing whitespace
                             */
                            while (matchEnd > matchStart
                                    && Character.isWhitespace(
                                        text.charAt(matchEnd - 1))) {
                                matchEnd -= 1;
                            }

                            String tocText =
                                text.substring(matchStart, matchEnd);
                            out.println("Creating TOC entry \""
                                    + tocText + "\"");
                            add_toc_entry(p, tocText, pageno, ulx, uly);
                            matchStart = -1;
                        }
                    }
                }
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            /*
             * Close page in the input and output documents.
             */
            if (USE_PDI) {
                p.end_page_ext("");
            }
            tet.close_page(page);
        }
    }

    private void create_toc(pdflib p) throws Exception {
        final String optlist = "fontname=" + TOC_FONT
            + " fontsize=" + TOC_FONTSIZE
            + " encoding=unicode ruler=100%"
            + " hortabmethod=ruler tabalignment=right";
        
        /*
         * Load the font for the title of the TOC
         */
        int font = p.load_font(TOC_TITLE_FONT, "unicode", "");

        if (font == -1)
            throw new Exception("Error: " + p.get_errmsg());

        /*
         * Create a textflow for the collected TOC entries.
         */
        int tf = p.create_textflow(tocTextflow.toString(), optlist);
        if (tf == -1)
            throw new Exception("Error: " + p.get_errmsg());

        /*
         * Maximum number of the mark defined in the Textflow
         */
        int maxMark = (destNumber * 2) - 1;
        
        /*
         * Keep track of the first mark for each page for creating the
         * "GoTo" actions on the page.
         */
        int startMark = 0;
        
        /*
         * Loop until all of the text is placed; create new pages as long as
         * more text needs to be placed.
         */
        String result;
        do {
            p.begin_page_ext(0, 0, "group=toc width=" + TOC_WIDTH + " height="
                    + TOC_HEIGHT);

            /* Place a text line with a title */
            p.setfont(font, TOC_TITLE_FONTSIZE);
            p.fit_textline(TOC_TITLE, TOC_LLX, TOC_TITLE_LLY, "");

            /*
             * Place the Textflow with the table of contents in blind mode, to
             * find out how many marks did fit into the box. With this
             * information we also prevent a TOC entry from being split over
             * two pages by using the "returnatmark" option.
             */
            result = p.fit_textflow(tf, TOC_LLX, TOC_LLY, TOC_URX, TOC_URY,
                    "blind");

            int lastMark = (int) p.info_textflow(tf, "lastmark");

            /*
             * An even mark number indicates the start of a text section to be
             * kept together. Reset it to the last odd mark number which
             * indicates the end of a text section.
             */
            if (lastMark % 2 == 0) {
                lastMark -= 1;
            }

            /*
             * Now actually fit the textflow. To rewind the textflow status to
             * before the last call to fit_textflow() use "rewind=-1".
             */
            result = p.fit_textflow(tf, TOC_LLX, TOC_LLY, TOC_URX, TOC_URY,
                    "returnatmark=" + lastMark + " rewind=-1");

            /*
             * When PDI is in use, create the "GoTo" actions that allows to
             * click on a TOC entry for jumping to the corresponding section of
             * the document.
             */
            for (int i = startMark; USE_PDI && i <= lastMark / 2; i += 1) {
                String destinationName = get_destination_name(i);
                int action = p.create_action("GoTo", "destname="
                        + destinationName);

                p.create_annotation(0, 0, 0, 0, "Link", "action={activate "
                        + action + "} linewidth=0 usematchbox={"
                        + destinationName + "}");
            }

            startMark = (lastMark / 2) + 1;

            p.end_page_ext("");

            /*
             * "_boxfull" means we must continue because there is more text;
             * "_nextpage" is interpreted as "start new column"
             */
        }
        while (!result.equals("_stop") && !result.equals("_boxempty")
                && !result.equals("_mark" + maxMark));

        /* Check for errors */
        if (result.equals("_boxempty"))
            throw new Exception("Error: Textflow box for TOC is too small");

        p.delete_textflow(tf);
    }

    private void execute() {
        TET tet = null;
        pdflib p = null;
        int pageno = 0;

        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            p = new pdflib();
            p.set_option("searchpath={" + DOC_SEARCH_PATH + "}");

            if (p.begin_document(outfilename, "groups={toc content}") == -1) {
                System.err.println("Error: " + p.get_errmsg());
                return;
            }

            /* add document info entries */
            p.set_info("Creator", "Create Table Of Contents TET Cookbook Example");
            p.set_info("Author", "PDFlib GmbH");
            p.set_info("Title", infilename);

            final int doc = tet.open_document(infilename, DOC_OPTLIST);
            if (doc == -1) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
                return;
            }

            final int n_pages = (int) tet.pcos_get_number(doc, "length:pages");
            int pdiHandle = -1;
            if (USE_PDI) {
                pdiHandle = p.open_pdi_document(infilename, "");
                if (pdiHandle == -1) {
                    System.err.println("Error: " + p.get_errmsg());
                    return;
                }
            }

            /*
             * Loop over pages in the document
             */
            for (pageno = 1; pageno <= n_pages; ++pageno) {
                process_page(tet, doc, p, pdiHandle, pageno);
            }

            /*
             * Use the information collected while processing the input
             * document to create the table of contents.
             */
            create_toc(p);
            
            p.end_document("");
            if (USE_PDI) {
                p.close_pdi_document(pdiHandle);
            }
            tet.close_document(doc);
            
            out.println("Created PDF output document \"" + outfilename
                    + "\" with " + destNumber  + " TOC entries.");
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
        }
        catch (PDFlibException e) {
            if (pageno == 0) {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "(): " + e.get_errmsg() + "\n");
            }
            else {
                System.err.println("Error " + e.get_errnum() + " in "
                        + e.get_apiname() + "() on page " + pageno + ": "
                        + e.get_errmsg() + "\n");
            }
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
        }
        finally {
            tet.delete();
            p.delete();
        }
    }

    /**
     * @param infilename
     *            the name of the file for which the bookmarked file will be
     *            generated
     * @param outfilename
     *            the name of the output file
     */
    private create_table_of_contents(String infilename, String outfilename) {
        this.infilename = infilename;
        this.outfilename = outfilename;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 2) {
            out.println("usage: create_table_of_contents <infilename> <outfilename>");
            return;
        }

        create_table_of_contents t = new create_table_of_contents(args[0], args[1]);
        t.execute();
    }
}
