package com.pdflib.cookbook.tet.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.pdflib.TETException;
import com.pdflib.TET;

/**
 * PDF image reader based on PDFlib TET. The example demonstrates the extraction
 * of images in memory for feeding them into the Java Image I/O API in order to
 * get the image metadata.
 * <p>
 * The javax.imageio package that comes with the standard JRE is limited in what
 * image formats it support. There is no support for TIFF, JPEG2000 and JBIG2
 * images. For these image formats it is possible to install plugins that
 * extend the functionality of javax.imageio.
 * <p>
 * Note that some PDF producers embed JPEG images in PDF that are not strictly
 * conformant, which may cause exceptions like the following to be thrown:
 * <p>
 * javax.imageio.IIOException: Inconsistent metadata read from stream
 *      at com.sun.imageio.plugins.jpeg.JPEGMetadata.&lt;init&gt;(JPEGMetadata.java:362)
 *      at com.sun.imageio.plugins.jpeg.JPEGImageReader.getImageMetadata(JPEGImageReader.java:1023)
 *      at com.pdflib.cookbook.tet.image.images_in_memory.print_metadata(images_in_memory.java:264)
 *      at com.pdflib.cookbook.tet.image.images_in_memory.main(images_in_memory.java:191)
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: images_in_memory.java,v 1.7 2015/12/15 09:10:48 stm Exp $
 */
public class images_in_memory
{
    /**
     * Global option list
     */
    static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
        + "../resource/glyphlist ../input}";
    
    /**
     * Document-specific option list
     */
    static final String DOC_OPTLIST = "";
    
    /**
     * Page-specific option list
     */
    static final String PAGE_OPTLIST = "granularity=page";
    
    /**
     * Basic image extract options (more below)
     */
    static final String BASE_IMAGE_OPTLIST = "compression=auto";

    /**
     * The encoding in which the output is sent to System.out. For running the
     * example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING = System
            .getProperty("file.encoding");

    /**
     * A sequence of blanks or tabs used for indenting the metadata tree.
     */
    private static final String METADATA_INDENTATION = "  ";
    
    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;
    
