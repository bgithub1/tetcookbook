package com.pdflib.cookbook.tet.mains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pdflib.cookbook.tet.text.TextExtractor;

/**
 * Main for extracting text using Tet
 * @author bperlman1
 *
 */
public class RunTextExtractor {
	/**
	 * 
	 * @param argv
	 * args:
	 *    inputFolder=myInputFolder regex=patternThatSelectsFiles outputFolder=myOutputFolder
	 * @throws UnsupportedEncodingException
	 */
    public static void main(String argv[]) throws UnsupportedEncodingException {
    	Map<String, String> argMap = new HashMap<>();
    	
    	for(int i =0;i<argv.length;i++){
    		String[] parts = argv[i].split("=");
    		if(parts.length!=2){
    			System.err.println("arg pairs must be of form key=arg");
    			System.err.println("illegal arg: " + argv[i]);
    			throw new IllegalStateException();
    		}
    		argMap.put(parts[0], parts[1]);
    	}
    	
    	// get input folder
    	String inputFolder = argMap.get("inputFolder");
    	if(inputFolder==null)inputFolder = "./";
    	String regex = argMap.get("regex");
    	if(regex==null)regex = ".pdf";
    	String outputFolder = argMap.get("outputFolder");
    	if(outputFolder==null)outputFolder = "./output";
    	checkDir(outputFolder); // make the folder if necessary
    	List<String> filesToExtract = getFilesFromRegex(inputFolder, regex);
    	for(String filePath : filesToExtract){
        	TextExtractor te = new TextExtractor();
        	List<String> lines = te.getTextLines(filePath);
        	String[] parts = filePath.split("/");
        	String outputFileName = parts[parts.length-1].replace(".pdf", ".txt");
        	
        	String outputFilePath = outputFolder + "/" + outputFileName;
			try {
	    		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputFilePath)));
	        	for(String s : lines){
	            	bw.write(s);
	            	bw.newLine();
	        	}
				bw.close();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
    	}
    	
    }

    
	private static List<String> getFilesFromRegex(String rootDirectory,String regexExpression){
        try {
        	File dir;
        	if(rootDirectory==null){
        		dir = new File("./");
        	}else{
        		dir = new File(rootDirectory);
        	}
            
//            FileFilter fileFilter = new RegexFileFilter(regexExpression);
//            File[] files = dir.listFiles(fileFilter);
            File[] files = dir.listFiles();
            List<String> ret = new ArrayList<>();
            for(File f : files){
            	String absPath = f.getAbsolutePath();
            	if(absPath.matches(regexExpression)){
            		ret.add(absPath);
            	}
            }
            return ret;
        } catch (Exception e) {
            System.err.println( e.getMessage());
            return null;
        }


	}
	
	private static void checkDir(String directoryName){
		File theDir = new File(directoryName);

		// if the directory does not exist, create it
		if (!theDir.exists()) {
		    System.out.println("creating directory: " + directoryName);
		    boolean result = false;

		    try{
		        theDir.mkdir();
		        result = true;
		    } 
		    catch(SecurityException se){
		        throw new IllegalStateException(se);
		    }        
		    if(!result) {    
		        throw new IllegalStateException("problem making directory");
		    }
		}		
	}

    
}
