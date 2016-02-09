package com.pdflib.cookbook.tet.tetml;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Extract text from PDF document as XML. If constant INMEMORY is false,
 * write the XML to the output file. Otherwise fetch the XML in memory, parse it
 * and print some information to System.out.
 * <p>
 * Required software: TET 5
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: tetml.java,v 1.3 2015/12/01 09:51:40 stm Exp $
 */
public class tetml {
    /**
     * Global option list.
     */
    static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
        + "../resource/glyphlist ../input}";

    /**
     * Document specific option list.
     */
    static final String BASE_DOC_OPTLIST = "";

    /**
     * Page-specific option list.
     */
    static final String PAGE_OPTLIST = "granularity=word";

    /**
     * The encoding in which the output is sent to System.out. For running the
     * example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING = System
            .getProperty("file.encoding");

    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;
    
    /**
     * Set to true for in-memory processing.
     */
    private static final boolean INMEMORY = true;
    
    /**
     * Word counter for in-memory processing code.
     */
    int word_count = 0;
    
    /**
     * SAX handler class to count the words in the document.
     */
    private class sax_handler extends DefaultHandler {
        public void startElement(String uri, String local_name,
                String qualified_name, Attributes attributes)
                throws SAXException {
            if (local_name.equals("Word")) {
                word_count += 1;
            }
            else if (local_name.equals("Font")) {
                out.println("Font " + attributes.getValue("", "name")
                        + " (" + attributes.getValue("", "type") + ")");
            }
        }
    }
    
    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 1) {
            System.err.println("usage: tetml <pdffilename>");
            return;
        }

        /*
         * For JRE 1.4 the property must be set what XML parser to use, later
         * JREs seem to have a default set internally. It seems to be the case
         * that in 1.4 org.apache.crimson.parser.XMLReaderImpl is always
         * available.
         */
        String jre_version = System.getProperty("java.version");
        if (jre_version.startsWith("1.4")) {
            System.setProperty("org.xml.sax.driver",
                    "org.apache.crimson.parser.XMLReaderImpl");
        }

        /*
         * We need a tetml object, otherwise it's not possible to set up the
         * handler for the SAX parser with the local sax_handler class.
         */
        tetml t = new tetml();
        t.process_xml(args);
    }

    private void process_xml(String[] args) {
        TET tet = null;
        try {
            tet = new TET();
            tet.set_option(GLOBAL_OPTLIST);

            final String outputfilename = args[0] + ".tetml";
            
            final String docoptlist = (INMEMORY ? "tetml={}"
                    : "tetml={filename={" + outputfilename + "}}")
                    + " " + BASE_DOC_OPTLIST;

            if (INMEMORY) {
                out.println("Processing TETML output for document \""
                        + args[0] + "\" in memory...");
            }
            else {
                out.println("Extracting TETML for document \"" + args[0] 
                          + "\" to file \"" + outputfilename + "\"...");
            }

            final int doc = tet.open_document(args[0], docoptlist);
            if (doc == -1) {
                System.err.println("Error " + tet.get_errnum() + " in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
                tet.delete();
                return;
            }

            final int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /*
             * Loop over pages in the document;
             */
            for (int pageno = 0; pageno <= n_pages; ++pageno) {
                tet.process_page(doc, pageno, PAGE_OPTLIST);
            }

            /*
             * This could be combined with the last page-related call.
             */
            tet.process_page(doc, 0, "tetml={trailer}");

            if (INMEMORY) {
                /*
                 * Get the XML document as a byte array.
                 */
                final byte[] tetml = tet.get_tetml(doc, "");

                if (tetml == null) {
                    System.err.println("tetml: couldn't retrieve XML data");
                    return;
                }

                /*
                 * Process the in-memory XML document to print out some
                 * information that is extracted with the sax_handler class.
                 */

                XMLReader reader = XMLReaderFactory.createXMLReader();
                reader.setContentHandler(new sax_handler());
                reader.parse(new InputSource(new ByteArrayInputStream(tetml)));
                out.println("Found " + word_count + " words in document");
            }

            tet.close_document(doc);
        }
        catch (TETException e) {
            System.err.println("Error " + e.get_errnum() + " in "
                    + e.get_apiname() + "(): " + e.get_errmsg());
	    System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
	    System.exit(1);
        }
        finally {
            if (tet != null) {
                tet.delete();
            }
        }
    }
}