    public static void main(String argv[]) throws UnsupportedEncodingException {
        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        TET tet = null;

        try {
            if (argv.length != 1) {
                throw new Exception("usage: images_in_memory <filename>");
            }

            tet = new TET();

            tet.set_option(GLOBAL_OPTLIST);

            int doc = tet.open_document(argv[0], DOC_OPTLIST);
            if (doc == -1) {
                throw new Exception("Error " + tet.get_errnum() + "in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }

            /* get number of pages in the document */
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /* loop over pages */
            for (int pageno = 1; pageno <= n_pages; ++pageno) {
                int page = tet.open_page(doc, pageno, PAGE_OPTLIST);

                if (page < 0) {
                    print_tet_error(tet, pageno);
                    continue; /* try next page */
                }

                /* Retrieve all images on the page */
                int imageno = -1;
                while (tet.get_image_info(page) == 1) {
                    imageno++;
                    
                    /*
                     * Invoke the write_image_file routine with option
                     * "typeonly", to find out the type of the image without
                     * actually writing an imagefile.
                     */
                    int imageType = tet.write_image_file(doc, tet.imageid,
                            BASE_IMAGE_OPTLIST + " typeonly");
                    
                    /*
                     * Map the type to a format (see the TET Manual for the
                     * documentation of the return values of write_image_file)
                     */
                    String imageFormat;
                    switch (imageType) {
                    case 10:
                        imageFormat = "tiff";
                        break;

                    case 20:
                        imageFormat = "jpg";
                        break;
                        
                    case 30:
                        /* Note: type 30 for JPEG 2000 will no longer be returned by TET 5 */
                        imageFormat = "jp2";
                        break;

                    case 31:
                        imageFormat = "jp2";
                        break;
                        
                    case 32:
                        imageFormat = "jpf";
                        break;
                        
                    case 33:
                        imageFormat = "j2k";
                        break;

                    case 40:
                        /* Note: type 40/raw will no longer be returned by TET 5 */
                        imageFormat = "raw";
                        break;

                    case 50:
                        imageFormat = "jbig2";
                        break;
                        
                    default:
                        System.err.println("Page " + pageno + " image "
                                + imageno
                                + ": write_image_file returned unknown value "
                                + imageType + ", skipping image, error: "
                                + tet.get_errmsg());
                        continue;
                    }
                    
                    /*
                     * Fetch the image data in memory.
                     */
                    byte imagedata[] = tet.get_image_data(doc, tet.imageid,
                            BASE_IMAGE_OPTLIST);

                    if (imagedata == null) {
                        print_tet_error(tet, pageno);
                        continue; /* process next image */
                    }
                    
                    /*
                     * Do something meaningful with the data. Here we try to
                     * extract image metadata.
                     */
                    print_metadata(imagedata, imageFormat, pageno, imageno + 1);
                }

                if (tet.get_errnum() != 0) {
                    print_tet_error(tet, pageno);
                }

                tet.close_page(page);
            }

            tet.close_document(doc);
        }
        catch (TETException e) {
            System.err.println("TET exception occurred in extractor sample:");
            System.err.println("[" + e.get_errnum() + "] " + e.get_apiname()
                    + ": " + e.get_errmsg());
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

    /**
     * Try to consume the the binary image data with the Java ImageReader class,
     * and print out any available image metadata.
     * 
     * @param imagedata
     *            The binary data of the image.
     * @param imageFormat
     *            The name of the image format.
     * @param pageno
     *            The page number on which the image was found.
     * @param imageno
     *            The number of the image on the page.
     * 
     * @throws IOException
     *             An error occured in the ImageIO API.
     */
    private static void print_metadata(byte[] imagedata,
            String imageFormat, int pageno, int imageno) throws IOException  {
        /*
         * Try to consume the the binary imagedata with the
         * Java ImageReader class. First try to find a suitable
         * ImageReader class.
         */
        Iterator<ImageReader> readerIterator = ImageIO.getImageReadersByFormatName(imageFormat);
        if (readerIterator != null && readerIterator.hasNext()) {
            /*
             * We try only the first ImageReader from potentially multiple
             * ImageReaders.
             */
            ImageReader reader = (ImageReader) readerIterator.next();
            
            /*
             * Create an ImageInputStream from the binary data and feed it to
             * the ImageReader instance.
             */
            ImageInputStream inputStream =
                ImageIO.createImageInputStream(new ByteArrayInputStream(imagedata));
            reader.setInput(inputStream);
            
            /*
             * Try to retrieve the metadata and print it if available.
             */
            IIOMetadata metadata;
            try {
                metadata = reader.getImageMetadata(0);
                
                if (metadata != null) {
                    String format = metadata.getNativeMetadataFormatName();
                    Node tree = metadata.getAsTree(format);
                    print_metadata(tree, pageno, imageno);
                }
            }
            catch (IOException e) {
                System.err.println("getImageMetadata() raised exception (page " + pageno + ", image " + imageno + "):");
                e.printStackTrace();
            }
        }
        else {
            System.err.println("No Java ImageReader available for suffix "
                    + imageFormat);
        }
    }

    /**
     * Print out the metadata in the DOM tree.
     * 
     * @param tree
     *            The DOM tree to print.
     * @param pageno
     *            The page number of the image to which the metadata belongs.
     * @param imageno
     *            The number of the image on the page.
     */
    private static void print_metadata(Node tree, int pageno, int imageno) {
        out.println("Metadata for image "
                + imageno + " on page " + pageno + ":");
        print_metadata(tree, 0);
    }
    
    /**
     * Recursively walk the DOM subtree given by node and print out node name
     * and attributes.
     * 
     * @param node
     *            The subtree to print.
     * @param level
     *            The current level in the total tree, used for indentation.
     */
    private static void print_metadata(Node node, int level) {
        String indentation = get_indentation(level);
        out.print(indentation + "node=\"" + node.getNodeName() + "\"");
        String value = node.getNodeValue();
        if (value != null) {
            out.print(" value=\"" + value  + "\"");
        }
        out.println();
        NamedNodeMap map = node.getAttributes();
        if (map != null) {
            int length = map.getLength();
            if (length > 0) {
                out.print(indentation + " "); 
                for (int i = 0; i < length; i++) {
                    Node attr = map.item(i);
                    out.print(" " + attr.getNodeName() + "=\""
                            + attr.getNodeValue() + "\"");
                }
                out.println();
            }
        }
        
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i += 1) {
            print_metadata(children.item(i), level + 1);
        }
    }

    /**
     * Produce an indentation string according to parameter level.
     * 
     * @param level
     *            Indentation level
     *            
     * @return A string composed of the METADATA_INDENTATION times level
     */
    private static String get_indentation(int level) {
        StringBuffer indentation = new StringBuffer();
        for (int i = 0; i < level; i += 1) {
            indentation.append(METADATA_INDENTATION);
        }
        return indentation.toString();
    }

    /**
     * Report a TET error.
     * 
     * @param tet
     *            The TET object
     * @param pageno
     *            The page number on which the error occurred
     */
    private static void print_tet_error(TET tet, int pageno) {
        System.err.println("Error " + tet.get_errnum() + " in  "
                + tet.get_apiname() + "() on page " + pageno + ": "
                + tet.get_errmsg());
    }
}
